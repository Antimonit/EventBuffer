package me.khol.eventbuffer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Conceptually similar to `SingleLiveEvent`, [EventBuffer] delivers events across the
 * boundary between View and ViewModel. Unlike `SingleLiveEvent`, however, [EventBuffer]
 * guarantees that each event is consumed once and only once.
 *
 * The [EventBuffer] inherits a buffer size of 64 items from [Channel]. If the buffer
 * grows beyond 64 unprocessed events, any further calls to [MutableEventBuffer.send]
 * will suspend.
 *
 * Only a single subscriber is allowed to [collect] the [EventBuffer] at a time. Any
 * subsequent calls to [collect] while the previous collector is still active will
 * yield an [IllegalStateException]. If you need to deliver a single event to multiple
 * observers, consider creating an [EventBuffer] for each observer separately.
 */
interface EventBuffer<Event> {

    /**
     * Collect emissions of [Event]s.
     *
     * [collector] is always executed on the [Dispatchers.Main].
     *
     * Call to [collect] never finishes normally.
     *
     * Note: The [collector] is purposefully not marked as suspending. Use of cancellable
     * suspending functions (i.e. [ensureActive], [yield], [delay], [withContext], etc.)
     * inside of [collector] would be potentially problematic since it could disrupt the
     * guarantees of having each event processed completely once and only once.
     */
    suspend fun collect(collector: (Event) -> Unit): Nothing
}

interface MutableEventBuffer<Event> : EventBuffer<Event> {

    /**
     * Emit an [Event].
     *
     * Can be safely invoked from any thread.
     */
    suspend fun send(event: Event)
}

fun <T> MutableEventBuffer(): MutableEventBuffer<T> = AtomicEventBuffer()

private class AtomicEventBuffer<Event> : MutableEventBuffer<Event> {

    private val _events = Channel<Event>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    // Do not use [consumeAsFlow] since it does not support multiple collectors. Although
    // we do not need support for multiple _simultaneous_ collectors, we need to support
    // multiple _sequential_ collectors being able to resubscribe to the same EventBuffer
    // after a configuration change.
    //
    // Furthermore, [consumeAsFlow] causes exceptions thrown on the consumer's side to
    // cancel the channel. With [receiveAsFlow], we have a guarantee that:
    // > Failure or cancellation of the flow collector does not affect the channel.
    private val events = _events.receiveAsFlow()

    // No need for synchronization primitives as we are already synchronized by the main
    // dispatcher.
    private var isCollecting = false

    override suspend fun collect(collector: (Event) -> Unit): Nothing {
        // It is critical to use the `Main.immediate` dispatcher here.
        //
        // The `Main.immediate` acts as a form of synchronization where both producer and
        // consumer run in the same "frame" without giving the Android framework a chance
        // to move between Lifecycle states.
        //
        // Without `Main.immediate`, it would be possible for an event to be delivered to
        // a consumer that is already in a STOPPED state and thus cannot freely act upon
        // the event (e.g. update the UI state or perform fragment transactions) and the
        // event would be dropped.
        withContext(Dispatchers.Main.immediate) {
            if (isCollecting) throw MultipleConcurrentCollectorsException()
            try {
                isCollecting = true
                events.collect(collector)
            } finally {
                isCollecting = false
            }
        }
        // Because "Failure or cancellation of the flow collector does not affect the
        // channel", the channel can never be closed and thus the `collect` method can
        // never finish normally.
        //
        // This behavior is similar to the one of [SharedFlow#collect].
        error("A call to collect can never finish normally.")
    }

    // We did not add the non-suspending `trySend` variant on purpose since, by design,
    // `trySend` does not guarantee delivery of the event.
    // The whole point of `EvenBuffer` is to guarantee delivery of each event exactly once,
    // which cannot be achieved with `trySend`.

    override suspend fun send(event: Event) {
        // Must run on the `Main` dispatcher.
        //
        // See `collect` for details.
        withContext(Dispatchers.Main) {
            _events.send(event)
        }
    }
}
