/*
 * SPDX-FileCopyrightText: 2026 Django Eijgensteijn
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.format

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OggPageTest {
    /**
     * Build an Ogg page with [body] split into 255 byte segments.
     *
     * The CRC field is left zeroed. Callers that need a valid one go through
     * [OggPage.markEndOfStream].
     */
    private fun page(headerType: Int, sequence: Int, body: UByteArray): UByteArray {
        val segments = ArrayList<UByte>()
        var remaining = body.size

        while (remaining >= 255) {
            segments.add(255u)
            remaining -= 255
        }
        segments.add(remaining.toUByte())

        val header = UByteArray(OggPage.MIN_HEADER_SIZE + segments.size)
        "OggS".forEachIndexed { i, c -> header[i] = c.code.toUByte() }
        header[4] = 0u
        header[5] = headerType.toUByte()
        // Granule position, serial number, and CRC are left as zero.
        for (i in 0 until 4) {
            header[18 + i] = (sequence.shr(8 * i) and 0xff).toUByte()
        }
        header[26] = segments.size.toUByte()
        segments.forEachIndexed { i, s -> header[OggPage.MIN_HEADER_SIZE + i] = s }

        return header + body
    }

    /**
     * Reference CRC-32/MPEG-2 (non-reflected, zero init, no final XOR), computed bit by bit so it
     * shares no code with the table driven implementation under test.
     */
    private fun referenceCrc(buf: UByteArray, offset: Int): UInt {
        var crc = 0u

        for (i in offset until buf.size) {
            crc = crc xor buf[i].toUInt().shl(24)

            repeat(8) {
                crc = if (crc and 0x80000000u != 0u) {
                    crc.shl(1) xor 0x04c11db7u
                } else {
                    crc.shl(1)
                }
            }
        }

        return crc
    }

    private fun crcField(buf: UByteArray, offset: Int): UInt {
        var value = 0u

        for (i in 0 until 4) {
            value = value or buf[offset + 22 + i].toUInt().shl(8 * i)
        }

        return value
    }

    @Test
    fun testFindLastPage() {
        val buf = page(0x02, 0, UByteArray(10)) + page(0x00, 1, UByteArray(20))

        assertEquals(OggPage.MIN_HEADER_SIZE + 1 + 10, OggPage.findLast(buf))
    }

    @Test
    fun testFindLastPageIgnoresMagicInsideBody() {
        // A page whose body happens to contain "OggS" followed by a plausible header.
        val body = UByteArray(40)
        val decoy = page(0x00, 9, UByteArray(4))
        decoy.copyInto(body, 4, 0, minOf(decoy.size, body.size - 4))

        val buf = page(0x02, 0, UByteArray(10)) + page(0x00, 1, body)

        assertEquals(OggPage.MIN_HEADER_SIZE + 1 + 10, OggPage.findLast(buf))
    }

    @Test
    fun testFindLastPageWithTruncatedBuffer() {
        // A buffer that starts in the middle of a page has no page ending at its end.
        val buf = page(0x00, 0, UByteArray(20)).copyOfRange(5, 20)

        assertNull(OggPage.findLast(buf))
    }

    @Test
    fun testFindLastPageRejectsUnknownVersion() {
        val buf = page(0x00, 0, UByteArray(10))
        buf[4] = 1u

        assertNull(OggPage.findLast(buf))
    }

    @Test
    fun testMarkEndOfStreamSetsFlagAndCrc() {
        val buf = page(0x00, 1, ubyteArrayOf(1u, 2u, 3u, 4u, 5u))
        val offset = OggPage.findLast(buf)!!

        assertFalse(OggPage.isEndOfStream(buf, offset))
        assertTrue(OggPage.markEndOfStream(buf, offset))

        assertTrue(OggPage.isEndOfStream(buf, offset))
        assertEquals(0x04u.toUByte(), buf[offset + 5])

        // The stored CRC must match a from-scratch computation over the whole page.
        val expected = run {
            val zeroed = buf.copyOf()
            for (i in 0 until 4) {
                zeroed[offset + 22 + i] = 0u
            }
            referenceCrc(zeroed, offset)
        }

        assertEquals(expected, crcField(buf, offset))
    }

    @Test
    fun testMarkEndOfStreamPreservesEverythingElse() {
        val body = UByteArray(300) { (it and 0xff).toUByte() }
        val buf = page(0x00, 7, body)
        val before = buf.copyOf()
        val offset = OggPage.findLast(buf)!!

        assertTrue(OggPage.markEndOfStream(buf, offset))

        // Only the header type byte and the CRC field may change.
        assertArrayEquals(
            before.copyOfRange(0, offset + 5).asByteArray(),
            buf.copyOfRange(0, offset + 5).asByteArray(),
        )
        assertArrayEquals(
            before.copyOfRange(offset + 6, offset + 22).asByteArray(),
            buf.copyOfRange(offset + 6, offset + 22).asByteArray(),
        )
        assertArrayEquals(
            before.copyOfRange(offset + 26, before.size).asByteArray(),
            buf.copyOfRange(offset + 26, buf.size).asByteArray(),
        )
    }

    @Test
    fun testMarkEndOfStreamIsNoOpWhenAlreadySet() {
        val buf = page(0x04, 1, ubyteArrayOf(1u, 2u, 3u))
        val offset = OggPage.findLast(buf)!!
        val before = buf.copyOf()

        assertFalse(OggPage.markEndOfStream(buf, offset))
        assertArrayEquals(before.asByteArray(), buf.asByteArray())
    }

    @Test
    fun testHeaderSizeIncludesSegmentTable() {
        // 300 bytes of body needs two segment table entries: 255 and 45.
        val buf = page(0x00, 1, UByteArray(300))
        val offset = OggPage.findLast(buf)!!

        assertEquals(OggPage.MIN_HEADER_SIZE + 2, OggPage.headerSize(buf, offset))
    }

    @Test
    fun testMaxSizeMatchesOggLimit() {
        assertEquals(65307, OggPage.MAX_SIZE)
    }
}
