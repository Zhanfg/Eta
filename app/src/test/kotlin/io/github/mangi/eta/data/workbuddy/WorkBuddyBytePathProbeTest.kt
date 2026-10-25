package io.github.mangi.eta.data.workbuddy

import java.io.BufferedReader
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkBuddyBytePathProbeTest {
    private fun parse(text: String) = WorkBuddyBytePathProbe.parseSse(
        BufferedReader(StringReader(text))
    ) {}

    @Test fun recognizesRealTextAndFinalDone() {
        val result = parse(
            "data: {\"choices\":[{\"delta\":{\"content\":\"O\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"K\"}}]}\n\n" +
                "data: [DONE]\n\n"
        )
        assertEquals(2, result.sseEvents)
        assertEquals(2, result.visibleCharacters)
        assertTrue(result.completed)
    }

    @Test fun missingDoneIsNotSuccessfulCompletion() {
        val result = parse("data: {\"choices\":[{\"delta\":{\"content\":\"text\"}}]}\n\n")
        assertEquals(4, result.visibleCharacters)
        assertFalse(result.completed)
    }

    @Test fun reasoningOnlyDoesNotCountAsVisibleModelReply() {
        val result = parse(
            "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"hidden\"}}]}\n\n" +
                "data: [DONE]\n\n"
        )
        assertEquals(0, result.visibleCharacters)
        assertTrue(result.completed)
    }
}
