package com.betting.service;

import java.security.SecureRandom;

/**
 * Session key generator
 */
public final class SessionKeyGenerator {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final int LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();


    private SessionKeyGenerator() {
    }

    /**
     * generate session key
     *
     * @return
     */
    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++)
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        return sb.toString();
    }

}
