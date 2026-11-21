package com.maloy.muzza

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class IosAccountTest {
    @Test
    fun normalizesCookieSeparatorsForTheYouTubeClient() {
        assertEquals(
            "SAPISID=dummy; SID=other",
            normalizedAccountCookie(" SAPISID=dummy;SID=other "),
        )
    }

    @Test
    fun rejectsMissingAuthenticationAndHeaderInjection() {
        assertFailsWith<IllegalArgumentException> { normalizedAccountCookie("SID=dummy") }
        assertFailsWith<IllegalArgumentException> { normalizedAccountCookie("SAPISID=dummy\r\nX-Injected: value") }
        assertFailsWith<IllegalArgumentException> { normalizedAccountCookie("SAPISID=dummy; bad name=value") }
    }

    @Test
    fun rejectsInvalidVisitorData() {
        assertNull(validatedVisitorData("  "))
        assertEquals("visitor", validatedVisitorData(" visitor "))
        assertFailsWith<IllegalArgumentException> { validatedVisitorData("visitor\nheader") }
    }
}
