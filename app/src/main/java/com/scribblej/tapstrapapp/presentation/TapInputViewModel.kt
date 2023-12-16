package com.scribblej.tapstrapapp.presentation


import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.scribblej.tapstrapapp.CommandList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TapInputViewModel(private val sharedPreferences: SharedPreferences) : ViewModel() {

    // Display MetaKeys State
    private val _metaKeys = MutableLiveData<Int>()
    val metaKeys: LiveData<Int> = _metaKeys

    private val _modOnce = MutableLiveData<Boolean>()
    val modOnce: LiveData<Boolean> = _modOnce

    fun updateMetaKeys(newMetaKeys: Int) {
        _metaKeys.value = newMetaKeys
    }

    fun updateModOnce(newModOnce: Boolean) {
        _modOnce.value = newModOnce
    }


    var tapCount = mutableStateOf(0)

    fun setTapCount(newTapCount: Int) {
        tapCount.value = newTapCount
    }

    private val _tapPattern = MutableStateFlow(0)
    val tapPattern: StateFlow<Int> = _tapPattern.asStateFlow()

    // We don't really need to know this, but we need something to indicate
    // unambiguously when a new tap comes in even if it's the same as the last one.
    private val _lifetimeTaps = MutableStateFlow(sharedPreferences.getInt("lifetime_taps", 0))
    val lifetimeTaps: StateFlow<Int> = _lifetimeTaps.asStateFlow()

    fun updateTapPattern(newPattern: Int) {
        _tapPattern.value = newPattern
        _lifetimeTaps.value = _lifetimeTaps.value + 1
        sharedPreferences.edit().putInt("lifetime_taps", _lifetimeTaps.value).apply()
    }

    // the commandLists for the current TapPattern
    var commandLists = mutableStateOf<List<CommandList>>(emptyList())

    fun setCommandLists(newCommandLists: List<CommandList>) {
        commandLists.value = newCommandLists
    }

    ////// This section is for making the CommandLists in the UI clickable:
    private var onCommandListClick: ((CommandList) -> Unit)? = null

    fun setOnCommandListClickCallback(callback: (CommandList) -> Unit) {
        onCommandListClick = callback
    }

    fun commandListClick(commandList: CommandList) {
        onCommandListClick?.invoke(commandList)
    }
}