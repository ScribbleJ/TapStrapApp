package com.scribblej.tapstrapapp

import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.preference.PreferenceManager

import com.tapwithus.sdk.TapSdk
import com.tapwithus.sdk.TapSdkFactory

import android.view.View
import android.view.inputmethod.InputConnection
import android.view.inputmethod.EditorInfo
import androidx.annotation.NonNull

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner


import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.scribblej.tapstrapapp.presentation.TapInputView


import com.scribblej.tapstrapapp.presentation.TapInputViewModel
import com.tapwithus.sdk.mode.TapInputMode
import com.tapwithus.sdk.mouse.MousePacket

// For managing the timeouts without race conditions
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


class TapInputMethodService :   LifecycleInputMethodService(),
                                ViewModelStoreOwner,
                                SavedStateRegistryOwner {

    private val tapController: TapController = TapController
    private lateinit var tapInputViewModel: TapInputViewModel

    // TODO: check whether input is even wanted?
    fun sendKey(keyEvent: KeyEvent) {
        if (currentInputConnection == null)
        {
            Log.d("foo", "InputConnection is null.")
            return
        }

        val inputConnection: InputConnection = currentInputConnection ?: return
        inputConnection.sendKeyEvent(keyEvent)
    }

    fun onTapInputReceived(tapData: TapData) {
        // TODO: Set callback so these things work.


        tapInputViewModel.updateTapPattern(tapData.tapPattern)
        tapInputViewModel.setCommandLists(tapData.potentialCommandLists)
        tapInputViewModel.setTapCount(tapData.tapCount)

    }

    override fun onCreateInputView(): View {
        val view = TapInputView(this, tapInputViewModel)
        Log.d("foo","Create View.")
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        return view
    }

    fun updateViewModel(tapData: TapData) {
        tapInputViewModel.update(tapData)
    }

    override fun onCreate() {
        super.onCreate()

        savedStateRegistryController.performRestore(null)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        tapInputViewModel = TapInputViewModel(sharedPreferences)

        // Settings
        val multiTapTimeout = sharedPreferences.getInt("multi_tap_timeout", 300)
        val loopMultiTaps   = sharedPreferences.getBoolean("loop_multi_taps", false)

        ////// This callback is to allow for the user to select a CommandList from the UI and execute it
        // rather than tapping to it as God intended.
        val commandListClickCallback: (CommandList) -> Unit = { commandList ->
            tapController.executeCommandList(commandList)
            Log.d("foo", "Clicked and executed commandList.")
        }

        tapInputViewModel.setOnCommandListClickCallback(commandListClickCallback)

        tapController.initialize(appContext = this,
                                 tapTimeout = multiTapTimeout.toLong(),
                                 loopMultiTaps = loopMultiTaps,
                                 keyEventReceiverCallback = ::sendKey,
                                 tapEventReceiverCallback = ::updateViewModel,
        )

        Log.d("foo", "Created.  Timeout is $multiTapTimeout. loopMultiTaps is $loopMultiTaps")
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        tapController.resume()
        Log.d("foo", "Start Input.")
    }

    override fun onFinishInput() {
        super.onFinishInput()
        Log.d("foo", "Finish Input.")
        tapController.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO: ??? Do we need this call?
        // sdk.unregisterTapListener(this)

        Log.d("foo","Destroy.")

    }




    //  Garbage required to support service that Activities do for themselves.
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val lifecycle: Lifecycle
        get() = dispatcher.lifecycle
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = store

}
