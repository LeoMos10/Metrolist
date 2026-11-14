package com.metrolist.music.nothing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NothingProtocolTest {
    @Test
    fun crcMatchesStandardModbusVector() {
        assertEquals(0x4b37, NothingProtocol.crc16("123456789".toByteArray()))
    }

    @Test
    fun batteryRequestMatchesEarWeb() {
        assertArrayEquals(
            byteArrayOf(0x55, 0x60, 1, 7, 0xc0.toByte(), 0, 0, 1, 0xac.toByte(), 0xdf.toByte()),
            NothingProtocol.encode(0xc007, operation = 1),
        )
    }

    @Test
    fun handlesFragmentationMultiplePacketsAndNoise() {
        val decoder = NothingProtocol.Decoder()
        val first = NothingProtocol.encode(0x4007, byteArrayOf(1, 2, 80), 1)
        val second = NothingProtocol.encode(0x4042, "1.2.3".toByteArray(), 2)
        assertTrue(decoder.accept(byteArrayOf(0, 42) + first.copyOfRange(0, 5)).isEmpty())
        val result = decoder.accept(first.copyOfRange(5, first.size) + second)
        assertEquals(2, result.size)
        assertArrayEquals(first, result[0])
        assertArrayEquals(second, result[1])
    }

    @Test
    fun recoversAfterInvalidCrcAndOversizedHeader() {
        val packet = NothingProtocol.encode(0x4007, operation = 1)
        val corrupt = packet.copyOf().also { it[it.lastIndex] = 0 }
        val oversized = byteArrayOf(0x55, 0x60, 1, 0, 0, 0xff.toByte(), 0x7f, 1)
        val result = NothingProtocol.Decoder().accept(oversized + corrupt + packet)
        assertEquals(1, result.size)
        assertArrayEquals(packet, result.single())
    }
}
