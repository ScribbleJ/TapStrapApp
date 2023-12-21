package com.scribblej.tapstrapapp

import android.content.Context
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.tapwithus.sdk.*
import com.tapwithus.sdk.mode.TapInputMode




// I don't think there's a case where it makes sense to have multiple instances?
// Maybe that would be a way to support multiple straps better, IDK
object TapController : TapAdapter {
    private lateinit var sdk: TapSdk

    // If not null, we ignore all taps until we see
    // a sequence of taps that match this list.
    private var unlockInputSequence : SequenceRecognizer? = null
    //
    private var mouseModeActive : Boolean = false
    // This is how we get the mapping from characters to Keycodes.
    private val keyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
    private lateinit var multiTapRecognizer: MultiTapRecognizer

    // Initialize keyEventReceiver as a nullable lambda function
    var keyEventReceiver: ((KeyEvent) -> Unit)? = null
    var tapEventReceiver: ((tapData: TapData) -> Unit)? = null

    fun initialize(appContext: Context, // TODO: Eliminate this, needing it is a sign we did something poorly.
                   tapTimeout: Long,
                   loopMultiTaps: Boolean,
                   keyEventReceiverCallback: ((KeyEvent) -> Unit)? = null,
                   tapEventReceiverCallback: ((tapData: TapData) -> Unit)? = null)
    {
        initializeMaps(appContext)

        keyEventReceiver = keyEventReceiverCallback
        tapEventReceiver = tapEventReceiverCallback

        // TapStrap SDK
        sdk = TapSdkFactory.getDefault(appContext)

        sdk.setDefaultMode(TapInputMode.controller(), true)
        sdk.registerTapListener(this)
        sdk.enablePauseResumeHandling()
        sdk.clearCacheOnTapDisconnection(true)

        multiTapRecognizer = JkTapRecognizer(multiTapTimeout = tapTimeout, loopMultiTaps = loopMultiTaps)
    }

    // Convenient
    private fun debuglog(logEntry: String) {
        Log.d("TapController", logEntry)
    }

    override fun onTapInputReceived(tapIdentifier: String, data: Int, repeatData: Int) {
        super.onTapInputReceived(tapIdentifier, data, repeatData)

        // Check if we're locked
        unlockInputSequence?.let { sequence ->
            if (sequence.isSequenceComplete(data)) {
                unlockInputSequence = null
                if (mouseModeActive) {
                    // TODO: Don't assume this is the reason we're locked.
                    endMouseMode()
                }
            }

            // TODO: Code here for updating viewModel to indicate lock status.
            return
        }

        val tapData = multiTapRecognizer.onTapReceived(data)
        handleRecognizedTap(tapData)
    }

    fun handleRecognizedTap(tapData: TapData) {
        executeCommandList(tapData.executableCommandList)
        tapEventReceiver?.invoke(tapData)
    }

    // Pause all use of sdk, IDK if we need this, or if we'd rather make
    // sure the Tap(s) stay in mode and on our code for the lifetime.
    fun pause() { sdk.pause() }
    fun resume() { sdk.resume() }

    // Ignore all taps until we see this exact sequence.
    fun ignoreInputUntilFromString(endCode: List<String>) {
        ignoreInputUntil(endCode.map { stringToTapPattern(it) })
    }

    fun ignoreInputUntil(endCode: List<Int>) {
        unlockInputSequence = SequenceRecognizer(endCode)
    }

    fun startMouseMode(exitCode: List<String> = listOf("01111")) {
        // We don't track an active TapStrap at this point so we just
        // treat multiples the same, if they exist.
        sdk.connectedTaps.forEach() { sdk.startControllerWithMouseHIDMode(it) }
        mouseModeActive = true
        ignoreInputUntilFromString(exitCode)
    }

    fun endMouseMode() {
        sdk.connectedTaps.forEach() { sdk.startControllerMode(it) }
        mouseModeActive = false;
    }

    // Translate a CommandList into KeyEvents
    fun executeCommandList( commandList: CommandList
                                    ) : Boolean {

        var keyEvents: Array<KeyEvent>
        var modOnceFlag = false

        var loopIndex = 0
        while (loopIndex < commandList.size) {
            val mapString = commandList[loopIndex]
            debuglog("mapString: $mapString")

            // Special handling for items that want a parameter
            if (mapString == "MAPSWITCH") { // Load a different mapping
                val param = commandList.getOrNull(loopIndex + 1)
                if(param == null){
                    debuglog("Missing parameter for MAPSWITCH.")
                    return false
                }
                setCurrentMap(param)  // may throw a horrible exception but there's nothing we can do.
                // NOTE: No commands after mapswitch.
                return true
            }

            if (mapString == "STARTMOUSE") { // Begin mouse mode; parameters indicate how to quit.
                // Check if there is at least one parameter after "STARTMOUSE"
                if (loopIndex + 1 >= commandList.size) {
                    debuglog("Missing parameter(s) for STARTMOUSE, defaulting to '01111'.")
                    startMouseMode()
                    return true
                }

                val params = commandList.subList(loopIndex + 1, commandList.size)
                startMouseMode(params) // NOTE: No commands after entering mouse mode
                return true
            }

            // REGULAR COMMANDS:
            // If the current command is a known command, then we perform it, using actionMap.
            if (actionMap.containsKey(mapString)) {
                keyEvents = actionMap[mapString]?.invoke() ?: arrayOf()
                // debuglog("handled actionMap")
            } else { // If the current command is NOT a known command, we treat it as characters to input
                keyEvents = keyCharacterMap.getEvents(mapString.toCharArray()) ?: arrayOf()
            }

            // the "modOnce" set of actions only turn off the mods after there's a character(s) typed.
            // this allows us to say things like "CTRLONCE,a,d".
            if (MetaKeysManager.useOnce and (keyEvents.isNotEmpty())) {
                // debuglog("modOnce")
                modOnceFlag = true
            }

            // We have a list of KeyEvents but we need to modify them to include our metaKeys.
            keyEvents.forEach { originalEvent ->
                val modifiedEvent = KeyEvent(
                    originalEvent.downTime,
                    originalEvent.eventTime,
                    originalEvent.action,
                    originalEvent.keyCode,
                    originalEvent.repeatCount,
                    originalEvent.metaState or MetaKeysManager.metaKeys
                )
                // debuglog("Sending KeyEvent, kc: ${originalEvent.keyCode}, meta: ${MetaKeysManager.metaKeys}")
                // We send the KeyEvents here, probably it's an Android InputConnection consuming them.
                keyEventReceiver?.invoke(modifiedEvent)
            }

            if (modOnceFlag)  // Flag indicates we should turn off our modKeys after sending
                MetaKeysManager.resetMetaKeys()

            loopIndex++
        } // while each command
        return true
    }

    // Turns the indicated metaKey(s) on/off
    private fun toggleModifier(mK: Int) : Array<KeyEvent> {
        MetaKeysManager.toggleMetaKey(mK)
        return arrayOf()
    }

    // Turns the indicated metaKeys on and sets the flag for
    // automatically turning them off after one use.
    private fun modifyOnce(mK: Int) : Array<KeyEvent> {
        MetaKeysManager.activateMetaKeys(mK, true)
        return arrayOf()
    }

    // Create KeyEvents manually for characters we can't just send as type
    private fun handleUnprintable(kC: Int): Array<KeyEvent> {
        val eventTime = System.currentTimeMillis()
        return arrayOf(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, kC, 0),
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, kC, 0),
        )
    }

    // TapAdapter Callbacks:
    override fun onTapConnected(tapIdentifier: String) {
        super.onTapConnected(tapIdentifier)
        //debuglog("Tap Connected: $tapIdentifier")

    }

    override fun onTapDisconnected(tapIdentifier: String) {
        super.onTapDisconnected(tapIdentifier)
        debuglog("Map Disconnected: $tapIdentifier")
    }

    override fun onTapChangedState(tapIdentifier: String, state: Int) {
        super.onTapChangedState(tapIdentifier, state)
        val TapModes = listOf("TEXT", "CONTROLLER", "CONTROLLER_WITH_MOUSE", "RAW", "CONTROLLER_WITH_HID")
        if (!(state in TapModes.indices)) {
            debuglog("Invalid tap state index: $state")
            return
        }

        // debuglog("Tap State Change: $tapIdentifier, $state:${TapModes[state]}")
    }

    override fun onError(tapIdentifier: String, code: Int, description: String) {
        super.onError(tapIdentifier, code, description)
        Log.e("TapController", "Tap $tapIdentifier ERROR $code \"$description\"")
    }


    class SequenceRecognizer(private val sequence: List<Int>) {
        private var currentIndex: Int = 0

        init { debuglog("lock Sequence initialized: $sequence") }

        fun isSequenceComplete(nextVal: TapPattern = 0) : Boolean {
            // If this pattern matches the next in the sequence, good.
            if (currentIndex < sequence.size && sequence[currentIndex] == nextVal)
                currentIndex++
            else // Otherwise, start over.
                currentIndex=0
            debuglog("Checked lock sequence, got $nextVal at $currentIndex for sequence $sequence")
            return currentIndex == sequence.size
        }
    }

    override fun onTapStartConnecting(tapIdentifier: String) {
        super.onTapStartConnecting(tapIdentifier)
        // debuglog("Tap Start Connection: $tapIdentifier")
        // We want to make absolutely sure any Tap we are working with is in
        // Controller mode ALL the time, because we don't want it sending spurious
        // keypresses while we're trying to send our own spurious keypresses.
        sdk.startControllerMode(tapIdentifier)
    }


    // The big list of commands
    private val actionMap: Map<String, () -> Array<KeyEvent>> = mapOf(
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
}