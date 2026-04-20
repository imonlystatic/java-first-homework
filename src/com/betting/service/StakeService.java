package com.betting.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stake service
 */
public class StakeService {
    private static final int TOP_N = 20;

    // betOfferId -> ( customerId -> highest stake )
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Integer>> data =
            new ConcurrentHashMap<>();

    /**
     * stake, save highest amount for same customer
     *
     * @param betOfferId
     * @param customerId
     * @param stake
     */
    public void post(int betOfferId, int customerId, int stake) {
        data.computeIfAbsent(betOfferId, id -> new ConcurrentHashMap<>()).merge(customerId, stake, Math::max);
    }

    /**
     * return top 20 stakes, order by amount in desc, return "" when no data
     *
     * @param betOfferId
     * @return
     */
    public String highStakes(int betOfferId) {
        // get stakes by betOfferId
        ConcurrentHashMap<Integer, Integer> stakes = data.get(betOfferId);
        if (stakes == null || stakes.isEmpty()) return "";

        // sort
        List<Map.Entry<Integer, Integer>> list = new ArrayList<>(stakes.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        // package data
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(TOP_N, list.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("customer ").append(list.get(i).getKey()).append(" posted the stake ")
                    .append(list.get(i).getValue());
        }
        return sb.toString();
    }

}
