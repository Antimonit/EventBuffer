package me.khol.arch.eventbuffer

import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import me.khol.arch.collect

class EventBufferFragment : Fragment() {

    val viewModel: EventBufferViewModel by viewModels()

    @VisibleForTesting
    val observedEvents = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.events.collect(this) {
            observedEvents += it
        }
    }
}
