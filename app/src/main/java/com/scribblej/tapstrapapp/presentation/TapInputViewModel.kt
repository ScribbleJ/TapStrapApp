package com.scribblej.tapstrapapp.presentation

import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.scribblej.tapstrapapp.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TapInputViewModel(private val sharedPreferences: SharedPreferences) : ViewModel() {

    fun update(tapData: TapData)
    {
        updateMetaKeys(tapData.metaKeys)
        updateModOnce(tapData.modOnce)
        setTapCount(tapData.tapCount)
        updateTapPattern(tapData.tapPattern)
        setCommandLists(tapData.potentialCommandLists)
    }

    //////////////////////////////////////////////////////////////////
    private val _metaKeys = MutableStateFlow(0)
    val metaKeys: StateFlow<Int> = _metaKeys.asStateFlow()
    fun updateMetaKeys(newMetaKeys: Int) {
        _metaKeys.value = newMetaKeys
    }

    //////////////////////////////////////////////////////////////////
    private val _modOnce = MutableStateFlow<Boolean>(false)
    val modOnce: StateFlow<Boolean> = _modOnce.asStateFlow()
    fun updateModOnce(newModOnce: Boolean) {
        _modOnce.value = newModOnce
    }

    //////////////////////////////////////////////////////////////////
    private val _tapCount = MutableStateFlow(0)
    val tapCount: StateFlow<Int> = _tapCount.asStateFlow()
    fun setTapCount(newTapCount: Int) {
        _tapCount.value = newTapCount
    }

    /////////////////////////////////////////////////////////////////
    private val _tapPattern = MutableStateFlow(0)
    val tapPattern: StateFlow<Int> = _tapPattern.asStateFlow()
    // We don't really need to know this, but we need something to indicate
    // unambiguously when a new tap comes in even if it's the same as the last one.
    private val _lifetimeTaps = MutableStateFlow(sharedPreferences.getInt("lifetime_taps", 0))
    val lifetimeTaps: StateFlow<Int> = _lifetimeTaps.asStateFlow()
    fun updateTapPattern(newPattern: Int) {
        _tapPattern.value = newPattern
        _lifetimeTaps.value = _lifetimeTaps.value + 1
        // we get nulls from the Preview mode.
        sharedPreferences.edit()?.putInt("lifetime_taps", _lifetimeTaps.value)?.apply()
    }

    //////////////////////////////////////////////////////////////////
    ///////// CommandLists for this tapPattern
    private val _commandLists = MutableStateFlow<List<CommandList>>(emptyList())
    val commandLists: StateFlow<List<CommandList>> = _commandLists.asStateFlow()
    fun setCommandLists(newCommandLists: List<CommandList>) {
        _commandLists.value = newCommandLists
    }
    private var onCommandListClick: ((CommandList) -> Unit)? = null
    fun setOnCommandListClickCallback(callback: (CommandList) -> Unit) {
        onCommandListClick = callback
    }
    fun commandListClick(commandList: CommandList) {
        onCommandListClick?.invoke(commandList)
    }
}