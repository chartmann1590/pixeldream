package com.hartmann.pixeldream.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTierDetectorTest {

    @Test
    fun flagshipIsRecommended() {
        // OnePlus 13R class: Snapdragon 8 Gen 3, 12 GB, 8 cores, 3.3 GHz.
        assertEquals(DeviceTier.RECOMMENDED, DeviceTierDetector.classify(12_288, 8, 3_300, true))
    }

    @Test
    fun eightGigabytesWithUnknownClockIsRecommended() {
        assertEquals(DeviceTier.RECOMMENDED, DeviceTierDetector.classify(8_192, 8, 0, true))
    }

    @Test
    fun sixGigabytesIsLow() {
        assertEquals(DeviceTier.LOW, DeviceTierDetector.classify(6_144, 8, 2_800, true))
    }

    @Test
    fun slowBigCoreIsLow() {
        // Snapdragon 695 / Helio G99 class phones top out around 2.2 GHz.
        assertEquals(DeviceTier.LOW, DeviceTierDetector.classify(8_192, 8, 2_200, true))
    }

    @Test
    fun fewCoresIsLow() {
        assertEquals(DeviceTier.LOW, DeviceTierDetector.classify(8_192, 6, 3_000, true))
    }

    @Test
    fun underMinimumRamOrThirtyTwoBitIsUnsupported() {
        assertEquals(DeviceTier.UNSUPPORTED, DeviceTierDetector.classify(4_096, 8, 3_000, true))
        assertEquals(DeviceTier.UNSUPPORTED, DeviceTierDetector.classify(8_192, 8, 3_000, false))
    }
}
