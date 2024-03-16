package me.khol.arch.singleliveevent

import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels

class SingleLiveEventFragment : Fragment() {

    val viewModel: SingleLiveEventViewModel by viewModels()

    @VisibleForTesting
    val observedEvents = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.events.observe(this) {
            observedEvents += it
        }
    }
}
