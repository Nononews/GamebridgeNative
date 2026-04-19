package com.nononews.gamebridgenative

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class UdpManager {
    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private val targetPort = 9090
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun connect(ip: String, onConnected: (Boolean, String?) -> Unit) {
        executor.execute {
            try {
                targetAddress = InetAddress.getByName(ip)
                if (socket == null || socket?.isClosed == true) {
                    socket = DatagramSocket()
                }
                // We don't "connect" DatagramSockets strictly, but test resolution
                onConnected(true, null)
            } catch (e: Exception) {
                targetAddress = null
                onConnected(false, e.message)
            }
        }
    }

    fun sendPayload(jsonPayload: String) {
        targetAddress?.let { ip ->
            socket?.let { s ->
                executor.execute {
                    try {
                        val bytes = jsonPayload.toByteArray(Charsets.UTF_8)
                        val packet = DatagramPacket(bytes, bytes.size, ip, targetPort)
                        s.send(packet)
                    } catch (e: Exception) {
                        Log.e("UdpManager", "Error sending UDP packet", e)
                    }
                }
            }
        }
    }

    fun disconnect() {
        socket?.close()
        socket = null
        targetAddress = null
    }
}
