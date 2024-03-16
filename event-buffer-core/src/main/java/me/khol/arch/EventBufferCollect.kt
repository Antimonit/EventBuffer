package me.khol.arch

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Similarly to `LiveData.observe`, the [block] callback is invoked only when the [lifecycleOwner]
 * is in either [Lifecycle.State.STARTED] or [Lifecycle.State.RESUMED] state and is forever disposed
 * of once the [lifecycleOwner] reaches the [Lifecycle.State.DESTROYED] state.
 */
fun <T> EventBuffer<T>.collect(
    lifecycleOwner: LifecycleOwner,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    block: (T) -> Unit,
): Job = lifecycleOwner.lifecycleScope.launch(coroutineContext) {
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        collect(block)
    }
}
