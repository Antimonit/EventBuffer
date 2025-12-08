package me.khol.eventbuffer.testing

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import me.khol.eventbuffer.MultipleConcurrentCollectorsException
import me.khol.eventbuffer.MutableEventBuffer
import me.khol.test.MainCoroutineRule
import org.junit.Rule
import org.junit.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

@OptIn(ExperimentalCoroutinesApi::class)
class EventBufferConsumeAsFlowTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @Test
    fun `All events are consumed by the currently connected Flow`() = runTest {
        val events = MutableEventBuffer<Int>()
        val flow = events.consumeAsFlow()

        events.send(1)
        events.send(2)
        flow.test {
            expectThat(awaitItem()).isEqualTo(1)
            expectThat(awaitItem()).isEqualTo(2)
        }

        flow.test {
            expectNoEvents()
            events.send(3)
            expectThat(awaitItem()).isEqualTo(3)
        }
    }

    @Test
    fun `Multiple concurrent collectors are not supported`() = runTest {
        val events = MutableEventBuffer<Unit>()
        val flow = events.consumeAsFlow()

        turbineScope {
            val turbine1 = flow.testIn(backgroundScope)
            val turbine2 = flow.testIn(backgroundScope)

            events.send(Unit)
            expectThat(turbine1.awaitItem()).isEqualTo(Unit)
            expectThat(turbine2.awaitError()).isA<MultipleConcurrentCollectorsException>()
        }
    }
}