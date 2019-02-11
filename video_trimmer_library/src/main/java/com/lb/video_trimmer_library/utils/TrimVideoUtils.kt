/*
 * MIT License
 *
 * Copyright (c) 2016 Knowledge, education for life.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.lb.video_trimmer_library.utils

import android.content.Context
import android.media.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.SparseIntArray
import androidx.annotation.NonNull
import androidx.annotation.WorkerThread
import com.googlecode.mp4parser.FileDataSourceViaHeapImpl
import com.googlecode.mp4parser.authoring.Track
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator
import com.googlecode.mp4parser.authoring.tracks.AppendTrack
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack
import com.lb.video_trimmer_library.interfaces.VideoTrimmingListener
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

object TrimVideoUtils {
    private const val DEFAULT_BUFFER_SIZE = 1024 * 1024

    @JvmStatic
    @WorkerThread
    fun startTrim(context: Context, inputVideoUri: Uri, outputTrimmedVideoFile: File, startMs: Long, endMs: Long, durationInMs: Long, callback: VideoTrimmingListener) {
//        Log.d("AppLog", "startTrim")
        outputTrimmedVideoFile.parentFile.mkdirs()
        outputTrimmedVideoFile.delete()
        var succeeded = false
        if (startMs <= 0L && endMs >= durationInMs) {
//            Log.d("AppLog", "trimmed file is the entire video, so just copy it")
            context.contentResolver.openInputStream(inputVideoUri).use {
                it?.copyTo(FileOutputStream(outputTrimmedVideoFile))
                succeeded = it != null && outputTrimmedVideoFile.exists()
            }
//            Log.d("AppLog", "succeeded copying?$succeeded")
        }
        if (!succeeded) {
//            Log.d("AppLog", "trying to trim using mp4parser...")
            try {
                val inputFilePath = FileUtils.getPath(context, inputVideoUri)
                succeeded = genVideoUsingMp4Parser(inputFilePath, outputTrimmedVideoFile, startMs, endMs)
            } catch (e: Exception) {
            }
//            Log.d("AppLog", "succeeded using mp4parser? $succeeded")
        }
        if (!succeeded) {
//            Log.d("AppLog", "trying to trim using Android framework API...")
            succeeded = genVideoUsingMuxer(context, inputVideoUri, outputTrimmedVideoFile.absolutePath, startMs, endMs, true, true)
//            Log.d("AppLog", "succeeded trimming using Android framework API?$succeeded")
        }
        Handler(Looper.getMainLooper()).post {
            //            callback.onFinishedTrimming(if (succeeded) Uri.parse(outputTrimmedVideoFile.toString()) else null)
            callback.onFinishedTrimming(if (succeeded) Uri.parse(outputTrimmedVideoFile.absolutePath) else null)
        }
    }

    @Throws(IOException::class)
    private fun genVideoUsingMp4Parser(filePath: String?, dst: File, startMs: Long, endMs: Long): Boolean {
        if (filePath.isNullOrBlank() || !File(filePath).exists())
            return false
        // NOTE: Switched to using FileDataSourceViaHeapImpl since it does not use memory mapping (VM).
        // Otherwise we get OOM with large movie files.
        val movie = MovieCreator.build(FileDataSourceViaHeapImpl(filePath))
        val tracks = movie.tracks
        movie.tracks = LinkedList()
        // remove all tracks we will create new tracks from the old
        var startTime1 = (startMs / 1000).toDouble()
        var endTime1 = (endMs / 1000).toDouble()
        var timeCorrected = false
        // Here we try to find a track that has sync samples. Since we can only start decoding
        // at such a sample we SHOULD make sure that the start of the new fragment is exactly
        // such a frame
        for (track in tracks) {
            if (track.syncSamples != null && track.syncSamples.isNotEmpty()) {
                if (timeCorrected) {
                    // This exception here could be a false positive in case we have multiple tracks
                    // with sync samples at exactly the same positions. E.g. a single movie containing
                    // multiple qualities of the same video (Microsoft Smooth Streaming file)
//                    throw RuntimeException("The startTime has already been corrected by another track with SyncSample. Not Supported.")
                    return false
                }
                startTime1 = correctTimeToSyncSample(track, startTime1, false)
                endTime1 = correctTimeToSyncSample(track, endTime1, true)
                timeCorrected = true
            }
        }
        for (track in tracks) {
            var currentSample: Long = 0
            var currentTime = 0.0
            var lastTime = -1.0
            var startSample1: Long = -1
            var endSample1: Long = -1
            for (i in 0 until track.sampleDurations.size) {
                val delta = track.sampleDurations[i]
                if (currentTime > lastTime && currentTime <= startTime1) {
                    // current sample is still before the new starttime
                    startSample1 = currentSample
                }
                if (currentTime > lastTime && currentTime <= endTime1) {
                    // current sample is after the new start time and still before the new endtime
                    endSample1 = currentSample
                }
                lastTime = currentTime
                currentTime += delta.toDouble() / track.trackMetaData.timescale.toDouble()
                ++currentSample
            }
            movie.addTrack(AppendTrack(CroppedTrack(track, startSample1, endSample1)))
        }
        dst.parentFile.mkdirs()
        if (!dst.exists()) {
            dst.createNewFile()
        }
        val out = DefaultMp4Builder().build(movie)
        val fos = FileOutputStream(dst)
        val fc = fos.channel
        out.writeContainer(fc)
        fc.close()
        fos.close()
        return true
    }

    //https://stackoverflow.com/a/44653626/878126 https://android.googlesource.com/platform/packages/apps/Gallery2/+/634248d/src/com/android/gallery3d/app/VideoUtils.java
    @JvmStatic
    @WorkerThread
    private fun genVideoUsingMuxer(context: Context, uri: Uri, dstPath: String, startMs: Long, endMs: Long, useAudio: Boolean, useVideo: Boolean): Boolean {
        // Set up MediaExtractor to read from the source.
        val extractor = MediaExtractor()
        //       val isRawResId=uri.scheme == "android.resource" && uri.host == context.packageName && !uri.pathSegments.isNullOrEmpty())
        val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")!!.fileDescriptor
        extractor.setDataSource(fileDescriptor)
        val trackCount = extractor.trackCount
        // Set up MediaMuxer for the destination.
        val muxer = MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        // Set up the tracks and retrieve the max buffer size for selected tracks.
        val indexMap = SparseIntArray(trackCount)
        var bufferSize = -1
        try {
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                var selectCurrentTrack = false
                if (mime.startsWith("audio/") && useAudio) {
                    selectCurrentTrack = true
                } else if (mime.startsWith("video/") && useVideo) {
                    selectCurrentTrack = true
                }
                if (selectCurrentTrack) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    indexMap.put(i, dstIndex)
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        bufferSize = if (newSize > bufferSize) newSize else bufferSize
                    }
                }
            }
            if (bufferSize < 0)
                bufferSize = DEFAULT_BUFFER_SIZE
            // Set up the orientation and starting time for extractor.
            val retrieverSrc = MediaMetadataRetriever()
            retrieverSrc.setDataSource(fileDescriptor)
            val degreesString = retrieverSrc.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            if (degreesString != null) {
                val degrees = Integer.parseInt(degreesString)
                if (degrees >= 0)
                    muxer.setOrientationHint(degrees)
            }
            if (startMs > 0)
                extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            // Copy the samples from MediaExtractor to MediaMuxer. We will loop
            // for copying each sample and stop when we get to the end of the source
            // file or exceed the end time of the trimming.
            val offset = 0
            var trackIndex: Int
            val dstBuf = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
//        try {
            muxer.start()
            while (true) {
                bufferInfo.offset = offset
                bufferInfo.size = extractor.readSampleData(dstBuf, offset)
                if (bufferInfo.size < 0) {
                    //InstabugSDKLogger.d(TAG, "Saw input EOS.");
                    bufferInfo.size = 0
                    break
                } else {
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    if (endMs > 0 && bufferInfo.presentationTimeUs > endMs * 1000) {
                        //InstabugSDKLogger.d(TAG, "The current sample is over the trim end time.");
                        break
                    } else {
                        bufferInfo.flags = extractor.sampleFlags
                        trackIndex = extractor.sampleTrackIndex
                        muxer.writeSampleData(indexMap.get(trackIndex), dstBuf,
                                bufferInfo)
                        extractor.advance()
                    }
                }
            }
            muxer.stop()
            return true
            //        } catch (e: IllegalStateException) {
            // Swallow the exception due to malformed source.
            //InstabugSDKLogger.w(TAG, "The source video file is malformed");
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            muxer.release()
        }
        return false
    }


    private fun correctTimeToSyncSample(@NonNull track: Track, cutHere: Double, next: Boolean): Double {
        val timeOfSyncSamples = DoubleArray(track.syncSamples.size)
        var currentSample: Long = 0
        var currentTime = 0.0
        for (i in 0 until track.sampleDurations.size) {
            val delta = track.sampleDurations[i]
            if (Arrays.binarySearch(track.syncSamples, currentSample + 1) >= 0) {
                // samples always start with 1 but we start with zero therefore +1
                timeOfSyncSamples[Arrays.binarySearch(track.syncSamples, currentSample + 1)] = currentTime
            }
            currentTime += delta.toDouble() / track.trackMetaData.timescale.toDouble()
            ++currentSample
        }
        var previous = 0.0
        for (timeOfSyncSample in timeOfSyncSamples) {
            if (timeOfSyncSample > cutHere) {
                return if (next) {
                    timeOfSyncSample
                } else {
                    previous
                }
            }
            previous = timeOfSyncSample
        }
        return timeOfSyncSamples[timeOfSyncSamples.size - 1]
    }
}
