package me.khol.eventbuffer.testing

import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import me.khol.eventbuffer.EventBuffer
import me.khol.eventbuffer.MultipleConcurrentCollectorsException

/**
 * This extension is not part of the main source set because the `collector` parameter of
 * [EventBuffer.collect] is purposefully not marked suspending. Suspending the `collector`
 * would break the guarantees of safely consuming each event completely, because it would
 * allow the Android framework to move the Lifecycle to the stopped state, causing a crash
 * if it would execute certain lifecycle-sensitive function after resuming its execution.
 *
 * In tests, we do not typically observe similar issues.
 *
 * Testing [Flow] should be easier, especially when combined with a mature testing library
 * such as [Turbine](https://github.com/cashapp/turbine).
 *
 * **Note:** this method consumes all the buffered events in the [EventBuffer] and continues
 * delivering all future events to the same [Flow] as long as the collecting coroutine is
 * active. Any attempts to consume the same [EventBuffer] multiple times concurrently will
 * result in an [MultipleConcurrentCollectorsException].
 */
fun <E> EventBuffer<E>.consumeAsFlow(): Flow<E> =
    callbackFlow {
        collect {
            trySendBlocking(it).getOrThrow()
        }
    }
