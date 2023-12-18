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

// For managing the timeouts without race conditions
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


class TapInputMethodService : LifecycleInputMethodService(),
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    TapAdapter {

    private lateinit var sdk: TapSdk

    private lateinit var tapInputViewModel: TapInputViewModel

    private val keyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)

    private fun toggleModifier(mK: Int) : Array<KeyEvent> {
        MetaKeysManager.toggleMetaKey(mK)
        return arrayOf()
    }

    private fun modifyOnce(mK: Int) : Array<KeyEvent> {
        MetaKeysManager.activateMetaKeys(mK, true)
        return arrayOf()
    }

    private fun prepareMapSwitch() : Array<KeyEvent> {
        mapSwitchFlag = true
        return arrayOf()
    }

    private fun tapMouseStart() : Array<KeyEvent> {
        sdk.connectedTaps.forEach() { sdk.startControllerWithMouseHIDMode(it) }
        return arrayOf()
    }
    private fun tapMouseStop() : Array<KeyEvent> {
        sdk.connectedTaps.forEach() { sdk.startControllerMode(it) }
        return arrayOf()
    }

    // TODO: Map these strings to the KeyEvents in the map initializer so we aren't doing string hash lookups in the tap code.
    // Then use the KeyEvent (Int) as the lookup value here.
    private val actionMap: Map<String, () -> Array<KeyEvent>> = mapOf(
        "MAPSWITCH" to { prepareMapSwitch() },
        "STARTMOUSE" to { tapMouseStart() },
        "STOPMOUSE"  to { tapMouseStop() },
        "CTRL"  to { toggleModifier(KeyEvent.META_CTRL_ON) },
        "ALT"   to { toggleModifier(KeyEvent.META_ALT_ON) },
        "SHIFT" to { toggleModifier(KeyEvent.META_SHIFT_ON) },
        "META"  to { toggleModifier(KeyEvent.META_META_ON) },
        "CAPSLOCK"  to {toggleModifier(KeyEvent.META_CAPS_LOCK_ON) },
        "NUMLOCK"   to {toggleModifier(KeyEvent.META_NUM_LOCK_ON) },
        "SCROLLOCK" to {toggleModifier(KeyEvent.META_SCROLL_LOCK_ON) },
        "CTRLONCE"  to { modifyOnce(KeyEvent.META_CTRL_ON) },
        "ALTONCE"   to { modifyOnce(KeyEvent.META_ALT_ON) },
        "SHIFTONCE" to { modifyOnce(KeyEvent.META_SHIFT_ON) },
        "METAONCE"  to { modifyOnce(KeyEvent.META_META_ON) },
        "CAPSLOCKONCE"  to { modifyOnce(KeyEvent.META_CAPS_LOCK_ON) }, // Why though
        "NUMLOCKONCE"   to { modifyOnce(KeyEvent.META_NUM_LOCK_ON) },
        "SCROLLOCKONCE"  to { modifyOnce(KeyEvent.META_SCROLL_LOCK_ON) },
        // For convenience since these are named strangely
        "BACKSPACE" to { handleUnprintable(KeyEvent.KEYCODE_DEL) }, // Backspace key, really
        "DELETE"    to { handleUnprintable(KeyEvent.KEYCODE_FORWARD_DEL) },
        // Generated from keycodes.txt/convertkeycodes.sh
        "APP_SWITCH"     to { handleUnprintable(KeyEvent.KEYCODE_APP_SWITCH) },
        "ASSIST"     to { handleUnprintable(KeyEvent.KEYCODE_ASSIST) },
        "BACK"     to { handleUnprintable(KeyEvent.KEYCODE_BACK) },
        "BREAK"     to { handleUnprintable(KeyEvent.KEYCODE_BREAK) },
        "BRIGHTNESS_DOWN"     to { handleUnprintable(KeyEvent.KEYCODE_BRIGHTNESS_DOWN) },
        "BRIGHTNESS_UP"     to { handleUnprintable(KeyEvent.KEYCODE_BRIGHTNESS_UP) },
        "CALCULATOR"     to { handleUnprintable(KeyEvent.KEYCODE_CALCULATOR) },
        "CALL"     to { handleUnprintable(KeyEvent.KEYCODE_CALL) },
        "CAMERA"     to { handleUnprintable(KeyEvent.KEYCODE_CAMERA) },
        "CAPTIONS"     to { handleUnprintable(KeyEvent.KEYCODE_CAPTIONS) },
        "CHANNEL_DOWN"     to { handleUnprintable(KeyEvent.KEYCODE_CHANNEL_DOWN) },
        "CHANNEL_UP"     to { handleUnprintable(KeyEvent.KEYCODE_CHANNEL_UP) },
        "CLEAR"     to { handleUnprintable(KeyEvent.KEYCODE_CLEAR) },
        "CONTACTS"     to { handleUnprintable(KeyEvent.KEYCODE_CONTACTS) },
        "COPY"     to { handleUnprintable(KeyEvent.KEYCODE_COPY) },
        "CUT"     to { handleUnprintable(KeyEvent.KEYCODE_CUT) },
        "DEL"     to { handleUnprintable(KeyEvent.KEYCODE_DEL) },
        "DPAD_CENTER"     to { handleUnprintable(KeyEvent.KEYCODE_DPAD_CENTER) },
        "DPAD_DOWN"     to { handleUnprintable(KeyEvent.KEYCODE_DPAD_DOWN) },
        "DPAD_DOWN_LEFT"     to { handleUnprintable(KeyEvent.KEYCODE_DPAD_DOWN_LEFT) },
        "DPAD_DOWN_RIGHT"     to { handleUnprintable(KeyEvent.KEYCODE_DPAD_DOWN_RIGHT) },
        "DPAD_LEFT"     to { handleUnprintable(KeyEvent.KEYCODE_DPAD_LEFT) },
        "DPAD_RIGHT"     to { handleUnprintable(KeyEvent.KEYCODE_DPAD_RIGHT) },
        "DPAD_UP"     to { handleUnprintable(KeyEvent.KEYCODE_DPAD_UP) },
        "DPAD_UP_LEFT"     to { handleUnprintable(KeyEvent.KEYCODE_DPAD_UP_LEFT) },
        "DPAD_UP_RIGHT"     to { handleUnprintable(KeyEvent.KEYCODE_DPAD_UP_RIGHT) },
        "ENDCALL"     to { handleUnprintable(KeyEvent.KEYCODE_ENDCALL) },
        "ENTER"     to { handleUnprintable(KeyEvent.KEYCODE_ENTER) },
        "ENVELOPE"     to { handleUnprintable(KeyEvent.KEYCODE_ENVELOPE) },
        "ESCAPE"     to { handleUnprintable(KeyEvent.KEYCODE_ESCAPE) },
        "EXPLORER"     to { handleUnprintable(KeyEvent.KEYCODE_EXPLORER) },
        "F1"     to { handleUnprintable(KeyEvent.KEYCODE_F1) },
        "F2"     to { handleUnprintable(KeyEvent.KEYCODE_F2) },
        "F3"     to { handleUnprintable(KeyEvent.KEYCODE_F3) },
        "F4"     to { handleUnprintable(KeyEvent.KEYCODE_F4) },
        "F5"     to { handleUnprintable(KeyEvent.KEYCODE_F5) },
        "F6"     to { handleUnprintable(KeyEvent.KEYCODE_F6) },
        "F7"     to { handleUnprintable(KeyEvent.KEYCODE_F7) },
        "F8"     to { handleUnprintable(KeyEvent.KEYCODE_F8) },
        "F9"     to { handleUnprintable(KeyEvent.KEYCODE_F9) },
        "F10"     to { handleUnprintable(KeyEvent.KEYCODE_F10) },
        "F11"     to { handleUnprintable(KeyEvent.KEYCODE_F11) },
        "F12"     to { handleUnprintable(KeyEvent.KEYCODE_F12) },
        "FOCUS"     to { handleUnprintable(KeyEvent.KEYCODE_FOCUS) },
        "FORWARD"     to { handleUnprintable(KeyEvent.KEYCODE_FORWARD) },
        "FORWARD_DEL"     to { handleUnprintable(KeyEvent.KEYCODE_FORWARD_DEL) },
        "FUNCTION"     to { handleUnprintable(KeyEvent.KEYCODE_FUNCTION) },
        "GUIDE"     to { handleUnprintable(KeyEvent.KEYCODE_GUIDE) },
        "HELP"     to { handleUnprintable(KeyEvent.KEYCODE_HELP) },
        "HOME"     to { handleUnprintable(KeyEvent.KEYCODE_HOME) },
        "INFO"     to { handleUnprintable(KeyEvent.KEYCODE_INFO) },
        "INSERT"     to { handleUnprintable(KeyEvent.KEYCODE_INSERT) },
        "LANGUAGE_SWITCH"     to { handleUnprintable(KeyEvent.KEYCODE_LANGUAGE_SWITCH) },
        "LAST_CHANNEL"     to { handleUnprintable(KeyEvent.KEYCODE_LAST_CHANNEL) },
        "MEDIA_FAST_FORWARD"     to { handleUnprintable(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) },
        "MEDIA_NEXT"     to { handleUnprintable(KeyEvent.KEYCODE_MEDIA_NEXT) },
        "MEDIA_PAUSE"     to { handleUnprintable(KeyEvent.KEYCODE_MEDIA_PAUSE) },
        "MEDIA_PLAY"     to { handleUnprintable(KeyEvent.KEYCODE_MEDIA_PLAY) },
        "MEDIA_PLAY_PAUSE"     to { handleUnprintable(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
        "MEDIA_PREVIOUS"     to { handleUnprintable(KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
        "MEDIA_RECORD"     to { handleUnprintable(KeyEvent.KEYCODE_MEDIA_RECORD) },
        "MEDIA_REWIND"     to { handleUnprintable(KeyEvent.KEYCODE_MEDIA_REWIND) },
        "MEDIA_SKIP_BACKWARD"     to { handleUnprintable(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD) },
        "MEDIA_SKIP_FORWARD"     to { handleUnprintable(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD) },
        "MEDIA_STEP_BACKWARD"     to { handleUnprintable(KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD) },
        "MEDIA_STEP_FORWARD"     to { handleUnprintable(KeyEvent.KEYCODE_MEDIA_STEP_FORWARD) },
        "MEDIA_STOP"     to { handleUnprintable(KeyEvent.KEYCODE_MEDIA_STOP) },
        "MEDIA_TOP_MENU"     to { handleUnprintable(KeyEvent.KEYCODE_MEDIA_TOP_MENU) },
        "MENU"     to { handleUnprintable(KeyEvent.KEYCODE_MENU) },
        "MOVE_END"     to { handleUnprintable(KeyEvent.KEYCODE_MOVE_END) },
        "MOVE_HOME"     to { handleUnprintable(KeyEvent.KEYCODE_MOVE_HOME) },
        "MUTE"     to { handleUnprintable(KeyEvent.KEYCODE_MUTE) },
        "NAVIGATE_IN"     to { handleUnprintable(KeyEvent.KEYCODE_NAVIGATE_IN) },
        "NAVIGATE_NEXT"     to { handleUnprintable(KeyEvent.KEYCODE_NAVIGATE_NEXT) },
        "NAVIGATE_OUT"     to { handleUnprintable(KeyEvent.KEYCODE_NAVIGATE_OUT) },
        "NAVIGATE_PREVIOUS"     to { handleUnprintable(KeyEvent.KEYCODE_NAVIGATE_PREVIOUS) },
        "NOTIFICATION"     to { handleUnprintable(KeyEvent.KEYCODE_NOTIFICATION) },
        "PAGE_DOWN"     to { handleUnprintable(KeyEvent.KEYCODE_PAGE_DOWN) },
        "PAGE_UP"     to { handleUnprintable(KeyEvent.KEYCODE_PAGE_UP) },
        "PAIRING"     to { handleUnprintable(KeyEvent.KEYCODE_PAIRING) },
        "PASTE"     to { handleUnprintable(KeyEvent.KEYCODE_PASTE) },
        "POWER"     to { handleUnprintable(KeyEvent.KEYCODE_POWER) },
        "PROG_BLUE"     to { handleUnprintable(KeyEvent.KEYCODE_PROG_BLUE) },
        "PROG_GREEN"     to { handleUnprintable(KeyEvent.KEYCODE_PROG_GREEN) },
        "PROG_RED"     to { handleUnprintable(KeyEvent.KEYCODE_PROG_RED) },
        "PROG_YELLOW"     to { handleUnprintable(KeyEvent.KEYCODE_PROG_YELLOW) },
        "SCROLL_LOCK"     to { handleUnprintable(KeyEvent.KEYCODE_SCROLL_LOCK) },
        "SEARCH"     to { handleUnprintable(KeyEvent.KEYCODE_SEARCH) },
        "SETTINGS"     to { handleUnprintable(KeyEvent.KEYCODE_SETTINGS) },
        "SLEEP"     to { handleUnprintable(KeyEvent.KEYCODE_SLEEP) },
        "STEM_1"     to { handleUnprintable(KeyEvent.KEYCODE_STEM_1) },
        "STEM_2"     to { handleUnprintable(KeyEvent.KEYCODE_STEM_2) },
        "STEM_3"     to { handleUnprintable(KeyEvent.KEYCODE_STEM_3) },
        "STEM_PRIMARY"     to { handleUnprintable(KeyEvent.KEYCODE_STEM_PRIMARY) },
        "SYSRQ"     to { handleUnprintable(KeyEvent.KEYCODE_SYSRQ) },
        "SYSTEM_NAVIGATION_DOWN"     to { handleUnprintable(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN) },
        "SYSTEM_NAVIGATION_LEFT"     to { handleUnprintable(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT) },
        "SYSTEM_NAVIGATION_RIGHT"     to { handleUnprintable(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT) },
        "SYSTEM_NAVIGATION_UP"     to { handleUnprintable(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP) },
        "TAB"     to { handleUnprintable(KeyEvent.KEYCODE_TAB) },
        "VOICE_ASSIST"     to { handleUnprintable(KeyEvent.KEYCODE_VOICE_ASSIST) },
        "VOLUME_DOWN"     to { handleUnprintable(KeyEvent.KEYCODE_VOLUME_DOWN) },
        "VOLUME_MUTE"     to { handleUnprintable(KeyEvent.KEYCODE_VOLUME_MUTE) },
        "VOLUME_UP"     to { handleUnprintable(KeyEvent.KEYCODE_VOLUME_UP) },
        "WAKEUP"     to { handleUnprintable(KeyEvent.KEYCODE_WAKEUP) },
        "WINDOW"     to { handleUnprintable(KeyEvent.KEYCODE_WINDOW) },
        "ZOOM_IN"     to { handleUnprintable(KeyEvent.KEYCODE_ZOOM_IN) },
        "ZOOM_OUT"     to { handleUnprintable(KeyEvent.KEYCODE_ZOOM_OUT) },
        "BUTTON_1"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_1) },
        "BUTTON_2"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_2) },
        "BUTTON_3"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_3) },
        "BUTTON_4"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_4) },
        "BUTTON_5"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_5) },
        "BUTTON_6"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_6) },
        "BUTTON_7"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_7) },
        "BUTTON_8"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_8) },
        "BUTTON_9"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_9) },
        "BUTTON_10"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_10) },
        "BUTTON_11"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_11) },
        "BUTTON_12"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_12) },
        "BUTTON_13"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_13) },
        "BUTTON_14"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_14) },
        "BUTTON_15"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_15) },
        "BUTTON_16"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_16) },
        "BUTTON_A"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_A) },
        "BUTTON_B"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_B) },
        "BUTTON_C"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_C) },
        "BUTTON_L1"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_L1) },
        "BUTTON_L2"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_L2) },
        "BUTTON_MODE"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_MODE) },
        "BUTTON_R1"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_R1) },
        "BUTTON_R2"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_R2) },
        "BUTTON_SELECT"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_SELECT) },
        "BUTTON_START"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_START) },
        "BUTTON_THUMBL"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_THUMBL) },
        "BUTTON_THUMBR"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_THUMBR) },
        "BUTTON_X"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_X) },
        "BUTTON_Y"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_Y) },
        "BUTTON_Z"     to { handleUnprintable(KeyEvent.KEYCODE_BUTTON_Z) }
    )

    private fun handleUnprintable(kC: Int): Array<KeyEvent> {
        val eventTime = System.currentTimeMillis()
        return arrayOf(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, kC, 0),
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, kC, 0),
        )
    }

    var mapSwitchFlag: Boolean = false
    // TODO: check whether input is even wanted?
    private fun sendKeys(commandList: CommandList) {
        val inputConnection: InputConnection = currentInputConnection ?: return

        var keyEvents: Array<KeyEvent>
        var modOnceFlag = false

       commandList.forEach { mapString ->
            Log.d("foo","mapString: $mapString")

            // Because I didn't think of a better way -- handle a request to
            // change maps
            if(mapSwitchFlag) {
                setCurrentMap(mapString)
                mapSwitchFlag = false
                keyEvents = arrayOf()
            }
            // if the current command is a known command, then we perform it, using actionMap.
            else if (actionMap.containsKey(mapString)) {
                keyEvents = actionMap[mapString]?.invoke() ?: arrayOf()
                Log.d("foo","handled actionMap")
            } else {
                // If the current command is NOT a known command, we treat it as characters to input
                keyEvents = keyCharacterMap.getEvents(mapString.toCharArray()) ?: arrayOf()
            }

            // the "modOnce" set of actions only turn off the mods after there's a character(s) typed.
            // this allows us to say things like "CTRLONCE,a,d".
            if (MetaKeysManager.useOnce and (keyEvents.isNotEmpty())) {
                Log.d("foo","modOnce")
                modOnceFlag = true
            }

            keyEvents.forEach { originalEvent ->
                Log.d("foo","sending KeyEvent, kc: ${originalEvent.keyCode}, meta: ${MetaKeysManager.metaKeys}")
                val modifiedEvent = KeyEvent(
                    originalEvent.downTime,
                    originalEvent.eventTime,
                    originalEvent.action,
                    originalEvent.keyCode,
                    originalEvent.repeatCount,
                    originalEvent.metaState or MetaKeysManager.metaKeys
                )
                inputConnection.sendKeyEvent(modifiedEvent)
            }

            if (modOnceFlag)
                MetaKeysManager.resetMetaKeys()
        }

    }

    private var multiTapTimeout: Int = 300 // default value, replaced in onCreate
    private var lastInput: Int? = null
    private var repeatCount: Int = 0

    private var loopMultiTaps: Boolean = true

    private val executorService = Executors.newSingleThreadScheduledExecutor()
    private var scheduledFuture: ScheduledFuture<*>? = null

    // When we get a tap, we start a timer.  If the timer expires, we then input the tapped character.
    // If another tap comes before the timeout, we check to see if it's a double-tap.  If so, we input the
    // double-tapped character.  If not, we input the previous tap, and re-start the timer for this tap.

    // NOTE:  I've updated this to generically handle any number of repeated taps as different inputs,
    // but I'm not sure whether > 3 is useful.

    // TODO: A twisty maze of passages, all alike...
    override fun onTapInputReceived(tapIdentifier: String, data: Int, repeatData: Int) {
        synchronized(this) { // Otherwise there's a race condition between the timer and the new input.
            Log.d("foo","Got Tap")
            val tapPattern: TapPattern = data
            tapInputViewModel.updateTapPattern(tapPattern)

            val commandLists = getCommandListsForTapPattern(tapPattern)
            tapInputViewModel.setCommandLists(commandLists)


            // IF THIS IS THE SAME AS THE LAST AND THE MULTITAP TIMER ISN'T EXPIRED...
            if (lastInput != null && scheduledFuture?.isCancelled == false && lastInput == tapPattern) {
                repeatCount += 1
                tapInputViewModel.setTapCount(repeatCount)

                if (!loopMultiTaps and (repeatCount >= commandLists.size)) {
                    cancelScheduledTask()
                    Log.d("foo","sending because commandList at max and no loops allowed. $repeatCount")
                    sendKeys(getCommandList(commandLists, repeatCount))
                    lastInput = null
                    repeatCount = 0
                }
                else {
                    Log.d("foo","not sending because multi-tap rc: $repeatCount")
                    resetScheduledTask(tapPattern, commandLists, repeatCount)
                }

            // THIS IS A NEW TAPPATTERN!
            } else {
                // Send last tap if it exists, then reset the counters for this tap.
                cancelScheduledTask()
                lastInput?.let {
                    Log.d("foo","sending because tap differs from last rc: $repeatCount")
                    sendKeys(getCommandList(it, repeatCount))
                }
                lastInput = null
                repeatCount = 1
                tapInputViewModel.setTapCount(1)
                Log.d("foo","New tap for $tapPattern and we have ${commandLists.size} tap options containing '$commandLists' ")

                if (commandLists.size == 0)
                    Log.d("foo", "Invalid tapPattern: $tapPattern")
                else {
                    if (1 >= commandLists.size) {
                        Log.d("foo","Sending tap immediately because there's no multi defined for $tapPattern")
                        sendKeys(getCommandList(commandLists, 1))
                    }
                    else // Start a timer for this tap so we can handle multiple taps.
                        resetScheduledTask(tapPattern, commandLists, 1)
                }
            }
        }
    }

    private fun resetScheduledTask(tapPattern: TapPattern, commandLists: List<CommandList>, repeatCount: TapCount = 1) {
        synchronized(this) {
            cancelScheduledTask()
            lastInput = tapPattern
            scheduledFuture = executorService.schedule({
                synchronized(this) {
                    sendKeys(getCommandList(commandLists, repeatCount))
                    lastInput = null
                    Log.d("foo", "sending because timed out rc: $repeatCount")
                }
            }, multiTapTimeout.toLong(), TimeUnit.MILLISECONDS)
        }
    }

    private fun cancelScheduledTask() {
        synchronized(this) {
            scheduledFuture?.cancel(true)
            //if (scheduledFuture?.isCancelled == false)
                //if (! (scheduledFuture?.cancel(true) ?: true))
                    // Log.d("foo","unable to cancel.")
        }
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

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        tapInputViewModel = TapInputViewModel(sharedPreferences)

        // Performs all our app-level setup, mainly reading in the csv maps.
        initializeMaps(applicationContext)

        // Settings
        multiTapTimeout = sharedPreferences.getInt("multi_tap_timeout", multiTapTimeout)
        loopMultiTaps   = sharedPreferences.getBoolean("loop_multi_taps", loopMultiTaps)

        // TapStrap SDK
        sdk = TapSdkFactory.getDefault(this)

        sdk.setDefaultMode(TapInputMode.controller(), true)
        //sdk.disablePauseResumeHandling()  // Might disable the Tap's ability to be used for HID?
        sdk.registerTapListener(this)
        sdk.enablePauseResumeHandling()
        sdk.clearCacheOnTapDisconnection(true)

        ////// This callback is to allow for the user to select a CommandList from the UI and execute it
        // rather than tapping to it as God intended.

        val commandListClickCallback: (CommandList) -> Unit = { commandList ->
            cancelScheduledTask()
            sendKeys(commandList)
            repeatCount = 0
            lastInput = null
            Log.d("foo", "Clicked and executed commandList.")
        }

        tapInputViewModel.setOnCommandListClickCallback(commandListClickCallback)

        Log.d("foo", "Created.  Timeout is $multiTapTimeout. loopMultiTaps is $loopMultiTaps")
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        sdk.resume()
        Log.d("foo","Tap(s) currently connected: ${sdk.connectedTaps}")
        Log.d("foo", "Start Input.")
    }

    override fun onFinishInput() {
        super.onFinishInput()
        Log.d("foo", "Finish Input.")
        sdk.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        sdk.unregisterTapListener(this)

        Log.d("foo","Destroy.")
        //sdk.close()
    }

    override fun onTapConnected(tapIdentifier: String) {
        super.onTapConnected(tapIdentifier)

        Log.d("foo","Tap Connected: $tapIdentifier")

    }
    override fun onTapDisconnected(tapIdentifier: String) {
        super.onTapDisconnected(tapIdentifier)
        Log.d("foo","Tap Disconnected: $tapIdentifier")
    }
    override fun onTapChangedState(tapIdentifier: String, state: Int) {
        super.onTapChangedState(tapIdentifier, state)
        val TapModes = listOf("TEXT", "CONTROLLER", "CONTROLLER_WITH_MOUSE", "RAW", "CONTROLLER_WITH_HID")
        if (state in TapModes.indices) {
            Log.d("foo", "Tap State Change: $tapIdentifier, $state: ${TapModes[state]}")
        } else {
            Log.d("foo", "Invalid tap state index: $state")
        }

        Log.d("foo", "Tap State Change: $tapIdentifier, $state:${TapModes[state]}")
    }

    override fun onError(tapIdentifier: String, code: Int, description: String) {
        super.onError(tapIdentifier, code, description)
        Log.e("foo", "Tap $tapIdentifier ERROR $code \"$description\"")
    }

    override fun onTapStartConnecting(tapIdentifier: String) {
        super.onTapStartConnecting(tapIdentifier)
        Log.d("foo", "Tap Start Connection: $tapIdentifier")
        sdk.startControllerMode(tapIdentifier)
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
