package com.mike;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

public class Main {

    // Block data in parallel primitive arrays: no object headers, no pointer
    // indirection, stride-1 layout — all arrays fit together in cache lines.
    static int[]     bxs;
    static int[]     bys;
    static int[]     bzs;
    static boolean[] bShouldBeBedrock;
    static double[]  bProbabilities;
    static int       blockCount;

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
        int[]     txs  = new int[n];
        int[]     tys  = new int[n];
        int[]     tzs  = new int[n];
        boolean[] tSBB = new boolean[n];
        double[]  tPr  = new double[n];

        for (int i = 0; i < n; i++) {
            BedrockBlock b = rawBlocks.get(i);
            txs[i]  = b.x;
            tys[i]  = b.y;
            tzs[i]  = b.z;
            tSBB[i] = b.shouldBeBedrock;
            tPr[i]  = bedrockReader.computeProbability(b.y);
        }

        // Sort blocks by descending mismatch probability so wrong candidates
        // are rejected as early as possible (fewest isBedrock calls per position).
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            double pa = clamp01(tPr[a]);
            double pb = clamp01(tPr[b]);
            double ma = tSBB[a] ? (1.0 - pa) : pa;
            double mb = tSBB[b] ? (1.0 - pb) : pb;
            return Double.compare(mb, ma);
        });

        bxs            = new int[n];
        bys            = new int[n];
        bzs            = new int[n];
        bShouldBeBedrock = new boolean[n];
        bProbabilities   = new double[n];
        for (int i = 0; i < n; i++) {
            int j              = order[i];
            bxs[i]             = txs[j];
            bys[i]             = tys[j];
            bzs[i]             = tzs[j];
            bShouldBeBedrock[i] = tSBB[j];
            bProbabilities[i]   = tPr[j];
        }
        blockCount = n;

        for (int i = 0; i < n; i++) {
            System.out.println("BedrockBlock{x=" + bxs[i] + ", y=" + bys[i]
                    + ", z=" + bzs[i] + ", shouldBeBedrock=" + bShouldBeBedrock[i]
                    + ", p=" + String.format("%.3f", clamp01(bProbabilities[i])) + "}");
        }

        // Drive the hot path through the JIT interpreter threshold before the
        // real search begins, so the C2 compiler has already emitted native code
        // (including Long.rotateLeft intrinsification) when the spiral starts.
        warmup();

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
        // Capture statics as finals so each thread works on an immutable snapshot.
        final long     slo   = deriverSeedLo;
        final long     shi   = deriverSeedHi;
        final int      cnt   = blockCount;
        final int[]    xs    = bxs;
        final int[]    ys    = bys;
        final int[]    zs    = bzs;
        final boolean[] sbb  = bShouldBeBedrock;
        final double[]  probs = bProbabilities;
        for (int i = 0; i < cnt; i++) {
            boolean bedrock = BedrockReader.inlinedIsBedrock(
                    slo, shi,
                    x + xs[i], ys[i], z + zs[i],
                    probs[i]);
            if (sbb[i] != bedrock) return false;
        }
        return true;
    }

    // Invoke the hot path ~20 000 times to cross the C2 compilation threshold
    // before the spiral starts.  The dummy accumulator prevents dead-code elimination.
    private static void warmup() {
        long dummy = 0;
        for (int i = 0; i < 20_000; i++) {
            dummy += BedrockReader.inlinedIsBedrock(
                    deriverSeedLo ^ i, deriverSeedHi ^ i,
                    i, 0, i,
                    0.5) ? 1 : 0;
        }
        if (dummy < 0) System.out.println("(warmup: " + dummy + ")");
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
