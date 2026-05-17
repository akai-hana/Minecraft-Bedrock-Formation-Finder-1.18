package com.mike;

import com.mike.extracted.RandomProvider;
import com.mike.extracted.Xoroshiro128PlusPlusRandom;
import com.mike.recreated.Identifier;

public class BedrockReader {
    public final long deriverSeedLo;
    public final long deriverSeedHi;

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
     * Pre-computes the bedrock probability for a block at the given Y level.
     * Returns 2.0 (always bedrock) or -1.0 (never bedrock) at the boundaries,
     * and a value in (0, 1) for layers in between.
     * Call this once per block at startup and cache the result.
     */
    public double computeProbability(int y) {
        int min = bedrockType.min;
        int max = bedrockType.max;

        if (bedrockType == BedrockType.BEDROCK_FLOOR) {
            if (y == min) return 2.0;
            if (y > max)  return -1.0;
            return 1.0 - (double)(y - min) / (double)(max - min);
        } else {
            if (y == min) return 2.0;
            if (y < max)  return -1.0;
            return 1.0 - (double)(max - y) / (double)(max - min);
        }
    }

    /**
     * Returns whether the block at (x, y, z) is bedrock, given a probability
     * pre-computed by {@link #computeProbability}.
     * Performs the full RNG step on the stack with no heap allocation.
     */
    public static boolean inlinedIsBedrock(long deriverSeedLo, long deriverSeedHi,
                                           int x, int y, int z,
                                           double probability) {
        if (probability >= 1.0) return true;
        if (probability <= 0.0) return false;

        long l = (long)(x * 3129871) ^ (long)z * 116129781L ^ (long)y;
        l = l * l * 42317861L + l * 11L;
        long hash = l >> 16;

        long s0 = hash ^ deriverSeedLo;
        long s1 = deriverSeedHi;

        if ((s0 | s1) == 0L) {
            s0 = -7046029254386353131L;
            s1 =  7640891576956012809L;
        }

        long result = Long.rotateLeft(s0 + s1, 17) + s0;
        float f = (float)(result >>> 40) * 5.9604645E-8f;

        return (double) f < probability;
    }

    boolean isBedrock(int x, int y, int z) {
        return inlinedIsBedrock(deriverSeedLo, deriverSeedHi, x, y, z, computeProbability(y));
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
