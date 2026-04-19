package com.nononews.gamebridgenative

import android.content.Context
import android.webkit.JavascriptInterface

class AndroidBridge(private val context: Context, private val hidManager: HidManager) {

    @JavascriptInterface
    fun conectarBluetooth() {
        if (!hidManager.hasPermissions()) {
            // Manejar falta de permisos si es necesario
            return
        }
        hidManager.start()
    }

    @JavascriptInterface
    fun enviarReporte(buttons: Int, leftX: Int, leftY: Int, rightX: Int, rightY: Int) {
        if (!hidManager.isConnected) {
            return
        }
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
