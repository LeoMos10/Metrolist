package com.metrolist.music.nothing

/** Packet format used by ear-web: little-endian command/length, operation ID and CRC16. */
object NothingProtocol {
    fun crc16(bytes: ByteArray, length: Int = bytes.size): Int {
        var crc = 0xffff
        for (i in 0 until length) {
            crc = crc xor (bytes[i].toInt() and 0xff)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xa001 else crc ushr 1 }
        }
        return crc
    }

    fun encode(command: Int, payload: ByteArray = byteArrayOf(), operation: Int): ByteArray {
        require(command in 0..0xffff && payload.size <= 1024 && operation in 1..255)
        val bytes = ByteArray(10 + payload.size)
        bytes[0] = 0x55
        bytes[1] = 0x60
        bytes[2] = 1
        bytes[3] = command.toByte()
        bytes[4] = (command ushr 8).toByte()
        bytes[5] = payload.size.toByte()
        bytes[6] = (payload.size ushr 8).toByte()
        bytes[7] = operation.toByte()
        payload.copyInto(bytes, 8)
        val crc = crc16(bytes, bytes.size - 2)
        bytes[bytes.size - 2] = crc.toByte()
        bytes[bytes.size - 1] = (crc ushr 8).toByte()
        return bytes
    }

    class Decoder {
        private var pending = byteArrayOf()

        fun accept(chunk: ByteArray): List<ByteArray> {
            pending += chunk
            val packets = mutableListOf<ByteArray>()
            while (pending.size >= 8) {
                if (pending[0] != 0x55.toByte() || pending[1] != 0x60.toByte() || pending[2] != 1.toByte()) {
                    pending = pending.copyOfRange(1, pending.size)
                    continue
                }
                val length = (pending[5].toInt() and 255) or ((pending[6].toInt() and 255) shl 8)
                if (length > 1024) {
                    pending = pending.copyOfRange(1, pending.size)
                    continue
                }
                val size = length + 10
                if (pending.size < size) break
                val packet = pending.copyOf(size)
                val crc = (packet[size - 2].toInt() and 255) or ((packet[size - 1].toInt() and 255) shl 8)
                if (crc16(packet, size - 2) != crc) {
                    pending = pending.copyOfRange(1, pending.size)
                    continue
                }
                packets += packet
                pending = pending.copyOfRange(size, pending.size)
            }
            return packets
        }
    }
}

fun ByteArray.toNothingHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 255) }
