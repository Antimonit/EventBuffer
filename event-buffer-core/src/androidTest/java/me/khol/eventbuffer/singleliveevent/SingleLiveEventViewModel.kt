package me.khol.eventbuffer.singleliveevent

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SingleLiveEventViewModel : ViewModel() {

    private val _events = SingleLiveEvent<Int>()
    val events: LiveData<Int> = _events

    fun postValues() {
        viewModelScope.launch {
            // `postValue()` is not constrained to any thread.
            _events.postValue(0)
            delay(10)
            _events.postValue(1)
            _events.postValue(2)
            _events.postValue(3)
            delay(10)
            _events.postValue(4)
        }
    }

    fun setValues() {
        viewModelScope.launch {
            // `setValue()` must be executed on the Main thread.
            _events.value = 0
            delay(10)
            _events.value = 1
            _events.value = 2
            _events.value = 3
            delay(10)
            _events.value = 4
        }
    }
}
