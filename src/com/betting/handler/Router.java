package com.betting.handler;

import com.betting.service.SessionService;
import com.betting.service.StakeService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Path distributor, distribute by api keyword
 * GET  /{customerId}/session
 * POST /{betOfferId}/stake?sessionkey=xxx   body: stake(int)
 * GET  /{betOfferId}/highstakes
 */
public class Router implements HttpHandler {

    private final SessionService sessions;
    private final StakeService stakes;

    public Router(SessionService sessions, StakeService stakes) {
        this.sessions = sessions;
        this.stakes = stakes;
    }

    /**
     * Handle request path
     *
     * @param ex
     * @throws IOException
     */
    @Override
    public void handle(HttpExchange ex) throws IOException {
        // get path, like"/1234/session"
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod().toUpperCase();

        // split path，path.split("/") → ["", "1234", "session"]
        String[] parts = path.split("/");
        if (parts.length < 3) {
            send(ex, 400, "Bad Request");
            return;
        }
        // customerId or betOfferId
        int id;
        try {
            id = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            send(ex, 400, "Invalid id");
            return;
        }

        // get api keyword, like "session" | "stake" | "highstakes"
        String action = parts[2];

        switch (action) {
            case "session":
                this.handleSession(ex, method, id);
                break;
            case "stake":
                this.handleStake(ex, method, id);
                break;
            case "highstakes":
                this.handleHighStakes(ex, method, id);
                break;
            default:
                send(ex, 404, "Not Found");
        }
    }

    /**
     * GET path:/{customerId}/session
     *
     * @param ex
     * @param method
     * @param customerId
     * @throws IOException
     */
    private void handleSession(HttpExchange ex, String method, int customerId) throws IOException {
        if (!"GET".equals(method)) {
            send(ex, 405, "Method Not Allowed");
            return;
        }
        send(ex, 200, sessions.getOrCreate(customerId).getKey());
    }

    /**
     * POST path:/{betOfferId}/stake?sessionkey=xxx
     *
     * @param ex
     * @param method
     * @param betOfferId
     * @throws IOException
     */
    private void handleStake(HttpExchange ex, String method, int betOfferId) throws IOException {
        if (!"POST".equals(method)) {
            send(ex, 405, "Method Not Allowed");
            return;
        }

        String sessionKey = queryParam(ex.getRequestURI().getQuery(), "sessionkey");
        int customerId = sessions.resolveCustomer(sessionKey);
        if (customerId < 0) {
            send(ex, 401, "Unauthorized");
            return;
        }

        int stake;
        try {
            stake = Integer.parseInt(new String(ex.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8).trim());
        } catch (NumberFormatException e) {
            send(ex, 400, "Invalid stake");
            return;
        }

        stakes.post(betOfferId, customerId, stake);
        send(ex, 200, "");
    }

    /**
     * GET path:/{betOfferId}/highstakes
     *
     * @param ex
     * @param method
     * @param betOfferId
     * @throws IOException
     */
    private void handleHighStakes(HttpExchange ex, String method, int betOfferId) throws IOException {
        if (!"GET".equals(method)) {
            send(ex, 405, "Method Not Allowed");
            return;
        }
        send(ex, 200, stakes.highStakes(betOfferId));
    }

    /**
     * tools method
     *
     * @param query
     * @param name
     * @return
     */
    private static String queryParam(String query, String name) {
        if (query == null) return null;
        for (String kv : query.split("&"))
            if (kv.startsWith(name + "=")) return kv.substring(name.length() + 1);
        return null;
    }

    /**
     * Send request
     *
     * @param ex
     * @param status
     * @param body
     * @throws IOException
     */
    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

}
