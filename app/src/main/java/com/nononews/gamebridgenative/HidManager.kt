package com.nononews.gamebridgenative

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.webkit.WebView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class HidManager(private val context: Context, private val webView: WebView) {
    private val TAG = "GamepadHID"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    var connectedHost: BluetoothDevice? = null
        private set

    var currentProfile: String = "xbox"
    private var deviceName: String = "Xbox Wireless Controller"

    val isConnected: Boolean
        get() = connectedHost != null && hidDevice != null

    private val HID_DESCRIPTOR = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),
        0x09.toByte(), 0x05.toByte(),
        0xa1.toByte(), 0x01.toByte(),
        0x85.toByte(), 0x01.toByte(),
        0x05.toByte(), 0x09.toByte(),
        0x19.toByte(), 0x01.toByte(),
        0x29.toByte(), 0x10.toByte(),
        0x15.toByte(), 0x00.toByte(),
        0x25.toByte(), 0x01.toByte(),
        0x75.toByte(), 0x01.toByte(),
        0x95.toByte(), 0x10.toByte(),
        0x81.toByte(), 0x02.toByte(),
        0x05.toByte(), 0x01.toByte(),
        0x09.toByte(), 0x30.toByte(),
        0x09.toByte(), 0x31.toByte(),
        0x09.toByte(), 0x32.toByte(),
        0x09.toByte(), 0x35.toByte(),
        0x15.toByte(), 0x00.toByte(),
        0x26.toByte(), 0xff.toByte(), 0x00.toByte(),
        0x75.toByte(), 0x08.toByte(),
        0x95.toByte(), 0x04.toByte(),
        0x81.toByte(), 0x02.toByte(),
        0xc0.toByte()
    )

    fun setProfile(type: String) {
        currentProfile = type
        deviceName = when (type) {
            "ps"      -> "Wireless Controller"
            "xbox"    -> "Xbox Wireless Controller"
            "generic" -> "GameBridge Controller"
            "racing"  -> "GameBridge Wheel"
            else      -> "GameBridge Controller"
        }
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            @SuppressLint("MissingPermission")
            if (adapter != null) {
                adapter.name = deviceName
                Log.i(TAG, "BT adapter renamed to: \$deviceName")
            }
        } catch (e: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun start() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            notifyJS("window.onBluetoothError && window.onBluetoothError('BT_DISABLED')")
            return
        }

        // Si ya está escuchando, no volver a iniciar
        if (hidDevice != null) {
            registerApp()
            return
        }

        bluetoothAdapter!!.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    registerApp()
                }
            }
            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = null
                }
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        hidDevice?.let { device ->
            val sdp = BluetoothHidDeviceAppSdpSettings(
                deviceName,
                "Virtual Gamepad",
                "GameBridge",
                BluetoothHidDevice.SUBCLASS2_GAMEPAD,
                HID_DESCRIPTOR
            )

            device.registerApp(sdp, null, null, Executors.newSingleThreadExecutor(), object : BluetoothHidDevice.Callback() {
                override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                    Log.i(TAG, "App Status Changed. Registered: \$registered")
                    if (registered) {
                        notifyJS("window.onHidRegistered && window.onHidRegistered()")
                        // NOTA CLAVE: Ya NO forzamos una conexión saliente (hidDevice.connect).
                        // Ahora esperamos pacientemente como un Periférico a que el PC se conecte a nosotros.
                    }
                }

                override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                    Log.i(TAG, "Connection State Changed: \$state for \${device.name}")
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            connectedHost = device
                            val name = try { device.name ?: "PC" } catch (_: Exception) { "PC" }
                            notifyJS("window.onBluetoothConnected && window.onBluetoothConnected(\${escapeJS(name)})")
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            connectedHost = null
                            notifyJS("window.onDeviceDisconnected && window.onDeviceDisconnected()")
                        }
                    }
                }
            })
        }
    }

    @SuppressLint("MissingPermission")
    fun sendReport(report: ByteArray) {
        if (isConnected) {
            hidDevice?.sendReport(connectedHost, 1, report)
        }
    }

    @SuppressLint("MissingPermission")
    fun cleanup() {
        hidDevice?.let {
            it.unregisterApp()
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it)
        }
        hidDevice = null
        connectedHost = null
    }

    private fun notifyJS(jsCode: String) {
        webView.post { webView.evaluateJavascript(jsCode, null) }
    }

    private fun escapeJS(s: String): String = "'\${s.replace("'", "\\'")}'"
}
