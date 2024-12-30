package org.example;

public class BitHacks {
    public static void main(String[] args) {
        long[] tests = {
                0b1010L,           // 10
                0b1000L,           // 8
                0b0111L,           // 7
                0L,                // 0
                0b1111_1111L,      // 255
                Long.MAX_VALUE     // All 1s except sign bit
        };

        for (long test : tests) {
            long result = findNextWithDifferentLeftmostBit(test);
            System.out.printf("Input:  %16s (decimal: %d)%n",
                    Long.toBinaryString(test), test);
            System.out.printf("Output: %16s (decimal: %d)%n%n",
                    Long.toBinaryString(result), result);
        }

        long consumerMask = 0;
        for(int i = 0; i < 5; i++) {
            long nextConsumerBit = findNextWithDifferentLeftmostBit(consumerMask);
            consumerMask |= nextConsumerBit;
            System.out.printf("Next consumer bit: %16s (decimal: %d)%n", Long.toBinaryString(nextConsumerBit), nextConsumerBit);
            System.out.printf("Updated consumerMask: %16s (decimal: %d)%n", Long.toBinaryString(consumerMask), consumerMask);
        }
    }

    public static long findNextWithDifferentLeftmostBit(long v1) {
        if (v1 == 0) return 1;  // Special case for 0

        // Find the position of leftmost set bit
        int leftmostBitPos = 63 - Long.numberOfLeadingZeros(v1);

        // Create a mask with all 1s up to leftmostBitPos
        long mask = (1L << (leftmostBitPos + 1)) - 1;

        // Clear all bits from leftmostBitPos and set the next bit
        return (v1 & ~mask) | (1L << (leftmostBitPos + 1));
    }
}
