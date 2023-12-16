/* This exists solely to allow us to implement some of the SDK API without implementing all of it. */

package com.scribblej.tapstrapapp

import com.tapwithus.sdk.TapListener
import com.tapwithus.sdk.airmouse.AirMousePacket
import com.tapwithus.sdk.mode.RawSensorData
import com.tapwithus.sdk.mouse.MousePacket

interface TapAdapter : TapListener {
    override fun onBluetoothTurnedOn() {
        // default empty implementation
    }

    override fun onBluetoothTurnedOff() {
        // default empty implementation
    }

    override fun onTapStartConnecting(tapIdentifier: String) {
        // default empty implementation
    }

    override fun onTapConnected(tapIdentifier: String) {
        // default empty implementation
    }

    override fun onTapDisconnected(tapIdentifier: String) {
        // default empty implementation
    }

    override fun onTapResumed(tapIdentifier: String) {
        // default empty implementation
    }

    override fun onTapChanged(tapIdentifier: String) {
        // default empty implementation
    }

    override fun onTapInputReceived(tapIdentifier: String, data: Int, repeatData: Int) {
        // default empty implementation
    }

    override fun onTapShiftSwitchReceived(tapIdentifier: String, data: Int) {
        // default empty implementation
    }

    override fun onMouseInputReceived(tapIdentifier: String, data: MousePacket) {
        // default empty implementation
    }

    override fun onAirMouseInputReceived(tapIdentifier: String, data: AirMousePacket) {
        // default empty implementation
    }

    override fun onRawSensorInputReceived(tapIdentifier: String, rsData: RawSensorData) {
        // default empty implementation
    }

    override fun onTapChangedState(tapIdentifier: String, state: Int) {
        // default empty implementation
    }

    override fun onError(tapIdentifier: String, code: Int, description: String) {
        // default empty implementation
    }
}
