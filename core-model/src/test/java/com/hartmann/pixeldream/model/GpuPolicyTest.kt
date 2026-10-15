package com.hartmann.pixeldream.model

import org.junit.Assert.assertFalse
import org.junit.Test

class GpuPolicyTest {
    @Test
    fun autoNeverPicksGpuOnLowerTiers() {
        GpuBackendStore.AUTO_GPU_SOCS.forEach { soc ->
            assertFalse(GpuBackendStore.autoAllowed(DeviceTier.LOW, soc))
            assertFalse(GpuBackendStore.autoAllowed(DeviceTier.UNSUPPORTED, soc))
        }
    }

    @Test
    fun autoKeepsUnmeasuredChipsOnCpu() {
        assertFalse(GpuBackendStore.autoAllowed(DeviceTier.RECOMMENDED, "Some Unmeasured Chip 9000"))
        assertFalse(GpuBackendStore.autoAllowed(DeviceTier.RECOMMENDED, ""))
    }
}
