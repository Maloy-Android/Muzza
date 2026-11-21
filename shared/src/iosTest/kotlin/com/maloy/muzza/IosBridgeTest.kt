package com.maloy.muzza

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class IosBridgeTest {
    @Test
    fun cancelledRequestsProduceAnExplicitResponse() = runBlocking {
        val response = iosResponse { throw CancellationException() }
        assertEquals("The request was cancelled.", response.error)
    }
}
