package me.khol.eventbuffer.eventbuffer

import androidx.fragment.app.testing.launchFragment
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import strikt.api.expectThat
import strikt.assertions.containsExactly

@RunWith(AndroidJUnit4::class)
class EventBufferFragmentTest {

    /**
     * EventBuffer drops no events even when multiple events are produced while
     * the View is in the STOPPED state.
     */
    @Test
    fun consumerNotReady() {
        launchFragment<EventBufferFragment>(
            initialState = Lifecycle.State.CREATED,
        ).use { scenario ->
            // The fragment is created and EventBuffer collector registered,
            // but the collection is paused since the Lifecycle hasn't reached
            // the STARTED state yet.

            scenario.onFragment {
                it.viewModel.sendValues()
            }

            // Give the ViewModel some time to produce the events.
            // This only blocks the test, not the main thread.
            Thread.sleep(100)

            scenario.moveToState(Lifecycle.State.STARTED)

            scenario.onFragment {
                expectThat(it.observedEvents)
                    .containsExactly(0, 1, 2, 3, 4)
            }
        }
    }

    /**
     * EventBuffer delivers all events even when events are produced while
     * the consumer is being recreated due to a configuration change or when
     * temporarily moved to a STOPPED state when not visible on the screen.
     */
    @Test
    fun disconnectedConsumer() {
        launchFragment<EventBufferFragment>().use { scenario ->
            scenario.onFragment {
                it.viewModel.sendValues()
            }

            scenario.moveToState(Lifecycle.State.CREATED)

            // Give the ViewModel some time to produce the events.
            // This only blocks the test, not the main thread.
            Thread.sleep(100)

            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onFragment {
                expectThat(it.observedEvents)
                    .containsExactly(0, 1, 2, 3, 4)
            }
        }
    }

    /**
     * EventBuffer delivers all events even when multiple events are produced
     * in rapid succession.
     */
    @Test
    fun sendValues() {
        launchFragment<EventBufferFragment>(
            initialState = Lifecycle.State.RESUMED,
        ).use { scenario ->
            scenario.onFragment {
                it.viewModel.sendValues()
            }

            // Give the ViewModel some time to produce the events.
            // This only blocks the test, not the main thread.
            Thread.sleep(100)

            scenario.onFragment {
                expectThat(it.observedEvents)
                    .containsExactly(0, 1, 2, 3, 4)
            }
        }
    }
}
