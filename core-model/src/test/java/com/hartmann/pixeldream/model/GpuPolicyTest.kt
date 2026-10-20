package com.hartmann.pixeldream.model

import com.hartmann.pixeldream.diffusion.VulkanDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun maliIsAllowedAfterTheDescriptorFix() {
        val mali = VulkanDevice.parse("13b5:72120020:Mali-G715")!!
        assertTrue(mali.isMali)
        assertTrue(GpuBackendStore.hardwareAllowed(mali))
        val adreno = VulkanDevice.parse("5143:43050a01:Adreno (TM) 740")!!
        assertTrue(GpuBackendStore.hardwareAllowed(adreno))
    }

    @Test
    fun autoUsesTheGpuOnMeasuredChipsOnly() {
        assertTrue(GpuBackendStore.autoAllowed(DeviceTier.RECOMMENDED, "Tensor G3"))
        assertFalse(GpuBackendStore.autoAllowed(DeviceTier.LOW, "Tensor G3"))
    }

    @Test
    fun parsesVulkanDeviceNames() {
        assertEquals("Adreno (TM) 740", VulkanDevice.parse("5143:43050a01:Adreno (TM) 740")!!.name)
        assertNull(VulkanDevice.parse("garbage"))
    }
}
