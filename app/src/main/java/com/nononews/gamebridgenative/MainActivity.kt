package com.nononews.gamebridgenative

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView
    lateinit var hidManager: HidManager

    companion object {
        const val REQUEST_PERMISSIONS = 100
        const val REQUEST_ENABLE_BT = 101
    }

    // El vigilante para los 120 segundos
    private val discoverabilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_SCAN_MODE_CHANGED) {
                val mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR)
                // Si el modo descubrible termina y seguimos desconectados
                if (mode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                    if (!hidManager.isConnected) {
                        Log.i("GamepadHID", "Visibilidad agotada. No hubo conexión.")
                        runOnUiThread {
                            webView.evaluateJavascript("if(window.onBluetoothFailure) window.onBluetoothFailure();", null)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        webView = WebView(this)
        setContentView(webView)
        hideSystemUI()

        hidManager = HidManager(this, webView)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        webView.addJavascriptInterface(AndroidBridge(this, hidManager), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")
        
        // Manejo del gesto "Atras" nativo de Android
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("""
                    (function() {
                        if(window.onNativeBackPressed) return window.onNativeBackPressed();
                        return "CLOSE_APP";
                    })();
                """) { result ->
                    if (result == "\"CLOSE_APP\"") {
                        finish()
                    }
                }
            }
        })
        
        // Registrar el escuchador de visibilidad
        val filter = IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        registerReceiver(discoverabilityReceiver, filter)
    }

    fun setOrientation(landscape: Boolean) {
        requestedOrientation = if (landscape)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perms = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN)
            val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
            } else {
                startDiscoverableMode()
            }
        } else {
            val perms = arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
            val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
            } else {
                startDiscoverableMode()
            }
        }
    }

    private fun startDiscoverableMode() {
        runOnUiThread {
            // Activar Perfil Bluetooth para estar listos a recibir conexión
            hidManager.start()
            
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(discoverableIntent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                startDiscoverableMode()
            } else {
                 runOnUiThread {
                    webView.evaluateJavascript("if(window.onBluetoothError) window.onBluetoothError('PERMISOS_DENEGADOS');", null)
                }
            }
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hidManager.cleanup()
        try { unregisterReceiver(discoverabilityReceiver) } catch (e: Exception) {}
    }
}
