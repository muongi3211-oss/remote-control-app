package com.example.remotecontrol

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.view.Surface
import java.io.InputStream
import java.nio.ByteBuffer

class JpgStreamDecoder(private val surface: Surface) {
    @Volatile
    private var isRunning = false
    private var socketInputStream: InputStream? = null
    private var decodeThread: Thread? = null

    companion object {
        private const val TAG = "JpgStreamDecoder"
        private const val MAX_FRAME_BYTES = 10 * 1024 * 1024
    }

    fun start(inputStream: InputStream) {
        stop()
        socketInputStream = inputStream
        isRunning = true

        decodeThread = Thread({
            readSocketAndRenderLoop()
        }, "JpgStreamDecoderThread").apply {
            isDaemon = true
            start()
        }
    }

    private fun readSocketAndRenderLoop() {
        val lengthHeader = ByteArray(4)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val inputStream = socketInputStream ?: return

        try {
            while (isRunning && !Thread.currentThread().isInterrupted) {
                if (!surface.isValid) {
                    Thread.sleep(50)
                    continue
                }

                if (!readFully(inputStream, lengthHeader, 4)) break
                val dataLength = ByteBuffer.wrap(lengthHeader).int
                if (dataLength <= 0 || dataLength > MAX_FRAME_BYTES) {
                    Log.w(TAG, "Kích thước gói tin không hợp lệ: $dataLength")
                    continue
                }

                val jpegBytes = ByteArray(dataLength)
                if (!readFully(inputStream, jpegBytes, dataLength)) break

                val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: continue
                try {
                    if (!surface.isValid) continue
                    val canvas = surface.lockCanvas(null) ?: continue
                    try {
                        canvas.drawColor(Color.BLACK)
                        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                        val dstRect = Rect(0, 0, canvas.width, canvas.height)
                        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
                    } finally {
                        surface.unlockCanvasAndPost(canvas)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi vẽ lên Surface", e)
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (e: InterruptedException) {
            // Dừng luồng bình thường
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi luồng nhận dữ liệu", e)
        } finally {
            isRunning = false
            try {
                inputStream.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun readFully(stream: InputStream, buffer: ByteArray, length: Int): Boolean {
        var bytesRead = 0
        while (bytesRead < length && isRunning) {
            val read = stream.read(buffer, bytesRead, length - bytesRead)
            if (read == -1) return false
            bytesRead += read
        }
        return bytesRead == length
    }

    fun join(timeoutMs: Long = 1500) {
        try {
            decodeThread?.join(timeoutMs)
        } catch (_: InterruptedException) {
        }
    }

    fun stop() {
        isRunning = false
        try {
            socketInputStream?.close()
        } catch (_: Exception) {
        }
        socketInputStream = null
        decodeThread?.interrupt()
        decodeThread = null
    }
}
