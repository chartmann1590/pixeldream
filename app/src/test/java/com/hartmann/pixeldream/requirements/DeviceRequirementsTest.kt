package com.hartmann.pixeldream.requirements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRequirementsTest {

    private fun specs(
        totalRamBytes: Long = DeviceRequirements.MIN_RAM_BYTES,
        freeStorageBytes: Long = DeviceRequirements.MIN_FREE_STORAGE_BYTES,
        cpuCores: Int = DeviceRequirements.MIN_CPU_CORES,
        is64Bit: Boolean = true,
        androidApi: Int = DeviceRequirements.MIN_API,
    ) = DeviceSpecs(
        totalRamBytes = totalRamBytes,
        freeStorageBytes = freeStorageBytes,
        cpuCores = cpuCores,
        is64Bit = is64Bit,
        androidApi = androidApi,
    )

    private fun check(report: RequirementReport, label: String): RequirementCheck =
        report.checks.first { it.label == label }

    @Test
    fun `device below minimum RAM fails the RAM check`() {
        val report = DeviceRequirements.evaluate(
            specs(totalRamBytes = 4L * 1024 * 1024 * 1024),
        )
        assertFalse(report.meetsMinimum)
        assertFalse(check(report, "RAM").passed)
    }

    @Test
    fun `device meeting all minimums passes`() {
        val report = DeviceRequirements.evaluate(specs())
        assertTrue(report.meetsMinimum)
        assertTrue(report.checks.all { it.passed })
    }

    @Test
    fun `exactly at minimum values passes`() {
        val report = DeviceRequirements.evaluate(
            specs(
                totalRamBytes = DeviceRequirements.MIN_RAM_BYTES,
                freeStorageBytes = DeviceRequirements.MIN_FREE_STORAGE_BYTES,
                cpuCores = DeviceRequirements.MIN_CPU_CORES,
                is64Bit = true,
                androidApi = DeviceRequirements.MIN_API,
            ),
        )
        assertTrue(report.meetsMinimum)
    }

    @Test
    fun `storage below minimum fails`() {
        val report = DeviceRequirements.evaluate(
            specs(freeStorageBytes = DeviceRequirements.MIN_FREE_STORAGE_BYTES - 1),
        )
        assertFalse(report.meetsMinimum)
        assertFalse(check(report, "Free storage").passed)
        assertTrue(check(report, "RAM").passed)
    }

    @Test
    fun `evaluate produces one check per requirement`() {
        val report = DeviceRequirements.evaluate(specs())
        assertEquals(
            listOf("Android version", "RAM", "Processor", "CPU cores", "Free storage"),
            report.checks.map { it.label },
        )
    }

    @Test
    fun `device below minimum Android API fails the OS check`() {
        val report = DeviceRequirements.evaluate(specs(androidApi = DeviceRequirements.MIN_API - 1))
        assertFalse(report.meetsMinimum)
        assertFalse(check(report, "Android version").passed)
    }

    @Test
    fun `formatBytes renders gigabytes with one decimal`() {
        assertEquals("6.0 GB", DeviceRequirements.formatBytes(6L * 1024 * 1024 * 1024))
        assertEquals("3.8 GB", DeviceRequirements.formatBytes((3.8 * 1024 * 1024 * 1024).toLong()))
    }
}
