package com.metrolist.music.nothing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NothingConnectionState(
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val log: List<String> = emptyList(),
)

@SuppressLint("MissingPermission")
class NothingBackend(context: Context) {
    private val adapter = context.applicationContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Mutex()
    private val connectionLock = Any()
    private var socket: BluetoothSocket? = null
    private var connection: Job? = null
    private var operation = 0
    private val mutableState = MutableStateFlow(NothingConnectionState())
    val state = mutableState.asStateFlow()

    fun pairedDevices(): List<BluetoothDevice> =
        if (adapter?.isEnabled == true) adapter.bondedDevices.sortedBy { it.name ?: it.address } else emptyList()

    private fun log(message: String) {
        mutableState.update { it.copy(log = (it.log + message).takeLast(60)) }
    }

    fun report(message: String) = log(message)

    fun connect(device: BluetoothDevice) {
        disconnect()
        val candidate = try {
            device.createRfcommSocketToServiceRecord(UUID.fromString("aeac4a03-dff5-498f-843a-34487cf133eb"))
        } catch (e: Exception) {
            log("Connection failed: ${e.message}")
            return
        }
        synchronized(connectionLock) { socket = candidate }
        mutableState.update { it.copy(connecting = true, connected = false) }
        log("Connecting: ${device.name ?: device.address}")
        connection = scope.launch {
            val timeout = launch {
                delay(15_000)
                log("Connection timeout")
                runCatching { candidate.close() }
            }
            try {
                candidate.connect()
                timeout.cancel()
                synchronized(connectionLock) {
                    if (socket !== candidate) return@launch
                    mutableState.update { it.copy(connecting = false, connected = true) }
                }
                log("Connected")
                val decoder = NothingProtocol.Decoder()
                val buffer = ByteArray(2048)
                while (true) {
                    val count = candidate.inputStream.read(buffer)
                    if (count < 0) throw IOException("Device disconnected")
                    for (packet in decoder.accept(buffer.copyOf(count))) {
                        val command = (packet[3].toInt() and 255) or ((packet[4].toInt() and 255) shl 8)
                        log("RX cmd=%04X id=%d payload=%s".format(command, packet[7].toInt() and 255, packet.copyOfRange(8, packet.size - 2).toNothingHex()))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(connectionLock) {
                    if (socket === candidate) log("Connection ended: ${e.message}")
                }
            } finally {
                timeout.cancel()
                runCatching { candidate.close() }
                synchronized(connectionLock) {
                    if (socket === candidate) {
                        socket = null
                        mutableState.update { it.copy(connecting = false, connected = false) }
                    }
                }
            }
        }
    }

    fun send(command: Int, payload: ByteArray = byteArrayOf()) {
        val target = synchronized(connectionLock) { socket } ?: return
        if (!state.value.connected) return
        scope.launch {
            writes.withLock {
                if (synchronized(connectionLock) { socket !== target }) return@withLock
                try {
                    operation = operation % 255 + 1
                    val packet = NothingProtocol.encode(command, payload, operation)
                    target.outputStream.write(packet)
                    target.outputStream.flush()
                    log("TX ${packet.toNothingHex()}")
                } catch (e: Exception) {
                    log("Write failed: ${e.message}")
                    runCatching { target.close() }
                }
            }
        }
    }

    fun disconnect() {
        synchronized(connectionLock) {
            val previous = socket
            socket = null
            runCatching { previous?.close() }
            connection?.cancel()
            connection = null
            mutableState.update { it.copy(connecting = false, connected = false) }
        }
    }

    fun close() {
        disconnect()
        scope.cancel()
    }
}
