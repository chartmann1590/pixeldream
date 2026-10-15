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
    fun maliStaysOnCpuUnlessOverridden() {
        val mali = VulkanDevice.parse("13b5:72120020:Mali-G715")!!
        assertTrue(mali.isMali)
        assertFalse(GpuBackendStore.hardwareAllowed(mali))
        assertTrue(GpuBackendStore.hardwareAllowed(mali, allowMali = true))
        val adreno = VulkanDevice.parse("5143:43050a01:Adreno (TM) 740")!!
        assertTrue(GpuBackendStore.hardwareAllowed(adreno))
    }

    @Test
    fun parsesVulkanDeviceNames() {
        assertEquals("Adreno (TM) 740", VulkanDevice.parse("5143:43050a01:Adreno (TM) 740")!!.name)
        assertNull(VulkanDevice.parse("garbage"))
    }

    @Test
    fun threadsUsePerformanceCores() {
        // Tensor G3: 4 x A510 (1.70 GHz), 4 x A715 (2.37 GHz), 1 x X3 (2.91 GHz).
        val tensorG3 = List(4) { 1704000L } + List(4) { 2367000L } + listOf(2914000L)
        assertEquals(5, CpuTopology.performanceCoreCount(tensorG3))
        // Two fast cores are too few to drop the rest.
        assertNull(CpuTopology.performanceCoreCount(List(6) { 1800000L } + List(2) { 2400000L }))
        // All cores alike: no split.
        assertNull(CpuTopology.performanceCoreCount(List(8) { 2000000L }))
    }
}
