package com.mike;

import com.mike.extracted.Xoroshiro128PlusPlusRandom;
import com.mike.extracted.RandomProvider;
import com.mike.recreated.Identifier;

public class BedrockReader {
    // Seeds extracted once at construction and stored as plain longs so the
    // hot-path static method can work entirely on the stack.
    public final long deriverSeedLo;
    public final long deriverSeedHi;

    // Kept for any non-hot-path callers that still use the object-based API.
    final Xoroshiro128PlusPlusRandom.RandomDeriver randomDeriver;
    final BedrockType bedrockType;

    public BedrockReader(long seed, BedrockType bedrockType) {
        this.bedrockType = bedrockType;

        Xoroshiro128PlusPlusRandom.RandomDeriver deriver =
            (Xoroshiro128PlusPlusRandom.RandomDeriver)
                RandomProvider.XOROSHIRO
                    .create(seed)
                    .createRandomDeriver()
                    .createRandom(bedrockType.id.toString())
                    .createRandomDeriver();

        this.randomDeriver = deriver;
        this.deriverSeedLo = deriver.seedLo;
        this.deriverSeedHi = deriver.seedHi;
    }

    /**
     * Returns whether the block at (x, y, z) is bedrock given a pre-computed
     * probability for that Y level.  Zero heap allocation — all state lives on
     * the stack as local longs/floats.
     *
     * Sentinels: probability >= 1.0 → always bedrock, <= 0.0 → never bedrock.
     */
    public static boolean inlinedIsBedrock(long deriverSeedLo, long deriverSeedHi,
                                           int x, int y, int z,
                                           double probability) {
        if (probability >= 1.0) return true;
        if (probability <= 0.0) return false;

        // MathHelper.hashCode inlined
        long l = (long)(x * 3129871) ^ (long)z * 116129781L ^ (long)y;
        l = l * l * 42317861L + l * 11L;
        long hash = l >> 16;

        // Per-position seed derivation (no allocation)
        long s0 = hash ^ deriverSeedLo;
        long s1 = deriverSeedHi;

        // Guard against all-zero state (mirrors Xoroshiro128PlusPlusRandomImpl ctor)
        if ((s0 | s1) == 0L) {
            s0 = -7046029254386353131L;
            s1 =  7640891576956012809L;
        }

        // Single xoroshiro128++ step
        long result = Long.rotateLeft(s0 + s1, 17) + s0;

        // nextFloat(): top 24 bits * 2^-24
        float f = (float)(result >>> 40) * 5.9604645E-8f;

        return (double) f < probability;
    }

    /**
     * Pre-computes the lerp probability for a block at the given Y coordinate.
     * Called once per block at startup, never during the search.
     *
     * Sentinels: 2.0 = always bedrock, -1.0 = never bedrock.
     */
    public double computeProbability(int y) {
        // Cache field reads as locals to avoid repeated field-chain indirection.
        int min = bedrockType.min;
        int max = bedrockType.max;

        if (bedrockType == BedrockType.BEDROCK_FLOOR) {
            if (y == min) return 2.0;
            if (y > max)  return -1.0;
            // lerp from 1.0 (at min) to 0.0 (at max)
            return 1.0 - (double)(y - min) / (double)(max - min);
        } else {
            if (y == max) return 2.0;
            if (y < min)  return -1.0;
            // lerp from 1.0 (at max) to 0.0 (at min)
            return 1.0 - (double)(max - y) / (double)(max - min);
        }
    }

    // Legacy object-based path, kept for compatibility.
    boolean isBedrock(int x, int y, int z) {
        double p = computeProbability(y);
        return inlinedIsBedrock(deriverSeedLo, deriverSeedHi, x, y, z, p);
    }

    public enum BedrockType {
        BEDROCK_FLOOR(new Identifier("bedrock_floor"), -64, -64 + 5),
        BEDROCK_ROOF (new Identifier("bedrock_roof"),  128, 128 - 5);

        public final Identifier id;
        public final int min;
        public final int max;

        BedrockType(Identifier id, int min, int max) {
            this.id  = id;
            this.min = min;
            this.max = max;
        }
    }
}
