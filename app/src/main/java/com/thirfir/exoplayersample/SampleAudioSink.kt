package com.thirfir.exoplayersample

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.FileOutputStream

fun extractAudioSegment(context: Context, mp4Uri: Uri, outputWavPath: String, startMs: Long, durationMs: Long) {
    val extractor = MediaExtractor()
    extractor.setDataSource(context, mp4Uri, null)
    //extractor.setDataSource(context.resources.openRawResourceFd(R.raw.test_ko_long))  // test

    val audioTrackIndex = (0 until extractor.trackCount).find { i ->
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        mime.startsWith("audio/")
    } ?: throw RuntimeException("Unable to find audio track")

    extractor.selectTrack(audioTrackIndex)

    val format = extractor.getTrackFormat(audioTrackIndex)
    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
    val decoder = MediaCodec.createDecoderByType(mime)
    decoder.configure(format, null, null, 0)
    decoder.start()

    extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
    val endTime = startMs + durationMs

    val bufferInfo = MediaCodec.BufferInfo()
    val wavOutputStream = FileOutputStream(outputWavPath)
    writeWavHeader(wavOutputStream, format)

    while (true) {
        if (extractor.sampleTime > endTime * 1000)
            break

        val inputBufferIndex = decoder.dequeueInputBuffer(10000)
        if (inputBufferIndex >= 0) {
            val inputBuffer = decoder.getInputBuffer(inputBufferIndex) ?: continue
            val sampleSize = extractor.readSampleData(inputBuffer, 0)
            if (sampleSize < 0) break  // 파일 끝

            decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
            extractor.advance()
        }

        val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
        if (outputBufferIndex >= 0) {
            val outputBuffer = decoder.getOutputBuffer(outputBufferIndex) ?: continue
            val chunk = ByteArray(bufferInfo.size)
            outputBuffer.get(chunk)
            wavOutputStream.write(chunk)
            decoder.releaseOutputBuffer(outputBufferIndex, false)
        }
    }

    decoder.stop()
    decoder.release()
    extractor.release()
    wavOutputStream.close()
}

private fun writeWavHeader(outputStream: FileOutputStream, format: MediaFormat) {
    val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    val byteRate = sampleRate * channels * 2  // 16-bit PCM

    val header = ByteArray(44)
    header[0] = 'R'.code.toByte()
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()

    // 전체 파일 크기 설정 (임시 값, 나중에 수정해야 함)
    val fileSize = 36
    System.arraycopy(fileSize.toLittleEndian(), 0, header, 4, 4)

    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte()
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    header[16] = 16  // PCM
    header[20] = 1  // Audio format (1 = PCM)
    header[22] = channels.toByte()
    System.arraycopy(sampleRate.toLittleEndian(), 0, header, 24, 4)
    System.arraycopy(byteRate.toLittleEndian(), 0, header, 28, 4)
    header[32] = (channels * 2).toByte()  // Block align
    header[34] = 16  // Bits per sample
    header[36] = 'd'.code.toByte()
    header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()

    outputStream.write(header)
}

private fun Int.toLittleEndian(): ByteArray {
    return byteArrayOf(
        (this and 0xFF).toByte(),
        (this shr 8 and 0xFF).toByte(),
        (this shr 16 and 0xFF).toByte(),
        (this shr 24 and 0xFF).toByte()
    )
}
