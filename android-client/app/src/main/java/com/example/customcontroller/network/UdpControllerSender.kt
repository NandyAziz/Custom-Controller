package com.example.customcontroller.network

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpControllerSender {
    companion object {
        const val SERVER_IP = "10.216.17.135"
        const val SERVER_PORT = 5555
        const val TICK_HZ = 250
        private const val TICK_MS = 1000L / TICK_HZ
        private const val RETRY_DELAY_MS = 500L
        private const val PACKET_SIZE = 13
    }

    private val state = ControllerState()
    private val packetBuffer = ByteArray(PACKET_SIZE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: DatagramSocket? = null
    private var sequence = 0
    private val wake = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true

        scope.launch {
            while (isActive) {
                try {
                    val address = InetAddress.getByName(SERVER_IP)
                    DatagramSocket().also { socket = it }.use { sock ->
                        while (isActive && !sock.isClosed) {
                            withTimeoutOrNull(TICK_MS) { wake.receive() }
                            sendPacket(sock, address)
                        }
                    }
                } catch (_: CancellationException) {
                    throw
                } catch (_: IOException) {
                    // USB/RNDIS can disappear when the cable or tethering is disconnected.
                    // Keep the app alive and retry after the network becomes available again.
                    socket?.close()
                    socket = null
                    delay(RETRY_DELAY_MS)
                } catch (_: SecurityException) {
                    // Keep the UI alive even if the network is temporarily unavailable.
                    socket?.close()
                    socket = null
                    delay(RETRY_DELAY_MS)
                } catch (_: Exception) {
                    // Last-resort protection against an unexpected network exception.
                    socket?.close()
                    socket = null
                    delay(RETRY_DELAY_MS)
                }
            }
        }
    }

    fun onControllerStateChanged(
        digital: Int,
        extra: Int,
        leftX: Int,
        leftY: Int,
        rightX: Int,
        rightY: Int,
        leftTrigger: Int,
        rightTrigger: Int
    ) {
        state.digital = digital
        state.extra = extra
        state.leftX = leftX
        state.leftY = leftY
        state.rightX = rightX
        state.rightY = rightY
        state.leftTrigger = leftTrigger
        state.rightTrigger = rightTrigger
        wake.trySend(Unit)
    }

    private fun sendPacket(socket: DatagramSocket, address: InetAddress) {
        packetBuffer[0] = sequence.toByte()
        packetBuffer[1] = state.digital.toByte()
        packetBuffer[2] = state.extra.toByte()

        writeShortLE(packetBuffer, 3, state.leftX)
        writeShortLE(packetBuffer, 5, state.leftY)
        writeShortLE(packetBuffer, 7, state.rightX)
        writeShortLE(packetBuffer, 9, state.rightY)

        packetBuffer[11] = state.leftTrigger.coerceIn(0, 255).toByte()
        packetBuffer[12] = state.rightTrigger.coerceIn(0, 255).toByte()

        socket.send(DatagramPacket(packetBuffer, packetBuffer.size, address, SERVER_PORT))
        sequence = (sequence + 1) and 0xFF
    }

    private fun writeShortLE(buffer: ByteArray, offset: Int, value: Int) {
        val v = value.coerceIn(-32768, 32767)
        buffer[offset] = (v and 0xFF).toByte()
        buffer[offset + 1] = ((v shr 8) and 0xFF).toByte()
    }

    fun stop() {
        started = false
        socket?.close()
        socket = null
        scope.cancel()
    }
}
