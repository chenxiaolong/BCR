package com.chiller3.bcr.codec

import android.media.AudioFormat
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.FileDescriptor

class OpusCodec : Codec() {
    override val codecParamType: CodecParamType = CodecParamType.Bitrate
    override val codecParamRange: UIntRange = 6_000u..510_000u
    // "Essentially transparent mono or stereo speech, reasonable music"
    // https://wiki.hydrogenaud.io/index.php?title=Opus
    override val codecParamDefault: UInt = 48_000u
    // https://datatracker.ietf.org/doc/html/rfc7845#section-9
    override val mimeTypeContainer: String = "audio/ogg"
    override var mimeTypeAudio: String = MediaFormat.MIMETYPE_AUDIO_OPUS
    override val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    override fun getMediaFormat(audioFormat: AudioFormat, sampleRate: Int): MediaFormat =
        super.getMediaFormat(audioFormat, sampleRate).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, codecParamValue.toInt() * audioFormat.channelCount)
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun getContainer(fd: FileDescriptor): Container =
        MediaMuxerContainer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
}