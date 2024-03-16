[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CircleCI](https://img.shields.io/circleci/build/gh/Antimonit/EventBuffer?label=CircleCI)](https://circleci.com/gh/Antimonit/EventBuffer)
[![codecov](https://codecov.io/gh/Antimonit/EventBuffer/graph/badge.svg?token=XIGVGMYYQT)](https://codecov.io/gh/Antimonit/EventBuffer)

# EventBuffer

Conceptually similar to `SingleLiveEvent`, `EventBuffer` delivers events across the
View–ViewModel boundary.

Unlike `SingleLiveEvent`, however, `EventBuffer` guarantees that each event is consumed **once and
only once**.

## Issues with SingleLiveEvent

Though `LiveData` is quite reliable as a stream of **states** in the Android world, it cannot
reliably represent a stream of **events**. To provide this functionality, the `SingleLiveEvent` was
retrofitted on top of `LiveData`, but this approach is inherently broken.

In contrast to **state**, where we are interested only in the latest value and reading the same
value multiple times or even zero times is perfectly valid, when it comes to **events** we want each
value to be consumed once and only once. In other words, we need to ensure that:

* **no event is dropped** (i.e. when there are no active observers or when there are too many emissions at once)
* **no event is acted on multiple times** (i.e. events should not be replayed after reconnecting an observer)

Extending `SingleLiveEvent` from `LiveData` has one crucial consequence. Since `LiveData` is by
design conflated, `SingleLiveEvent` also inherits this behavior. As a result, `SingleLiveEvent`
cannot queue more than one event at a time and this can violate the requirement that each event is
consumed at least once in two ways:

#### Emitting a burst of events

<details>
<summary>Details</summary>

When sending multiple events in rapid succession, the event stored in `SingleLiveEvent` might get
overwritten before the View had a chance to consume it.

This happens only with `postValue()` and can be worked around by using only `setValue()` from the
Main thread.

When `postValue()` is used, irrespectively of what thread it is invoked from, a `Runnable` callback
invoking `setValue()` is queued up in the Main thread's `Looper`. This is, however, asynchronous. By
the time the callback is finally processed, the `SingleLiveEvent`'s value might have already been
updated again.

> Note: `postValue()` is problematic only in conjunction with `SingleLiveEvent`. It is safe to use
> it with `LiveData` where this conflating behavior is actually expected.

</details>

#### Sending events with no active collector

<details>
<summary>Details</summary>

When the collector is stopped (i.e. in the `CREATED`/`DESTROYED` state), sending events will also
override any undelivered event stored within the `SingleLiveEvent`.

This can happen when *multiple* events trigger during the window when a View is disconnected from
the ViewModel, either due to
* configuration change destroy-recreate cycle, or
* simply because the View is in a stopped state due to it being temporarily replaced by another
  Activity or Fragment.

This can occasionally happen when the timing aligns so perfectly that multiple events are produced
during a configuration change.

More commonly it can happen when a Fragment in a ViewPager scrolls off the screen and transitions to
the `CREATED` state.

</details>

### SharedFlow

It is reasonable to assume that `SharedFlow` might be the replacement for `SingleLiveEvent`, just
like `StateFlow` can fully substitute `LiveData`. And while `SharedFlow` is able to buffer events
sent in a rapid succession, it falls short when an event is emitted while there are no active
observers. In such case, the event is simply dropped.

### Channel

High-level primitives like `SharedFlow` and `StateFlow` are insufficient for our requirements.

* We cannot use `StateFlow` because its implementation is by default conflated.
* We cannot use `SharedFlow` because events are dropped immediately when there are no observers.

What options do we have when using `Channel` for events?

1) Use non-suspending `trySend` and use `SUSPEND` overflow strategy with a large `capacity` to reduce the chance `trySend` will fail. If we are over the capacity limit, `trySend` will return `false`, and the event will be discarded.
2) Use non-suspending `trySend` and use `DROP_OLDEST` or `DROP_LATEST` overflow strategy so that `trySend` will have no chance to fail. If we are over the capacity limit, `trySend` will not fail but will overwrite another, not-yet-consumed, event.
3) Use suspending `send` with `SUSPEND` overflow strategy and propagate the `suspend` modifier upstream.
4) Use suspending `send` with `SUSPEND` overflow strategy and `launch` a coroutine ourselves and let it suspend.

Option 4) is a no-go since it breaks structured concurrency.

Options 1) and 2) are for our purposes identical because they both lead to losing some of the
events and only differ in the selection of events to drop.

Option 3) has a slightly different behavior from 1) and 2). When the buffer is full, rather than
dropping events it will suspend execution instead. Execution will resume again once enough events
in the buffer get processed.

### Buffering

What is an appropriate size for the buffer?

While a buffer of size 0 provides the most consistent and predictable behavior, this would, however,
in most cases just unnecessarily hinder performance since every `MutableEventBuffer.send` would
always suspend until the event is completely processed by the consumer.

On the other hand, an unlimited buffer could lead to memory starvation if events are produced faster
that the consumer can process for a long period of time.

The `EventBuffer` inherits a buffer size of 64 items from `Channel`. If the buffer grows beyond 64
unprocessed events, any further calls to `MutableEventBuffer.send` will suspend. This results in a
sensible balance where in most of the cases we don't need to suspend, but at the same time, the
number of unprocessed events waiting in the memory is never more than 64 + number of launched
coroutines. In other words, you should not have memory issues unless you launch thousands of
coroutines in a loop which all send events into an `EventBuffer`.

### Multicasting

`SharedFlow` supports multicasting natively, but we cannot use it because it drops events
immediately when there are no observers. `Channel`, however, does not support multicasting and
events are distributed fairly in a FIFO order between all the collecting coroutines.

Multicasting events to multiple observers is conceptually problematic, especially when we should
support disconnecting and reconnecting of observers and buffering of events when there are no active
observers.

To deliver buffered events to all observers, we would need an additional mechanism for explicitly
starting the delivery of events, akin to what `ConnectableObservable` from RxJava library achieves.
This greatly complicates the design and was left out.

**Multicasting events across the View–ViewModel boundary is thus discouraged.**

Due to the aforementioned issues, only a single subscriber is allowed to `collect` the `EventBuffer`
at a time. Any subsequent calls to `collect` while the previous collector is still active will yield
an `IllegalStateException`.

If you need to deliver a single event to multiple observers, consider creating an `EventBuffer` for
each observer separately.

See also
* [[Proposal] Primitive or Channel that guarantees the delivery and processing of items](https://github.com/Kotlin/kotlinx.coroutines/issues/2886)
* [Sending events to UI.kt](https://gist.github.com/gmk57/330a7d214f5d710811c6b5ce27ceedaa?permalink_comment_id=3639568#gistcomment-3639568)

## Usage
Similar to `LiveData` and `MutableLiveData`, `EventBuffer` is a read-only interface and sending
events is only possible through the `MutableEventBuffer` interface.

```kt
import me.khol.arch.*

class MyViewModel : ViewModel() {

    private val _events = MutableEventBuffer<Int>()
    val events: EventBuffer<Int> = _events

    fun sendValues() {
        viewModelScope.launch {
            _events.send(0)
            delay(10)
            _events.send(1)
            _events.send(2)
            _events.send(3)
            delay(10)
            _events.send(4)
        }
    }
}
```


Note that in order to guarantee the delivery of events, the `send` method is suspending and
must be executed from a suspending context.

In Android components with a `Lifecycle`, use the `EventBuffer.collect` extension function to
collect events in a lifecycle-aware fashion.

```kt
import me.khol.arch.*

class MyFragment : Fragment() {

    val viewModel: MyViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.events.collect(viewLifecycleOwner) {
            // handle event
        }
    }
}
```

## License

```text
Copyright 2024 David Khol

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

See [LICENSE](LICENSE) for more details.
