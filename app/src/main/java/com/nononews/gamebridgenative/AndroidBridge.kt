package com.nononews.gamebridgenative

import android.content.Intent
import android.bluetooth.BluetoothAdapter
import android.webkit.JavascriptInterface

class AndroidBridge(private val activity: MainActivity, private val hidManager: HidManager) {

    /** Called by JS when user picks a controller type (ps, xbox, generic, racing) */
    @JavascriptInterface
    fun setConfig(profileType: String) {
        hidManager.setProfile(profileType)
        // Request Bluetooth permissions NOW (after user explicitly chose a controller)
        activity.runOnUiThread {
            activity.requestPermissionsIfNeeded()
        }
    }

    /** Rotate screen: landscape=true for gamepad, false=portrait for menu */
    @JavascriptInterface
    fun setOrientation(landscape: Boolean) {
        activity.runOnUiThread {
            activity.setOrientation(landscape)
        }
    }

    /** Activate Bluetooth if off, then start HID registration */
    @JavascriptInterface
    fun conectarBluetooth() {
        if (!hidManager.hasPermissions()) return
        hidManager.start()
    }

    /** Make device discoverable (300s = 5 min) - called before HID registration
     *  so the PC can find and initiate the pairing */
    @JavascriptInterface
    fun solicitarVisibilidadBluetooth() {
        activity.runOnUiThread {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300) // 5 minutes
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(discoverableIntent)
        }
    }

    /** Start scanning for nearby Bluetooth devices */
    @JavascriptInterface
    fun iniciarEscaneo() {
        hidManager.startDiscovery()
    }

    /** Stop Bluetooth scan */
    @JavascriptInterface
    fun detenerEscaneo() {
        hidManager.stopDiscovery()
    }

    /** Connect to a specific device by MAC address */
    @JavascriptInterface
    fun conectarDispositivo(address: String) {
        hidManager.connectToDevice(address)
    }

    /** Send gamepad report: 16-bit buttons + 4 analog axes (0-255) */
    @JavascriptInterface
    fun enviarReporte(buttons: Int, leftX: Int, leftY: Int, rightX: Int, rightY: Int) {
        if (!hidManager.isConnected) return
        val report = ByteArray(6)
        report[0] = (buttons and 0xFF).toByte()
        report[1] = ((buttons shr 8) and 0xFF).toByte()
        report[2] = leftX.toByte()
        report[3] = leftY.toByte()
        report[4] = rightX.toByte()
        report[5] = rightY.toByte()
        hidManager.sendReport(report)
    }
}
