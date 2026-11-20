package com.example.remotecontrol

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.remotecontrol.R
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {
    companion object {
        // IPv4 hiện tại của PC trên mạng Hotspot / Wi-Fi
        private const val SERVER_HOST = "10.186.244.30"
        private const val SERVER_PORT = 5555
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val READ_TIMEOUT_MS = 5000
        private const val RETRY_DELAY_MS = 1500L
        private const val MAX_FRAME_BYTES = 10 * 1024 * 1024
    }

    private lateinit var screenImageView: ImageView
    private lateinit var hiddenEditText: EditText

    @Volatile
    private var streamThread: Thread? = null

    @Volatile
    private var currentSocket: Socket? = null

    @Volatile
    private var isStreaming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide()
        hideSystemBars()

        screenImageView = findViewById(R.id.screenImageView)
        hiddenEditText = findViewById(R.id.hiddenEditText)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        startStreaming()
    }

    override fun onPause() {
        stopStreaming()
        super.onPause()
    }

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    private fun startStreaming() {
        stopStreaming()
        isStreaming = true

        val thread = Thread({
            val lengthHeader = ByteArray(4)

            while (isStreaming && !Thread.currentThread().isInterrupted) {
                var socket: Socket? = null
                try {
                    socket = Socket()
                    socket.tcpNoDelay = true
                    socket.soTimeout = READ_TIMEOUT_MS
                    currentSocket = socket

                    socket.connect(InetSocketAddress(SERVER_HOST, SERVER_PORT), CONNECT_TIMEOUT_MS)
                    val inputStream = socket.getInputStream()

                    while (isStreaming && !Thread.currentThread().isInterrupted) {
                        if (!readFully(inputStream, lengthHeader, 4)) break
                        val frameLength = ByteBuffer.wrap(lengthHeader).int
                        if (frameLength <= 0 || frameLength > MAX_FRAME_BYTES) continue

                        val jpegBytes = ByteArray(frameLength)
                        if (!readFully(inputStream, jpegBytes, frameLength)) break

                        val bitmap: Bitmap? = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                        if (bitmap != null) {
                            runOnUiThread {
                                if (isStreaming && !isFinishing && !isDestroyed) {
                                    screenImageView.setImageBitmap(bitmap)
                                } else {
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {
                    sleepQuietly(RETRY_DELAY_MS)
                } finally {
                    try {
                        socket?.close()
                    } catch (_: Exception) {
                    }
                    if (currentSocket === socket) {
                        currentSocket = null
                    }
                }
            }
        }, "WifiScreenStreamThread").apply {
            isDaemon = true
            start()
        }

        streamThread = thread
    }

    private fun readFully(stream: InputStream, buffer: ByteArray, length: Int): Boolean {
        var totalRead = 0
        while (totalRead < length && isStreaming && !Thread.currentThread().isInterrupted) {
            val read = stream.read(buffer, totalRead, length - totalRead)
            if (read == -1) return false
            totalRead += read
        }
        return totalRead == length
    }

    private fun stopStreaming() {
        isStreaming = false

        val thread = streamThread
        streamThread = null
        thread?.interrupt()

        try {
            currentSocket?.close()
        } catch (_: Exception) {
        }
        currentSocket = null
    }

    private fun sleepQuietly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
