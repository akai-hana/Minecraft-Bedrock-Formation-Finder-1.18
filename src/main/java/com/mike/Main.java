package com.mike;

import java.util.ArrayList;
import java.util.Arrays;

public class Main {
    static ArrayList<BedrockBlock> blocks = new ArrayList<>();
    static BedrockReader bedrockReader;

    static long   deriverSeedLo;
    static long   deriverSeedHi;
    static BedrockReader.BedrockType bedrockType;

    static double[] bProbabilities;

    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);

        String[] coordinateString = args[1].split(":");
        int x = Integer.parseInt(coordinateString[0]);
        int z = Integer.parseInt(coordinateString[1]);

        bedrockType = switch (args[2]) {
            case "floor" -> BedrockReader.BedrockType.BEDROCK_FLOOR;
            case "roof"  -> BedrockReader.BedrockType.BEDROCK_ROOF;
            default      -> BedrockReader.BedrockType.BEDROCK_FLOOR;
        };

        Arrays.stream(args).skip(3).forEach((arg) -> blocks.add(new BedrockBlock(arg)));
        if (blocks.size() == 0) return;

        bedrockReader = new BedrockReader(seed, bedrockType);
        deriverSeedLo = bedrockReader.deriverSeedLo;
        deriverSeedHi = bedrockReader.deriverSeedHi;

        // Pre-compute probabilities before sorting so we can rank by them.
        double[] rawProb = new double[blocks.size()];
        for (int i = 0; i < blocks.size(); i++)
            rawProb[i] = bedrockReader.computeProbability(blocks.get(i).y);

        // Sort blocks by descending mismatch probability so checkFormation
        // short-circuits as early as possible on wrong candidate positions.
        Integer[] order = new Integer[blocks.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            double pa = clamp01(rawProb[a]);
            double pb = clamp01(rawProb[b]);
            double ma = blocks.get(a).shouldBeBedrock ? (1.0 - pa) : pa;
            double mb = blocks.get(b).shouldBeBedrock ? (1.0 - pb) : pb;
            return Double.compare(mb, ma);
        });

        ArrayList<BedrockBlock> sortedBlocks = new ArrayList<>();
        bProbabilities = new double[blocks.size()];
        for (int i = 0; i < order.length; i++) {
            sortedBlocks.add(blocks.get(order[i]));
            bProbabilities[i] = rawProb[order[i]];
        }
        blocks = sortedBlocks;

        blocks.forEach(System.out::println);

        Direction direction = Direction.RIGHT;
        int stepsToTake = 1;
        int stepsTaken = 0;
        int sidesUntilIncremental = 0;

        while (true) {
            if (checkFormation(x, z)) {
                System.out.println("Found Bedrock Formation at X:" + x + " Z:" + z);
                break;
            }

            if (stepsTaken >= stepsToTake) {
                stepsTaken = 0;
                sidesUntilIncremental++;
                switch (direction) {
                    case LEFT  -> direction = Direction.DOWN;
                    case RIGHT -> direction = Direction.UP;
                    case UP    -> direction = Direction.LEFT;
                    case DOWN  -> direction = Direction.RIGHT;
                }
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
    }

    static boolean checkFormation(int x, int z) {
        for (int i = 0; i < blocks.size(); i++) {
            BedrockBlock block = blocks.get(i);
            boolean bedrock = BedrockReader.inlinedIsBedrock(
                    deriverSeedLo, deriverSeedHi,
                    x + block.x, block.y, z + block.z,
                    bProbabilities[i]);
            if (block.shouldBeBedrock != bedrock) return false;
        }
        return true;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    enum Direction {
        LEFT,
        RIGHT,
        UP,
        DOWN
    }
}
