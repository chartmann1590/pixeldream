package com.hartmann.pixeldream.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialModelCatalogTest {
    @Test
    fun productionArtifactsArePinnedAndVerifiable() {
        val models = OfficialModelCatalog.models
        assertEquals(ModelKind.entries.size, models.size)
        assertEquals(models.size, models.map { it.kind }.distinct().size)

        models.forEach { model ->
            assertTrue(model.url.startsWith("https://huggingface.co/"))
            assertFalse(model.url.contains("/resolve/main/"))
            assertTrue(model.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(model.sizeBytes > 1_000_000_000L)
        }
    }
}
