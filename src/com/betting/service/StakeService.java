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

    // cache to avoid sorting every time, invalidate on write
    private final ConcurrentHashMap<Integer, String> cache = new ConcurrentHashMap<>();

    /**
     * stake, save highest amount for same customer
     *
     * @param betOfferId
     * @param customerId
     * @param stake
     */
    public void post(int betOfferId, int customerId, int stake) {
        data.computeIfAbsent(betOfferId, id -> new ConcurrentHashMap<>())
                .merge(customerId, stake, Math::max);
        // invalidate cache for this betOfferId
        cache.remove(betOfferId);
    }

    /**
     * return top 20 stakes, order by amount in desc, return "" when no data
     *
     * @param betOfferId
     * @return
     */
    public String highStakes(int betOfferId) {
        // try cache first
        String cached = cache.get(betOfferId);
        if (cached != null) return cached;

        ConcurrentHashMap<Integer, Integer> stakes = data.get(betOfferId);
        if (stakes == null || stakes.isEmpty()) return "";

        // use min-heap to find top N, O(n log N) where N=20, much better than full sort O(n log n)
        PriorityQueue<Map.Entry<Integer, Integer>> heap =
                new PriorityQueue<>(TOP_N, Comparator.comparingInt(Map.Entry::getValue));

        for (Map.Entry<Integer, Integer> entry : stakes.entrySet()) {
            if (heap.size() < TOP_N) {
                heap.offer(entry);
            } else if (entry.getValue() > heap.peek().getValue()) {
                heap.poll();
                heap.offer(entry);
            }
        }

        // sort result in desc order for output
        List<Map.Entry<Integer, Integer>> topList = new ArrayList<>(heap);
        topList.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < topList.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append("customer ").append(topList.get(i).getKey()).append(" posted the stake ")
                    .append(topList.get(i).getValue());
        }

        String result = sb.toString();
        cache.put(betOfferId, result);
        return result;
    }

}