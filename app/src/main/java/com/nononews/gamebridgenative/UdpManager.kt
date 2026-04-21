package com.nononews.gamebridgenative

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpManager {
    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private val targetPort = 9090
    
    @Volatile private var isRunning = false
    @Volatile private var latestPayload: String? = null
    private var workerThread: Thread? = null

    fun connect(ip: String, onConnected: (Boolean, String?) -> Unit) {
        Thread {
            try {
                targetAddress = InetAddress.getByName(ip)
                if (socket == null || socket?.isClosed == true) {
                    socket = DatagramSocket()
                }
                startWorkerThread()
                onConnected(true, null)
            } catch (e: Exception) {
                targetAddress = null
                onConnected(false, e.toString())
            }
        }.start()
    }

    private fun startWorkerThread() {
        if (isRunning) return
        isRunning = true
        workerThread = Thread {
            var lastSent: String? = null
            while (isRunning) {
                val current = latestPayload
                if (current != null && current != lastSent) {
                    try {
                        val ip = targetAddress
                        val s = socket
                        if (ip != null && s != null) {
                            val bytes = current.toByteArray(Charsets.UTF_8)
                            val packet = DatagramPacket(bytes, bytes.size, ip, targetPort)
                            s.send(packet)
                            lastSent = current
                        }
                    } catch (e: Exception) {
                        Log.e("UdpManager", "Error sending UDP packet", e)
                    }
                }
                // Previene consumo 100% CPU si no hay datos nuevos
                try { Thread.sleep(2) } catch (_: Exception) {}
            }
        }
        workerThread?.start()
    }

    fun sendPayload(jsonPayload: String) {
        // En vez de encolar, simplemente sobreescribimos el estado más reciente.
        // El thread de red garantizado enviará este y dropeará las colas viejas.
        latestPayload = jsonPayload
    }

    fun disconnect() {
        isRunning = false
        workerThread?.interrupt()
        workerThread = null
        socket?.close()
        socket = null
        targetAddress = null
    }
}
