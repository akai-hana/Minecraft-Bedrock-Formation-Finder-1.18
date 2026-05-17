package com.mike;

import java.util.ArrayList;
import java.util.Arrays;

public class Main {
    static ArrayList<BedrockBlock> blocks = new ArrayList<>();
    static BedrockReader bedrockReader;

    static long   deriverSeedLo;
    static long   deriverSeedHi;
    static BedrockReader.BedrockType bedrockType;

    // Probability pre-computed once per block at startup; indexed in the same
    // order as the blocks list.
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
        blocks.sort((b1, b2) -> {
            switch (bedrockType) {
                case BEDROCK_FLOOR -> { return b1.y < b2.y ? 1 : -1; }
                case BEDROCK_ROOF  -> { return b1.y < b2.y ? -1 : 1; }
            }
            return 0;
        });
        if (blocks.size() == 0) return;
        blocks.forEach(System.out::println);

        bedrockReader = new BedrockReader(seed, bedrockType);
        deriverSeedLo = bedrockReader.deriverSeedLo;
        deriverSeedHi = bedrockReader.deriverSeedHi;

        // Pre-compute the Y-level probability for every block once here so
        // checkFormation never repeats the lerp math during the search.
        bProbabilities = new double[blocks.size()];
        for (int i = 0; i < blocks.size(); i++)
            bProbabilities[i] = bedrockReader.computeProbability(blocks.get(i).y);

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
        int i = 0;
        for (BedrockBlock block : blocks) {
            boolean bedrock = BedrockReader.inlinedIsBedrock(
                    deriverSeedLo, deriverSeedHi,
                    x + block.x, block.y, z + block.z,
                    bProbabilities[i++]);
            if (block.shouldBeBedrock != bedrock) return false;
        }
        return true;
    }

    enum Direction {
        LEFT,
        RIGHT,
        UP,
        DOWN
    }
}
