/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.format

import android.media.MediaRecorder
import com.chiller3.bcr.R

enum class AudioSource {
    VOICE_CALL,
    VOICE_UPLINK_DOWNLINK,
    VOICE_UPLINK,
    VOICE_DOWNLINK;

    val sources: Array<Int>
        get() = when (this) {
            VOICE_CALL -> arrayOf(MediaRecorder.AudioSource.VOICE_CALL)
            VOICE_UPLINK_DOWNLINK -> arrayOf(
                MediaRecorder.AudioSource.VOICE_UPLINK,
                MediaRecorder.AudioSource.VOICE_DOWNLINK,
            )
            VOICE_UPLINK -> arrayOf(MediaRecorder.AudioSource.VOICE_UPLINK)
            VOICE_DOWNLINK -> arrayOf(MediaRecorder.AudioSource.VOICE_DOWNLINK)
        }

    val isStereo: Boolean
        get() = this == VOICE_UPLINK_DOWNLINK

    val nameResId: Int
        get() = when (this) {
            VOICE_CALL -> R.string.audio_source_voice_call
            VOICE_UPLINK_DOWNLINK -> R.string.audio_source_voice_uplink_downlink
            VOICE_UPLINK -> R.string.audio_source_voice_uplink
            VOICE_DOWNLINK -> R.string.audio_source_voice_downlink
        }

    companion object {
        fun getByName(name: String): AudioSource? = AudioSource.entries.find { it.name == name }
    }
}
