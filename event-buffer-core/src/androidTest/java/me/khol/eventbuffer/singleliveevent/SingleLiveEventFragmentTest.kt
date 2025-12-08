package me.khol.eventbuffer.singleliveevent

import androidx.fragment.app.testing.launchFragment
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.doesNotContain

@RunWith(AndroidJUnit4::class)
class SingleLiveEventFragmentTest {

    /**
     * SingleLiveEvent drops events when multiple events are produced while
     * the View has not reached the STARTED state.
     */
    @Test
    fun consumerNotReady() {
        launchFragment<SingleLiveEventFragment>(
            initialState = Lifecycle.State.CREATED,
        ).use { scenario ->
            // The fragment is created and SingleLiveEvent observer registered,
            // but the collection is paused since the Lifecycle hasn't reached
            // the STARTED state yet.

            scenario.onFragment {
                it.viewModel.setValues()
            }

            // Give the ViewModel some time to produce the events.
            // This only blocks the test, not the main thread.
            Thread.sleep(100)

            scenario.moveToState(Lifecycle.State.STARTED)

            scenario.onFragment {
                expectThat(it.observedEvents)
                    .containsExactly(4)
                    .doesNotContain(0, 1, 2, 3)
            }
        }
    }

    /**
     * SingleLiveEvent might not deliver some events when events are produced
     * while the consumer is being recreated due to a configuration change or
     * when temporarily moved to a STOPPED state when not visible on the screen.
     */
    @Test
    fun disconnectedConsumer() {
        launchFragment<SingleLiveEventFragment>().use { scenario ->
            scenario.onFragment {
                it.viewModel.setValues()
            }

            scenario.moveToState(Lifecycle.State.CREATED)

            // Give the ViewModel some time to produce the events.
            // This only blocks the test, not the main thread.
            Thread.sleep(100)

            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onFragment {
                expectThat(it.observedEvents)
                    .containsExactly(0, 4)
                    .doesNotContain(1, 2, 3)
            }
        }
    }

    /**
     * SingleLiveEvent might not deliver some events when multiple events are
     * produced in rapid succession using `postValue(it)`, even if the consumer
     * is active.
     */
    @Test
    fun singleLiveEventPostValue() {
        launchFragment<SingleLiveEventFragment>().use { scenario ->
            scenario.onFragment {
                it.viewModel.postValues()
            }

            // Give the ViewModel some time to produce the events.
            // This only blocks the test, not the main thread.
            Thread.sleep(100)

            scenario.onFragment {
                expectThat(it.observedEvents)
                    .containsExactly(0, 3, 4)
                    .doesNotContain(1, 2)
            }
        }
    }

    /**
     * SingleLiveEvent delivers all events when using `setValue(it)` and the
     * consumer is active, but events must be produced from the Main dispatcher.
     */
    @Test
    fun singleLiveEventSetValue() {
        launchFragment<SingleLiveEventFragment>().use { scenario ->
            scenario.onFragment {
                it.viewModel.setValues()
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
