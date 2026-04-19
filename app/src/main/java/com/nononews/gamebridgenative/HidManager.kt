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
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class HidManager(private val context: Context) {
    private val TAG = "GamepadHID"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    var connectedHost: BluetoothDevice? = null
        private set

    val isConnected: Boolean
        get() = connectedHost != null && hidDevice != null

    private val HID_DESCRIPTOR = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),       // USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x05.toByte(),       // USAGE (Gamepad)
        0xa1.toByte(), 0x01.toByte(),       // COLLECTION (Application)
        0x85.toByte(), 0x01.toByte(),       //   REPORT_ID (1)
        0x05.toByte(), 0x09.toByte(),       //   USAGE_PAGE (Button)
        0x19.toByte(), 0x01.toByte(),       //   USAGE_MINIMUM (Button 1)
        0x29.toByte(), 0x10.toByte(),       //   USAGE_MAXIMUM (Button 16)
        0x15.toByte(), 0x00.toByte(),       //   LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x01.toByte(),       //   LOGICAL_MAXIMUM (1)
        0x75.toByte(), 0x01.toByte(),       //   REPORT_SIZE (1)
        0x95.toByte(), 0x10.toByte(),       //   REPORT_COUNT (16)
        0x81.toByte(), 0x02.toByte(),       //   INPUT (Data,Var,Abs)
        0x05.toByte(), 0x01.toByte(),       //   USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x30.toByte(),       //   USAGE (X)
        0x09.toByte(), 0x31.toByte(),       //   USAGE (Y)
        0x09.toByte(), 0x32.toByte(),       //   USAGE (Z) -> Right X
        0x09.toByte(), 0x35.toByte(),       //   USAGE (Rz) -> Right Y
        0x15.toByte(), 0x00.toByte(),       //   LOGICAL_MINIMUM (0)
        0x26.toByte(), 0xff.toByte(), 0x00.toByte(), //   LOGICAL_MAXIMUM (255)
        0x75.toByte(), 0x08.toByte(),       //   REPORT_SIZE (8)
        0x95.toByte(), 0x04.toByte(),       //   REPORT_COUNT (4)
        0x81.toByte(), 0x02.toByte(),       //   INPUT (Data,Var,Abs)
        0xc0.toByte()                       // END_COLLECTION
    )

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        // BluetoothAdapter.getDefaultAdapter() is deprecated in newer APIs but standard for this use case if Context isn't BluetoothManager
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "Bluetooth no disponible o desactivado")
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
                "GameBridge",
                "Virtual Gamepad",
                "GameBridgeCorp",
                BluetoothHidDevice.SUBCLASS2_GAMEPAD,
                HID_DESCRIPTOR
            )

            device.registerApp(sdp, null, null, Executors.newSingleThreadExecutor(), object : BluetoothHidDevice.Callback() {
                override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                    Log.i(TAG, "App Status Changed. Registered: $registered")
                }

                override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                    Log.i(TAG, "Connection State Changed: $state")
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        connectedHost = device
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        connectedHost = null
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
}
