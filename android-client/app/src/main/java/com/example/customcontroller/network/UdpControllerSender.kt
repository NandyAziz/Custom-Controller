package com.example.customcontroller.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.UnknownHostException

class UdpControllerSender {

    companion object {
        // Controller input remains on the existing port.
        const val SERVER_PORT = 5555

        // New discovery port.
        private const val DISCOVERY_PORT = 5556

        // Discovery request/response protocol.
        private const val DISCOVERY_REQUEST =
            "CUSTOM_CONTROLLER_DISCOVER"

        private const val DISCOVERY_RESPONSE_PREFIX =
            "CUSTOM_CONTROLLER"

        const val TICK_HZ = 250

        private const val TICK_MS =
            1000L / TICK_HZ

        private const val DISCOVERY_INTERVAL_MS = 1500L
        private const val DISCOVERY_TIMEOUT_MS = 350
        private const val DISCOVERY_RETRY_DELAY_MS = 250L

        private const val PACKET_SIZE = 13
    }

    private val state =
        ControllerState()

    private val packetBuffer =
        ByteArray(PACKET_SIZE)

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private var controllerSocket: DatagramSocket? = null

    @Volatile
    private var discoveredServer: InetSocketAddress? = null

    @Volatile
    private var sequence: Int = 0

    @Volatile
    private var started = false

    fun start() {
        if (started) {
            return
        }

        started = true

        /*
         * Discovery runs independently from controller sending.
         *
         * This means discovery traffic can never block the
         * 250 Hz controller packet loop.
         */
        scope.launch {
            discoveryLoop()
        }

        /*
         * Controller packet loop.
         */
        scope.launch {
            sendLoop()
        }
    }

    /**
     * Continuously searches for a Custom Controller Server.
     *
     * Discovery is deliberately repeated periodically so the app
     * can move between:
     *
     * Wi-Fi
     * USB/RNDIS
     * another Wi-Fi network
     *
     * without requiring a restart.
     */
    private suspend fun discoveryLoop() {
        while (scope.coroutineContext.isActive && started) {
            try {
                discoverServer()
            } catch (_: CancellationException) {
                break
            } catch (_: SecurityException) {
                // Network permission/access issue.
                // Keep the app alive and try again later.
            } catch (_: SocketException) {
                // Network interface may disappear during USB/Wi-Fi changes.
                // Retry instead of crashing the app.
            } catch (_: IOException) {
                // Temporary network failure.
            } catch (_: Exception) {
                // Last-resort protection for unexpected network errors.
            }

            delay(DISCOVERY_INTERVAL_MS)
        }
    }

    /**
     * Sends a discovery broadcast through every usable IPv4 interface.
     *
     * This is important because:
     *
     * Wi-Fi and USB/RNDIS can have different subnets.
     *
     * We therefore DO NOT hard-code:
     *
     * 10.x.x.x
     * 192.168.x.x
     *
     * or any specific PC address.
     */
    private suspend fun discoverServer() {
        withContext(Dispatchers.IO) {
            val interfaces =
                NetworkInterface.getNetworkInterfaces()
                    ?: return@withContext

            val discoverySocket =
                DatagramSocket(null)

            discoverySocket.reuseAddress = true
            discoverySocket.broadcast = true

            try {
                discoverySocket.bind(
                    InetSocketAddress(0)
                )

                val requestBytes =
                    DISCOVERY_REQUEST
                        .toByteArray(Charsets.UTF_8)

                val broadcasts =
                    collectBroadcastAddresses(interfaces)

                if (broadcasts.isEmpty()) {
                    return@withContext
                }

                /*
                 * Send discovery request on every broadcast address.
                 *
                 * Example:
                 *
                 * Wi-Fi:
                 * 192.168.101.255:5556
                 *
                 * USB/RNDIS:
                 * 10.x.x.255:5556
                 */
                for (broadcastAddress in broadcasts) {
                    try {
                        val packet =
                            DatagramPacket(
                                requestBytes,
                                requestBytes.size,
                                broadcastAddress,
                                DISCOVERY_PORT
                            )

                        discoverySocket.send(packet)
                    } catch (_: IOException) {
                        // One interface can disappear while another stays alive.
                        // Ignore this interface and continue.
                    }
                }

                /*
                 * Listen briefly for server responses.
                 *
                 * The Windows server responds directly to the source
                 * address/port of this socket.
                 */
                discoverySocket.soTimeout =
                    DISCOVERY_TIMEOUT_MS

                val receiveBuffer =
                    ByteArray(1024)

                val deadline =
                    System.currentTimeMillis() +
                        DISCOVERY_TIMEOUT_MS

                while (
                    System.currentTimeMillis() < deadline
                ) {
                    try {
                        val responsePacket =
                            DatagramPacket(
                                receiveBuffer,
                                receiveBuffer.size
                            )

                        discoverySocket.receive(
                            responsePacket
                        )

                        val response =
                            String(
                                responsePacket.data,
                                0,
                                responsePacket.length,
                                Charsets.UTF_8
                            ).trim()

                        val parsed =
                            parseDiscoveryResponse(
                                response,
                                responsePacket.address
                            )

                        if (parsed != null) {
                            discoveredServer = parsed

                            /*
                             * First valid response wins.
                             *
                             * On a machine with both Wi-Fi and USB,
                             * the next discovery cycle can still update
                             * the address if the network changes.
                             */
                            return@withContext
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    } catch (_: IOException) {
                        break
                    }
                }
            } finally {
                try {
                    discoverySocket.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Collects broadcast addresses from all suitable IPv4 interfaces.
     *
     * Loopback, disabled interfaces, and non-IPv4 addresses are ignored.
     */
    private fun collectBroadcastAddresses(
        interfaces: java.util.Enumeration<NetworkInterface>
    ): List<InetAddress> {
        val result =
            LinkedHashSet<String>()

        while (interfaces.hasMoreElements()) {
            val networkInterface =
                interfaces.nextElement()

            try {
                if (!networkInterface.isUp) {
                    continue
                }

                if (networkInterface.isLoopback) {
                    continue
                }

                /*
                 * We deliberately do not reject interfaces just because
                 * they are marked as point-to-point. Android/Windows USB
                 * networking implementations can expose networking
                 * differently depending on the device/driver.
                 */
                for (interfaceAddress in
                    networkInterface.interfaceAddresses
                ) {
                    val address =
                        interfaceAddress.address

                    if (address !is Inet4Address) {
                        continue
                    }

                    val broadcast =
                        interfaceAddress.broadcast

                    if (broadcast != null) {
                        result.add(
                            broadcast.hostAddress ?: continue
                        )
                    }
                }
            } catch (_: SocketException) {
                /*
                 * An interface can disappear exactly while USB/Wi-Fi
                 * changes. Ignore that interface.
                 */
            }
        }

        return result.mapNotNull { host ->
            try {
                InetAddress.getByName(host)
            } catch (_: UnknownHostException) {
                null
            }
        }
    }

    /**
     * Parses:
     *
     * CUSTOM_CONTROLLER|1|PC_NAME|5555
     *
     * The response address comes from the UDP sender itself.
     * The hostname is informational.
     */
    private fun parseDiscoveryResponse(
        response: String,
        sourceAddress: InetAddress
    ): InetSocketAddress? {
        if (!response.startsWith(
                "$DISCOVERY_RESPONSE_PREFIX|"
            )
        ) {
            return null
        }

        val parts =
            response.split('|')

        if (parts.size < 4) {
            return null
        }

        if (parts[0] != DISCOVERY_RESPONSE_PREFIX) {
            return null
        }

        /*
         * Protocol version.
         */
        if (parts[1] != "1") {
            return null
        }

        val port =
            parts[3].toIntOrNull()
                ?: return null

        if (port !in 1..65535) {
            return null
        }

        return InetSocketAddress(
            sourceAddress,
            port
        )
    }

    /**
     * Sends controller state at the original 250 Hz rate.
     *
     * No changes are made to the existing 13-byte packet format.
     */
    private suspend fun sendLoop() {
        while (scope.coroutineContext.isActive && started) {
            try {
                val server =
                    discoveredServer

                if (server == null) {
                    delay(25L)
                    continue
                }

                if (
                    controllerSocket == null ||
                    controllerSocket?.isClosed == true
                ) {
                    controllerSocket =
                        DatagramSocket().also {
                            controllerSocket = it
                        }
                }

                val socket =
                    controllerSocket

                if (socket == null || socket.isClosed) {
                    delay(25L)
                    continue
                }

                sendPacket(
                    socket,
                    server
                )

                delay(TICK_MS)
            } catch (_: CancellationException) {
                break
            } catch (_: SecurityException) {
                handleNetworkFailure()
                delay(DISCOVERY_RETRY_DELAY_MS)
            } catch (_: IOException) {
                /*
                 * UDP itself is connectionless, but the socket can still
                 * become invalid when Android changes network transport.
                 *
                 * Drop the socket/address so discovery can rebuild the
                 * connection path.
                 */
                handleNetworkFailure()
                delay(DISCOVERY_RETRY_DELAY_MS)
            } catch (_: Exception) {
                handleNetworkFailure()
                delay(DISCOVERY_RETRY_DELAY_MS)
            }
        }
    }

    private fun handleNetworkFailure() {
        discoveredServer = null

        try {
            controllerSocket?.close()
        } catch (_: Exception) {
        }

        controllerSocket = null
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
    }

    /**
     * Existing 13-byte wire format:
     *
     * byte 0   = sequence
     * byte 1   = digital
     * byte 2   = extra
     * byte 3-4 = left X
     * byte 5-6 = left Y
     * byte 7-8 = right X
     * byte 9-10 = right Y
     * byte 11 = left trigger
     * byte 12 = right trigger
     */
    private fun sendPacket(
        socket: DatagramSocket,
        address: InetSocketAddress
    ) {
        packetBuffer[0] =
            sequence.toByte()

        packetBuffer[1] =
            state.digital.toByte()

        packetBuffer[2] =
            state.extra.toByte()

        writeShortLE(
            packetBuffer,
            3,
            state.leftX
        )

        writeShortLE(
            packetBuffer,
            5,
            state.leftY
        )

        writeShortLE(
            packetBuffer,
            7,
            state.rightX
        )

        writeShortLE(
            packetBuffer,
            9,
            state.rightY
        )

        packetBuffer[11] =
            state.leftTrigger
                .coerceIn(0, 255)
                .toByte()

        packetBuffer[12] =
            state.rightTrigger
                .coerceIn(0, 255)
                .toByte()

        val packet =
            DatagramPacket(
                packetBuffer,
                packetBuffer.size,
                address.address,
                address.port
            )

        socket.send(packet)

        sequence =
            (sequence + 1) and 0xFF
    }

    private fun writeShortLE(
        buffer: ByteArray,
        offset: Int,
        value: Int
    ) {
        val v =
            value.coerceIn(
                -32768,
                32767
            )

        buffer[offset] =
            (v and 0xFF).toByte()

        buffer[offset + 1] =
            ((v shr 8) and 0xFF).toByte()
    }

    fun stop() {
        started = false

        discoveredServer = null

        try {
            controllerSocket?.close()
        } catch (_: Exception) {
        }

        controllerSocket = null

        scope.cancel()
    }
}