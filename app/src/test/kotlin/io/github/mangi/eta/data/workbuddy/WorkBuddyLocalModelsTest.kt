package io.github.mangi.eta.data.workbuddy

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkBuddyLocalModelsTest {
    @Test fun cnEnterpriseOnlyShowsCliModelsAndDeclaredToolCapabilities() {
        val data = JSONObject("""
            {"models":[
              {"id":"glm-5.2","name":"GLM","supportsToolCall":true,"maxInputTokens":100000},
              {"id":"nes-embedding","supportsToolCall":false},
              {"id":"hidden-model"}
            ],"agents":[{"name":"cli","models":["glm-5.2","nes-embedding"]}]}
        """.trimIndent())
        val models = WorkBuddyLocalModels.parseCatalog(data, global = false, v3 = false)
        assertEquals(listOf("glm-5.2"), models.map { it.modelId })
        assertTrue(models.single().supportsTools)
        assertEquals(100000, models.single().contextWindow)
    }

    @Test fun globalIncludesModelsNotInCnCliListAndDoesNotInventTools() {
        val data = JSONObject("""
            {"models":[{"id":"global-model"},{"id":"cli-model","supportsToolCall":false}],
              "agents":[{"name":"cli","models":["cli-model"]}]}
        """.trimIndent())
        val models = WorkBuddyLocalModels.parseCatalog(data, global = true, v3 = false)
        assertEquals(2, models.size)
        assertNull(models[0].toolCall)
        assertFalse(models[1].supportsTools)
    }

    @Test fun v3ReturnsModelsEvenWithoutAgents() {
        val data = JSONObject("""{"models":[{"id":"reason-model","supportsReasoning":true}]}""")
        val models = WorkBuddyLocalModels.parseCatalog(data, global = false, v3 = true)
        assertEquals("reason-model", models.single().modelId)
        assertTrue(models.single().supportsReasoning)
    }

    @Test fun narrowStringArrayIsSupported() {
        val models = WorkBuddyLocalModels.parseCatalog(
            JSONArray("""["one","two"]"""), global = true, v3 = true
        )
        assertEquals(listOf("one","two"), models.map { it.modelId })
    }

    @Test fun disabledAndNonChatModelsAreExcluded() {
        val data = JSONObject("""
            {"models":[{"id":"disabled","disabled":true},
                       {"id":"tiny","maxOutputTokens":32},
                       {"id":"image","tags":["text-to-image"]},
                       {"id":"valid"}]}
        """.trimIndent())
        assertEquals(listOf("valid"), WorkBuddyLocalModels.parseCatalog(data, false, true).map { it.modelId })
    }
}
