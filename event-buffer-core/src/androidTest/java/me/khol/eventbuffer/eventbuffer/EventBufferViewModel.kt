package me.khol.eventbuffer.eventbuffer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.khol.eventbuffer.EventBuffer
import me.khol.eventbuffer.MutableEventBuffer

class EventBufferViewModel : ViewModel() {

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
