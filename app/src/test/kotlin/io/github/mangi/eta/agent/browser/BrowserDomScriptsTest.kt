package io.github.mangi.eta.agent.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDomScriptsTest {
    @Test
    fun `readable extraction is bounded and preserves absolute urls`() {
        val script = BrowserDomScripts.wrap(BrowserDomScripts.readable(offset = 0, maxChars = 8_000))

        assertTrue(script.contains("!visible(node)"))
        assertTrue(script.contains("remainingNodes: 8000"))
        assertTrue(script.contains("deadline: Date.now() + 750"))
        assertTrue(script.contains("return boundedString(parsed.href"))
        assertFalse(script.contains("parsed.protocol !== 'https:'"))
        assertFalse(script.contains("innerText"))
        assertFalse(script.contains("textContent"))
    }

    @Test
    fun `browser click rejects hidden disabled or occluded targets instead of reporting success`() {
        val script = BrowserDomScripts.wrap(
            BrowserDomScripts.click(selector = "#submit", x = null, y = null)
        )

        assertTrue(script.contains("document.querySelectorAll(selector)"))
        assertTrue(script.contains("if (visible(matches[index]))"))
        assertTrue(script.contains("TARGET_NOT_VISIBLE"))
        assertTrue(script.contains("TARGET_DISABLED"))
        assertTrue(script.contains("TARGET_OCCLUDED"))
        assertTrue(script.contains("document.elementFromPoint(cx, cy)"))
        assertTrue(script.contains("requireHitTarget(target)"))
        assertTrue(script.contains("event_dispatched: true, action_verified: false"))
    }

    @Test
    fun `browser input verifies DOM value but not remote form submission`() {
        val script = BrowserDomScripts.wrap(
            BrowserDomScripts.type(selector = "#search", x = null, y = null, text = "hello", submit = true)
        )
        assertTrue(script.contains("requireHitTarget(target)"))
        assertTrue(script.contains("INPUT_NOT_APPLIED"))
        assertTrue(script.contains("input_value_verified: applied"))
        assertTrue(script.contains("submit_effect_verified: false"))
    }

    @Test
    fun `dom text extraction signals when screenshot vision is needed`() {
        val readable = BrowserDomScripts.wrap(BrowserDomScripts.readable(offset = 0, maxChars = 8_000))
        val text = BrowserDomScripts.wrap(BrowserDomScripts.text(selector = null, offset = 0, maxChars = 8_000))

        for (script in listOf(readable, text)) {
            assertTrue(script.contains("img,canvas,svg,object,embed,video"))
            assertTrue(script.contains("visual_inspection_recommended: visualHint.visual_inspection_recommended"))
            assertTrue(script.contains("visual_element_count: visualHint.visual_element_count"))
            assertTrue(script.contains("extractedChars < 32"))
        }
    }
}
