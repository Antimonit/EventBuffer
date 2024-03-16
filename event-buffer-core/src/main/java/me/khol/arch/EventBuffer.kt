package me.khol.arch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Though `LiveData` is quite reliable as a stream of **states** in the Android world,
 * it cannot reliably represent a stream of **events**. To provide this functionality
 * the `SingleLiveEvent` was retrofitted on top of `LiveData` but is inherently broken.
 *
 * In contrast to **state**, where we are interested only in the latest value available
 * and reading the same value multiple times or even zero times is perfectly valid, when
 * it comes to **events** we want each value to be consumed once and only once.
 *
 * In other words, we need to assure that no event is dropped (i.e. when there are no
 * active observers or when there are too many emissions at once) and no event is acted
 * on multiple times (i.e. events should not be replayed after reconnecting an observer).
 *
 * Conceptually similar to `SingleLiveEvent`, [EventBuffer] delivers events across the
 * boundary between View and ViewModel. Unlike `SingleLiveEvent`, however, [EventBuffer]
 * guarantees that each event is consumed once and only once.
 *
 * ## Buffering
 *
 * What is an appropriate size for the buffer? Buffer of size 0 would unnecessarily hinder
 * the performance since every [MutableEventBuffer.send] would always suspend until the
 * event is completely processed by the consumer. On the other hand, an unlimited buffer
 * could lead to memory starvation if events are produced faster that the consumer can
 * process for a long period of time.
 *
 * The [EventBuffer] inherits a buffer size of 64 items from [Channel]. If the buffer
 * grows beyond 64 unprocessed events, any further calls to [MutableEventBuffer.send]
 * will suspend. This results in a sensible balance where in most of the cases we don't
 * need to suspend but at the same time the number of unprocessed events waiting in the
 * memory is never more than 64 + number of launched coroutines. In other words, you
 * should not have memory issues unless you launch thousands of coroutines in a loop
 * which all send events into an [EventBuffer].
 *
 * ## A note on multicasting
 *
 * [SharedFlow] supports multicasting natively, but we cannot use it because it drops
 * events immediately when there are no observers. Thus, we have to use lower-level
 * primitive, such as a [Channel]. [Channel], however, does not support multicasting and
 * events are distributed fairly in a FIFO order between all the collecting coroutines.
 *
 * Multicasting events to multiple observers is conceptually problematic, especially when
 * we should support disconnecting and reconnecting of observers and buffering of events
 * when there are no active observers.
 *
 * To deliver buffered events to all observers, we would need an additional mechanism for
 * explicitly starting the delivery of events, akin to what `ConnectableObservable` from
 * RxJava library achieves. This greatly complicates the design and was left out.
 *
 * **Multicasting events across the View–ViewModel boundary is thus discouraged.**
 *
 * Due to the aforementioned issues, only a single subscriber is allowed to [collect]
 * the [EventBuffer] at a time. Any subsequent calls to [collect] while the previous
 * collector is still active will yield an [IllegalStateException].
 *
 * If you need to deliver a single event to multiple observers, consider creating an
 * [EventBuffer] for each observer separately.
 *
 * See also
 * * [[Proposal] Primitive or Channel that guarantees the delivery and processing of items](https://github.com/Kotlin/kotlinx.coroutines/issues/2886)
 * * [Sending events to UI.kt](https://gist.github.com/gmk57/330a7d214f5d710811c6b5ce27ceedaa?permalink_comment_id=3639568#gistcomment-3639568)
 */
interface EventBuffer<Event> {

    /**
     * Collect emissions of [Event]s. Used in the View layer.
     *
     * Always executed on the Main thread.
     *
     * Call to [collect] never finishes normally.
     *
     * Note: The [collector] is purposefully not marked as suspending.
     *
     * Using of cancellable suspending functions (i.e. [ensureActive], [yield], [delay],
     * [withContext], etc.) inside of [collector] would be potentially problematic since
     * it could disrupt the guarantees of having each event processed completely once and
     * only once.
     */
    suspend fun collect(collector: (Event) -> Unit): Nothing
}

interface MutableEventBuffer<Event> : EventBuffer<Event> {

    /**
     * Emit an [Event]. Used in the ViewModel layer.
     *
     * Can be safely invoked from any thread.
     */
    suspend fun send(event: Event)
}

fun <T> MutableEventBuffer(): MutableEventBuffer<T> = AtomicEventBuffer()

private class AtomicEventBuffer<Event> : MutableEventBuffer<Event> {

    /**
     * What options do we have when using `Channel` for events?
     *
     * 1) Use non-suspending `trySend` and
     *    - a) use SUSPEND overflow strategy with a large `capacity` to reduce the chance
     *      `trySend` will fail. If we are over the capacity limit, `trySend` will return
     *      `false`, and the event will be discarded.
     *    - b) use DROP_OLDEST or DROP_LATEST overflow strategy so that `trySend` will
     *      have no chance to fail. If we are over the capacity limit, `trySend` will not
     *      fail but will overwrite another, not-yet-consumed, event.
     * 2) Use suspending `send` with SUSPEND overflow strategy and
     *    - a) propagate the `suspend` modifier upstream.
     *    - b) `launch` a coroutine ourselves and let it suspend.
     *
     * Option 2b) is out of the window since it breaks structured concurrency.
     *
     * Options 1a) and 1b) are for our purposes identical because they both lead to losing
     * some of the events, only the selection of what event to drop differs. This can happen
     * in a couple of ways:
     *
     * * when *multiple* events trigger within a short period, faster than what the consumer
     *   can process, or
     * * when *multiple* events trigger during the window when a View is disconnected from
     *   the ViewModel, either due to
     *    * configuration change destroy-recreate cycle, or
     *    * simply because the View is in a stopped state due to it being temporarily
     *      replaced by another Activity or Fragment.
     *
     * The question here is what should be the capacity? By making it too small we can lose
     * some of the events. Set it too high and we might be wasting resources. What is a good
     * limit? 10? 42? Int.MAX_VALUE?
     *
     * Option 2a) has a slightly different behavior from 1a) and 2b). When the buffer is
     * full, rather than dropping events it will suspend execution instead. This should,
     * however, happen rarely, if ever at all.
     *
     * Alternatively, we could use a RENDEZVOUS capacity and suspend execution on every
     * single event emission. This would provide a more consistent and predictable behavior
     * decoupled from any other work happening in the system. This would, however, in most
     * cases just unnecessarily hinder performance, especially when the View is disconnected
     * or can't keep up processing events.
     *
     * Using the SUSPEND strategy in tandem with suspending `send` is, in a way, similar to
     * an unlimited `capacity`. The difference is that pending events are not waiting within
     * the buffer of the `Channel` but rather each in its own coroutine, potentially blocking
     * further emissions. Lastly, since we do not launch any coroutines ourselves, the number
     * of pending events is limited to the number of coroutines launched by the consumer plus
     * the internal buffer size.
     *
     * Note that:
     * * We can not use `StateFlow` because its implementation is by default conflated.
     * * We can not use `SharedFlow` because events are dropped immediately when there are
     *   no observers.
     */
    private val _exceptions = Channel<Event>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    /**
     * Implementation note:
     *
     * Do not use [consumeAsFlow] since it does not support multiple collectors. Although
     * we do not need support for multiple _simultaneous_ collectors, we need to support
     * multiple _sequential_ collectors being able to resubscribe to the same [EventBuffer]
     * after a configuration change.
     *
     * Furthermore, [consumeAsFlow] causes exceptions thrown on the consumer's side to
     * cancel the channel. With [receiveAsFlow], we have guaranteed that:
     * > Failure or cancellation of the flow collector does not affect the channel.
     */
    private val exceptions = _exceptions.receiveAsFlow()

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
                exceptions.collect(collector)
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
            _exceptions.send(event)
        }
    }
}
