package me.khol.arch

import me.khol.test.MainCoroutineRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

@OptIn(ExperimentalCoroutinesApi::class)
class EventBufferTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @Test
    fun `single event`() = runTest {
        val buffer = MutableEventBuffer<String>()

        // consumer
        val events = mutableListOf<String>()
        backgroundScope.launch {
            buffer.collect {
                events += it
            }
        }

        // producer
        launch {
            delay(10)
            buffer.send("First")
        }

        expectThat(events).isEmpty()
        delay(20)
        expectThat(events).containsExactly("First")
    }

    @Test
    fun `multiple events with no observer are queued`() = runTest {
        val buffer = MutableEventBuffer<String>()

        // consumer
        val events = mutableListOf<String>()
        backgroundScope.launch {
            delay(100)
            buffer.collect {
                events += it
            }
        }

        // producer
        launch {
            buffer.send("First")
            delay(10)
            buffer.send("Second")
            delay(10)
            buffer.send("Third")
        }

        // An observer does not exist at the moment of emissions.
        delay(90)
        expectThat(events).isEmpty()
        delay(20)
        // All queued events should be delivered at once
        expectThat(events).containsExactly("First", "Second", "Third")
        // No events should be dropped.
    }

    @Test
    fun `multiple events in a rapid succession are queued`() = runTest {
        val buffer = MutableEventBuffer<String>()

        // consumer
        val events = mutableListOf<String>()
        backgroundScope.launch {
            buffer.collect {
                runBlocking { delay(10) }
                events += it
            }
        }

        // producer
        launch {
            buffer.send("First")
            delay(5)
            buffer.send("Second")
            delay(5)
            buffer.send("Third")
        }

        // An observer exists but can't keep up with emissions.

        // Although coroutines delay is quite precise on its own, it becomes
        // very imprecise when it actually blocks the thread.
        // A 10ms delay may last ranging anywhere from 10ms to over 50ms.
        // Instead of checking contents of [events] at specified time marks,
        // we resort to sampling the data instead.

        val observed = buildSet {
            repeat(100) {
                add(events.toList())
                delay(1)
            }
        }

        expectThat(observed)
            .containsExactly(
                listOf(),
                listOf("First"),
                listOf("First", "Second"),
                listOf("First", "Second", "Third"),
            )
        // No events should be dropped.
    }

    @Test
    fun `suspend execution when buffer is full`() = runTest {
        val buffer = MutableEventBuffer<String>()

        // consumer
        val events = mutableListOf<String>()
        backgroundScope.launch {
            delay(100)
            buffer.collect {
                events += it
            }
        }

        // producer
        var sentEvents = 0
        launch {
            repeat(100) {
                buffer.send("Event")
                sentEvents++
            }
        }

        delay(10)
        // `buffer.send` will suspend after reaching 64 undelivered events.
        // See `Channel.CHANNEL_DEFAULT_CAPACITY`.
        expectThat(sentEvents).isEqualTo(64)
    }

    @Test
    fun `events are collected synchronously even if producer and consumer run on different dispatchers`() = // ktlint-disable max-line-length
        runTest {
            val buffer = MutableEventBuffer<String>()

            // consumer
            val events = mutableListOf<String>()
            backgroundScope.launch(Dispatchers.IO) {
                buffer.collect {
                    events += it
                }
            }
            runBlocking { delay(1) }

            // producer
            expectThat(events).isEmpty()
            withContext(Dispatchers.IO) {
                buffer.send("First")
            }
            expectThat(events).containsExactly("First")
        }

    @Test
    fun `do not drop emissions even when an observer disconnects and reconnects`() = runTest {
        val buffer = MutableEventBuffer<String>()

        // consumers
        val events = mutableListOf<String>()
        backgroundScope.launch {
            val job = launch {
                buffer.collect {
                    events += "A $it"
                }
            }

            // simulate a configuration change
            delay(15)
            job.cancel()
            delay(20)

            launch {
                buffer.collect {
                    events += "B $it"
                }
            }
        }

        // producer
        launch {
            delay(10)
            buffer.send("First")
            delay(10)
            buffer.send("Second")
            delay(10)
            buffer.send("Third")
        }

        delay(5)
        expectThat(events).isEmpty()
        delay(15)
        expectThat(events).containsExactly("A First")
        delay(20)
        expectThat(events).containsExactly("A First", "B Second", "B Third")
    }

    @Test
    fun `multiple concurrent observers are not supported`() = runTest {
        val buffer = MutableEventBuffer<String>()

        backgroundScope.launch {
            buffer.collect { /* ignored */ }
        }

        launch {
            try {
                buffer.collect { /* ignored */ }
            } catch (ex: IllegalStateException) {
                // pass
            } catch (ex: CancellationException) {
                error("Multiple collectors should terminate violently.")
            }
        }
    }

    @Test
    fun `multiple serial observers are supported`() = runTest {
        val buffer = MutableEventBuffer<String>()

        val job1 = launch {
            buffer.collect { /* ignored */ }
        }
        delay(10)
        job1.cancel()

        val job2 = launch {
            buffer.collect { /* ignored */ }
        }
        delay(10)
        job2.cancel()
    }
}
