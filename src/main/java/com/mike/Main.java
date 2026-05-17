package com.mike;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

public class Main {

    static BedrockBlock[] blocks;
    static double[]       bProbabilities;
    static int            blockCount;

    static long deriverSeedLo;
    static long deriverSeedHi;

    static final int CHUNK_SIZE = 65_536;

    public static void main(String[] args) {

        long seed = Long.parseLong(args[0]);

        String[] coordinateString = args[1].split(":");
        int startX = Integer.parseInt(coordinateString[0]);
        int startZ = Integer.parseInt(coordinateString[1]);

        BedrockReader.BedrockType bedrockType = switch (args[2]) {
            case "floor" -> BedrockReader.BedrockType.BEDROCK_FLOOR;
            case "roof"  -> BedrockReader.BedrockType.BEDROCK_ROOF;
            default      -> BedrockReader.BedrockType.BEDROCK_FLOOR;
        };

        List<BedrockBlock> rawBlocks = new ArrayList<>();
        Arrays.stream(args).skip(3).forEach(arg -> rawBlocks.add(new BedrockBlock(arg)));
        if (rawBlocks.isEmpty()) return;

        BedrockReader bedrockReader = new BedrockReader(seed, bedrockType);
        deriverSeedLo = bedrockReader.deriverSeedLo;
        deriverSeedHi = bedrockReader.deriverSeedHi;

        int n = rawBlocks.size();
        BedrockBlock[] tempBlocks = rawBlocks.toArray(new BedrockBlock[0]);
        double[] tempPr = new double[n];
        for (int i = 0; i < n; i++) {
            tempPr[i] = bedrockReader.computeProbability(tempBlocks[i].y);
        }

        // Sort blocks by descending mismatch probability so wrong candidates
        // are rejected as early as possible (fewest isBedrock calls per position).
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            double pa = clamp01(tempPr[a]);
            double pb = clamp01(tempPr[b]);
            double ma = tempBlocks[a].shouldBeBedrock ? (1.0 - pa) : pa;
            double mb = tempBlocks[b].shouldBeBedrock ? (1.0 - pb) : pb;
            return Double.compare(mb, ma);
        });

        blocks         = new BedrockBlock[n];
        bProbabilities = new double[n];
        for (int i = 0; i < n; i++) {
            blocks[i]         = tempBlocks[order[i]];
            bProbabilities[i] = tempPr[order[i]];
        }
        blockCount = n;

        for (int i = 0; i < n; i++) {
            System.out.println("BedrockBlock{x=" + blocks[i].x + ", y=" + blocks[i].y
                    + ", z=" + blocks[i].z + ", shouldBeBedrock=" + blocks[i].shouldBeBedrock
                    + ", p=" + String.format("%.3f", clamp01(bProbabilities[i])) + "}");
        }

        int x = startX;
        int z = startZ;
        Direction direction = Direction.RIGHT;
        int stepsToTake = 1, stepsTaken = 0, sidesUntilIncremental = 0;

        int[] chunkX = new int[CHUNK_SIZE];
        int[] chunkZ = new int[CHUNK_SIZE];

        outer:
        while (true) {
            int filled = 0;
            while (filled < CHUNK_SIZE) {
                chunkX[filled] = x;
                chunkZ[filled] = z;
                filled++;

                if (stepsTaken >= stepsToTake) {
                    stepsTaken = 0;
                    sidesUntilIncremental++;
                    direction = direction.next();
                }
                if (sidesUntilIncremental > 2) {
                    sidesUntilIncremental = 0;
                    stepsToTake++;
                }
                switch (direction) {
                    case LEFT  -> x--;
                    case RIGHT -> x++;
                    case UP    -> z++;
                    case DOWN  -> z--;
                }
                stepsTaken++;
            }

            final int   batchSize = filled;
            final int[] bx = chunkX;
            final int[] bz = chunkZ;

            OptionalInt match = IntStream.range(0, batchSize)
                    .parallel()
                    .filter(i -> checkFormation(bx[i], bz[i]))
                    .findFirst();

            if (match.isPresent()) {
                int idx = match.getAsInt();
                System.out.println("Found Bedrock Formation at X:" + bx[idx] + " Z:" + bz[idx]);
                break outer;
            }
        }
    }

    static boolean checkFormation(int x, int z) {
        // Capture statics as finals so each thread works on an immutable snapshot
        // with no risk of seeing a partially-updated state between writes.
        final long           slo   = deriverSeedLo;
        final long           shi   = deriverSeedHi;
        final int            cnt   = blockCount;
        final BedrockBlock[] bs    = blocks;
        final double[]       probs = bProbabilities;
        for (int i = 0; i < cnt; i++) {
            boolean bedrock = BedrockReader.inlinedIsBedrock(
                    slo, shi,
                    x + bs[i].x, bs[i].y, z + bs[i].z,
                    probs[i]);
            if (bs[i].shouldBeBedrock != bedrock) return false;
        }
        return true;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    enum Direction {
        LEFT, RIGHT, UP, DOWN;

        Direction next() {
            return switch (this) {
                case LEFT  -> Direction.DOWN;
                case RIGHT -> Direction.UP;
                case UP    -> Direction.LEFT;
                case DOWN  -> Direction.RIGHT;
            };
        }
    }
}
