package com.betting.service;

import com.betting.model.Session;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Session service
 */
public class SessionService {

    // customerId  -> current availble session
    private final ConcurrentHashMap<Integer, Session> byCustomer = new ConcurrentHashMap<>();
    // sessionKey  -> session  （for fast validate）
    private final ConcurrentHashMap<String, Session> byKey = new ConcurrentHashMap<>();

    public SessionService() {
        // daemon thread cleans up expired sessions every 5 minutes, prevent memory from growing too many
        ScheduledExecutorService cleaner =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "session-cleaner");
                    t.setDaemon(true);
                    return t;
                });
        cleaner.scheduleAtFixedRate(this::removeSessionWhenExpired, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * get or create session by customerId, return the same in 10 minutes
     *
     * @param customerId
     * @return
     */
    public Session getOrCreate(int customerId) {
        Session session = byCustomer.get(customerId);
        if (session != null && !session.isExpired()) {
            session.renew();
            return session;
        }
        // fix race condition: sync on customerId to avoid inconsistent state between byCustomer and byKey
        synchronized (byCustomer) {
            // double check inside lock
            session = byCustomer.get(customerId);
            if (session != null && !session.isExpired()) {
                session.renew();
                return session;
            }
            String key = SessionKeyGenerator.generate();
            Session newSession = new Session(key, customerId);
            byCustomer.put(customerId, newSession);
            byKey.put(key, newSession);
            if (session != null) byKey.remove(session.getKey());
            return newSession;
        }
    }

    /**
     * validate sessionKey, return customerId, return -1 when it's invalid
     *
     * @param sessionKey
     * @return
     */
    public int resolveCustomer(String sessionKey) {
        if (sessionKey == null) return -1;
        Session session = byKey.get(sessionKey);
        if (session == null || session.isExpired()) return -1;
        return session.getCustomerId();
    }

    /**
     * remove
     */
    private void removeSessionWhenExpired() {
        byKey.entrySet().removeIf(e -> e.getValue().isExpired());
        byCustomer.entrySet().removeIf(e -> e.getValue().isExpired());
    }
}
