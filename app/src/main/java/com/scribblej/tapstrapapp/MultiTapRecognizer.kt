package com.scribblej.tapstrapapp

import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

interface MultiTapRecognizer {
    fun onTapReceived(tapPattern: Int) : TapData

}

val _DEFAULTTIMEOUT: Long = 300

class JkTapRecognizer(private var multiTapTimeout: Long = _DEFAULTTIMEOUT, private var loopMultiTaps: Boolean = false) : MultiTapRecognizer {

    private fun debuglog(logEntry: String) {
        Log.d("JkTapHandler", logEntry)
    }

    fun setLoopMultiTaps(yesOrNo : Boolean = false) {
        loopMultiTaps = yesOrNo
    }
    fun setMultiTapTimeout(milliseconds: Long = _DEFAULTTIMEOUT) {
        multiTapTimeout = milliseconds
    }

    // When we get a tap, we start a timer.  If the timer expires, we then input the tapped character.
    // If another tap comes before the timeout, we check to see if it's a double-tap.  If so, we input the
    // double-tapped character.  If not, we input the previous tap, and re-start the timer for this tap.
    // Same algorithm for > 2 taps.

    private var currentCommandLists : List<CommandList> = listOf()
    private var lastInput : TapPattern = 0
    private var tapCount : Int = 0

    private val executorService = Executors.newSingleThreadScheduledExecutor()
    private var scheduledFuture: ScheduledFuture<*>? = null

    override fun onTapReceived(tapPattern: Int) : TapData {
        var returnData : TapData = TapData()

        synchronized(this) {
            // ASSERT: tapPattern is not 0, that would be a tap of no taps, that's dumb.
            // Note: OMG I just realized there aren't 32 possible taps, there's 31.
            cancelTapTimer()

            returnData.tapCount = tapCount
            returnData.tapPattern = tapPattern
            returnData.metaKeys = MetaKeysManager.metaKeys
            returnData.modOnce = MetaKeysManager.useOnce
            returnData.potentialCommandLists = currentCommandLists

            //////////////////////////////////////////
            // IF THIS IS THE SAME AS THE LAST (AND WE KNOW THE TIMER ISN'T EXPIRED OR LAST WOULD BE 0)
            if (lastInput == tapPattern) {
                tapCount += 1
                returnData.tapCount = tapCount

                // We want to send the final CommandList in the sequence if we aren't looping
                if (!loopMultiTaps && (tapCount >= currentCommandLists.size)) {
                    debuglog("GOT MULTI: Sending because commandList at max and no loops allowed.")
                    returnData.executableCommandList = getCommandList(currentCommandLists, tapCount)
                    lastInput = 0
                    tapCount = 0
                    return@synchronized
                }

                debuglog("GOT MULTI: Setting timer to send later...")
                setTapTimer()
                return@synchronized
            }

            ////////////////////////////////
            // THIS IS A NEW TAPPATTERN!
            // Send last tap if it exists, then reset the counters for this tap.
            if (lastInput != 0) {
                debuglog("NEW TAP: Sending prior because new pattern.")
                val pcl = getCommandListsForTapPattern(lastInput)
                    val lastTapData = TapData(tapPattern = lastInput,
                       potentialCommandLists = pcl,  // Should be unset?
                       executableCommandList = pcl[tapCount-1],
                       tapCount=tapCount,
                       metaKeys = MetaKeysManager.metaKeys,
                       modOnce = MetaKeysManager.useOnce)
                    // TODO: What a mess.
                    TapController.handleRecognizedTap(lastTapData)
            }

            lastInput = tapPattern
            tapCount = 1
            currentCommandLists = getCommandListsForTapPattern(tapPattern)

            returnData.tapCount = tapCount
            returnData.potentialCommandLists = currentCommandLists

            debuglog("NEW TAP: $tapPattern and we have ${currentCommandLists.size} tap options containing '$currentCommandLists' ")

            if (currentCommandLists.size == 0) {  // No options, no way.
                debuglog("NEW TAP: tapPattern not in map: ${tapPatternToString(tapPattern)}")
                return@synchronized
            }

            if (currentCommandLists.size == 1) {  // When there's only one options it is going to get sent regardless of anything else.
                returnData.executableCommandList = currentCommandLists[0]
                // the prior handler above may have changed MetaKeys
                returnData.metaKeys = MetaKeysManager.metaKeys
                returnData.modOnce = MetaKeysManager.useOnce

                lastInput = 0
                tapCount = 0
                debuglog("NEW TAP: Sending tap immediately because there's no multi defined for ${tapPatternToString(tapPattern)}")
                return@synchronized
            }

            debuglog("NEW TAP: Setting timer to send later...")
            // Start a timer for this tap so we can handle multiple taps.
            setTapTimer()
        }

        return returnData

    }

    private fun setTapTimer() {
        synchronized(this) {
            cancelTapTimer() // Belt and Suspenders looks good on me.
            scheduledFuture = executorService.schedule({
                synchronized(this) {
                    if (lastInput == 0) {
                        Log.e("JkTapHandler","In timer, lastInput has been unset.  This should be unpossible.")
                        // Not actually unpossible since sometimes the call to .cancel fails due to
                        // timey-wimey, wibbly-wobbly.
                        // If we're really concerned we should note a difference in tapPattern between
                        // creation and execution of this lambda as well.
                        // Actually, we keep a lifetime tap counter already so if we're REALLY concerned,
                        // that's the fundamental that would fail to match /only/ in race conditions.
                        return@schedule  // guess we aren't concerned.
                    }
                    debuglog("TIMER: Sending because timed out.")
                    val pcl = getCommandListsForTapPattern(lastInput)
                    val tapData = TapData(tapPattern = lastInput,
                       potentialCommandLists = pcl,  // Should be unset?
                       executableCommandList = pcl[tapCount-1],
                       tapCount=tapCount,
                       metaKeys = MetaKeysManager.metaKeys,
                       modOnce = MetaKeysManager.useOnce)
                    // TODO: What a mess.
                    TapController.handleRecognizedTap(tapData)
                    lastInput = 0
                    tapCount = 0
                }
            }, multiTapTimeout, TimeUnit.MILLISECONDS)
        }
    }

    private fun cancelTapTimer() {
        synchronized(this) {
            // returns false if it can't but we can't check it since we
            // call this frequently unnecessarily... plus it doesn't
            // matter because it'll still happen so we have to "handle" it
            // in the functions above.
            scheduledFuture?.cancel(true)
        }
    }
}