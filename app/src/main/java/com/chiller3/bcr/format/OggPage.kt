/*
 * SPDX-FileCopyrightText: 2026 Django Eijgensteijn
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:OptIn(ExperimentalUnsignedTypes::class)

package com.chiller3.bcr.format

import java.nio.ByteBuffer

/**
 * Ogg page structure, as much of it as is needed to terminate a bitstream.
 *
 * Kept free of Android dependencies so it can be unit tested on the JVM.
 */
object OggPage {
    private val MAGIC = ubyteArrayOf(0x4fu, 0x67u, 0x67u, 0x53u) // OggS
    private const val VERSION_OFFSET = 4
    private const val HEADER_TYPE_OFFSET = 5
    private const val CRC_OFFSET = 22
    private const val CRC_SIZE = 4
    private const val SEGMENT_COUNT_OFFSET = 26
    private const val MAX_SEGMENT_COUNT = 255

    const val MIN_HEADER_SIZE = 27

    /** The largest an Ogg page can be: header, full segment table, and maximum body. */
    const val MAX_SIZE = MIN_HEADER_SIZE + MAX_SEGMENT_COUNT + MAX_SEGMENT_COUNT * 255

    private const val HEADER_TYPE_EOS: UByte = 0x04u

    /** Ogg uses a non-reflected CRC-32 with no initial or final XOR value. */
    private const val CRC_POLYNOMIAL = 0x04c11db7u

    private val CRC_TABLE = UIntArray(256) { index ->
        var value = index.toUInt().shl(24)

        repeat(8) {
            value = if (value and 0x80000000u != 0u) {
                value.shl(1) xor CRC_POLYNOMIAL
            } else {
                value.shl(1)
            }
        }

        value
    }

    /**
     * Find the offset of the Ogg page that ends at the end of [buf].
     *
     * The search runs backwards because the buffer may begin in the middle of an earlier page. A
     * candidate is only accepted if its segment table accounts for exactly the remaining bytes,
     * which rules out the magic appearing inside page data.
     */
    fun findLast(buf: UByteArray): Int? {
        for (offset in buf.size - MIN_HEADER_SIZE downTo 0) {
            if (!buf.asByteArray().regionMatches(offset, MAGIC)) {
                continue
            }

            if (buf[offset + VERSION_OFFSET] != 0.toUByte()) {
                continue
            }

            val segmentCount = buf[offset + SEGMENT_COUNT_OFFSET].toInt()
            val headerSize = MIN_HEADER_SIZE + segmentCount
            if (offset + headerSize > buf.size) {
                continue
            }

            var bodySize = 0
            for (i in 0 until segmentCount) {
                bodySize += buf[offset + MIN_HEADER_SIZE + i].toInt()
            }

            if (offset + headerSize + bodySize == buf.size) {
                return offset
            }
        }

        return null
    }

    /** Size of the page header at [offset], including its segment table. */
    fun headerSize(buf: UByteArray, offset: Int): Int =
        MIN_HEADER_SIZE + buf[offset + SEGMENT_COUNT_OFFSET].toInt()

    fun isEndOfStream(buf: UByteArray, offset: Int): Boolean =
        buf[offset + HEADER_TYPE_OFFSET] and HEADER_TYPE_EOS != 0.toUByte()

    /**
     * Set the end-of-stream bit in the header type flags of the page at [offset] and update its
     * CRC checksum. The page must extend to the end of [buf].
     *
     * @return Whether the bit was set. False if the page already had it.
     */
    fun markEndOfStream(buf: UByteArray, offset: Int): Boolean {
        if (isEndOfStream(buf, offset)) {
            return false
        }

        buf[offset + HEADER_TYPE_OFFSET] = buf[offset + HEADER_TYPE_OFFSET] or HEADER_TYPE_EOS

        // The checksum is computed with its own field zeroed out.
        for (i in 0 until CRC_SIZE) {
            buf[offset + CRC_OFFSET + i] = 0u
        }

        val crc = computeCrc(buf, offset, buf.size - offset)
        for (i in 0 until CRC_SIZE) {
            buf[offset + CRC_OFFSET + i] = (crc.shr(8 * i) and 0xffu).toUByte()
        }

        return true
    }

    private fun ByteArray.regionMatches(offset: Int, expected: UByteArray): Boolean =
        ByteBuffer.wrap(this, offset, expected.size) == ByteBuffer.wrap(expected.asByteArray())

    private fun computeCrc(buf: UByteArray, offset: Int, size: Int): UInt {
        var crc = 0u

        for (i in offset until offset + size) {
            val index = (crc.shr(24) and 0xffu) xor buf[i].toUInt()
            crc = crc.shl(8) xor CRC_TABLE[index.toInt()]
        }

        return crc
    }
}
