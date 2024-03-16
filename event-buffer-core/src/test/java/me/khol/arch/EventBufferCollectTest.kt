package me.khol.arch

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import me.khol.test.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly

@ExperimentalCoroutinesApi
class EventBufferCollectTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var registry: LifecycleRegistry
    private lateinit var lifecycleOwner: LifecycleOwner

    private lateinit var buffer: MutableEventBuffer<String>
    private lateinit var observedEvents: MutableList<String>

    @Before
    fun setUp() {
        lifecycleOwner = object : LifecycleOwner {
            override val lifecycle: Lifecycle
                get() = registry
        }
        registry = LifecycleRegistry(lifecycleOwner)
        registry.currentState = Lifecycle.State.CREATED

        buffer = MutableEventBuffer()
        observedEvents = mutableListOf()
        buffer.collect(lifecycleOwner) {
            observedEvents += it
        }
    }

    private suspend inline fun assertValues(vararg elements: String) {
        delay(1)
        expectThat(observedEvents).containsExactly(elements.toList())
    }

    @Test
    fun `eventBuffer collect does not emit if lifecycle is stopped`() = runTest {
        assertValues()
        buffer.send("1")
        assertValues()
        buffer.send("2")
        assertValues()
    }

    @Test
    fun `eventBuffer collect emits if lifecycle is started`() = runTest {
        registry.currentState = Lifecycle.State.STARTED
        assertValues()
        buffer.send("1")
        assertValues("1")
        buffer.send("2")
        assertValues("1", "2")
    }

    @Test
    fun `eventBuffer collect pauses temporarily if lifecycle is stopped`() = runTest {
        registry.currentState = Lifecycle.State.STARTED
        assertValues()
        buffer.send("1")
        assertValues("1")
        registry.currentState = Lifecycle.State.CREATED
        assertValues("1")
        buffer.send("2")
        assertValues("1")
        registry.currentState = Lifecycle.State.STARTED
        assertValues("1", "2")
    }

    @Test
    fun `eventBuffer collect stops emitting once lifecycle reaches destroyed state`() = runTest {
        registry.currentState = Lifecycle.State.STARTED
        assertValues()
        buffer.send("1")
        assertValues("1")
        registry.currentState = Lifecycle.State.DESTROYED
        assertValues("1")
        buffer.send("2")
        assertValues("1")
        registry.currentState = Lifecycle.State.STARTED
        assertValues("1")
    }
}
