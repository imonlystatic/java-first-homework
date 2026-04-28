package com.betting.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stake service — strong consistency + bounded memory.
 * <p>
 * - Per-offer memory capped by probabilistic trim.
 * - Global offer count capped by LRU-like eviction.
 * - Cache invalidation is atomic (version bound to cache state), no stale write-back.
 */
public class StakeService {
    private static final int TOP_N = 20;

    private static final int MAX_CUSTOMERS_PER_OFFER = 10000;

    private static final int MAX_OFFERS = 10000;

    private final ConcurrentHashMap<Integer, BetOfferData> betOfferDataMap = new ConcurrentHashMap<>();

    public void post(int betOfferId, int customerId, int stake) {
        BetOfferData offer = betOfferDataMap.computeIfAbsent(betOfferId, k -> new BetOfferData());
        offer.post(customerId, stake);

        // probabilistic global cleanup to avoid blocking the hot path
        if (betOfferDataMap.size() > MAX_OFFERS && ThreadLocalRandom.current().nextInt(10) == 0) {
            trimOldOffers();
        }
    }

    public String highStakes(int betOfferId) {
        BetOfferData offer = betOfferDataMap.get(betOfferId);
        if (offer == null) return "";
        return offer.getHighStakes();
    }

    /**
     * Remove oldest offers until we are back under the limit.
     */
    private void trimOldOffers() {
        int targetSize = MAX_OFFERS * 9 / 10;
        List<Map.Entry<Integer, BetOfferData>> list = new ArrayList<>(betOfferDataMap.entrySet());
        // sort by last access time ascending → oldest first
        list.sort(Comparator.comparingLong(e -> e.getValue().lastAccessTime));
        int remove = list.size() - targetSize;
        for (int i = 0; i < remove && i < list.size(); i++) {
            Map.Entry<Integer, BetOfferData> e = list.get(i);
            betOfferDataMap.remove(e.getKey(), e.getValue());
        }
    }

    /**
     * Bet offer data
     */
    private static class BetOfferData {
        private final ConcurrentHashMap<Integer, Integer> stakes = new ConcurrentHashMap<>();
        private final AtomicLong version = new AtomicLong(0);
        private volatile CacheState cacheState;
        private volatile long lastAccessTime = System.nanoTime();

        /**
         * post stake to cache
         *
         * @param customerId
         * @param stake
         */
        public void post(int customerId, int stake) {
            stakes.merge(customerId, stake, Math::max);
            long newVersion = version.incrementAndGet();
            // atomically publish invalidation together with the new version
            cacheState = new CacheState(null, newVersion);
            lastAccessTime = System.nanoTime();

            // probabilistic per-offer memory trim
            if (stakes.size() > MAX_CUSTOMERS_PER_OFFER
                    && ThreadLocalRandom.current().nextInt(10) == 0) {
                trimLowestStakes();
            }
        }

        /**
         * get high statkes
         *
         * @return
         */
        public String getHighStakes() {
            lastAccessTime = System.nanoTime();
            CacheState state = cacheState;
            if (state != null && state.result != null && state.version == version.get()) {
                return state.result;
            }
            if (stakes.isEmpty()) return "";

            long v = version.get();
            String result = computeHighStakes();

            // only cache if no new post happened during our computation
            if (version.get() == v) {
                cacheState = new CacheState(result, v);
            }
            return result;
        }

        /**
         * compute high stakes
         *
         * @return
         */
        private String computeHighStakes() {
            PriorityQueue<Map.Entry<Integer, Integer>> heap =
                    new PriorityQueue<>(TOP_N, Comparator.comparingInt(Map.Entry::getValue));

            for (Map.Entry<Integer, Integer> e : stakes.entrySet()) {
                if (heap.size() < TOP_N) {
                    heap.offer(e);
                } else if (e.getValue() > heap.peek().getValue()) {
                    heap.poll();
                    heap.offer(e);
                }
            }

            List<Map.Entry<Integer, Integer>> topList = new ArrayList<>(heap);
            topList.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < topList.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append("customer ").append(topList.get(i).getKey()).append(" posted the stake ")
                        .append(topList.get(i).getValue());
            }
            return sb.toString();
        }

        /**
         * trim lowest statkes
         */
        private void trimLowestStakes() {
            int targetSize = MAX_CUSTOMERS_PER_OFFER * 9 / 10;
            PriorityQueue<Integer> heap = new PriorityQueue<>();
            for (int v : stakes.values()) {
                heap.offer(v);
                if (heap.size() > targetSize) heap.poll();
            }
            int threshold = heap.isEmpty() ? 0 : heap.peek();
            stakes.values().removeIf(v -> v <= threshold);
        }
    }

    /**
     * Immutable cache entry bound to a specific version.
     */
    private static class CacheState {
        final String result;
        final long version;

        CacheState(String result, long version) {
            this.result = result;
            this.version = version;
        }
    }

}
