package com.example.remotecontrol

import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : AppCompatActivity() {
    companion object {
        // Đổi thành IPv4 hiện tại của PC trên mạng Wi-Fi / Hotspot
        private const val SERVER_HOST = "10.186.244.30"
        private const val SERVER_PORT = 5555
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val READ_TIMEOUT_MS = 5000
        private const val RETRY_DELAY_MS = 1500L
    }

    @Volatile
    private var decoder: JpgStreamDecoder? = null

    @Volatile
    private var socketThread: Thread? = null

    @Volatile
    private var currentSocket: Socket? = null

    @Volatile
    private var activeSurface: Surface? = null

    @Volatile
    private var isStreaming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide()
        hideSystemBars()

        val surfaceView = findViewById<SurfaceView>(R.id.surfaceView)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                activeSurface = holder.surface
                startStreaming(holder.surface)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                activeSurface = holder.surface
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                stopStreaming()
                activeSurface = null
            }
        })
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
        val surface = activeSurface
        if (!isStreaming && surface != null && surface.isValid) {
            startStreaming(surface)
        }
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

    private fun startStreaming(surface: Surface) {
        stopStreaming()
        isStreaming = true

        val thread = Thread({
            while (isStreaming && !Thread.currentThread().isInterrupted) {
                if (!surface.isValid) {
                    sleepQuietly(300L)
                    continue
                }

                var socket: Socket? = null
                try {
                    socket = Socket()
                    socket.tcpNoDelay = true
                    socket.soTimeout = READ_TIMEOUT_MS
                    currentSocket = socket

                    socket.connect(InetSocketAddress(SERVER_HOST, SERVER_PORT), CONNECT_TIMEOUT_MS)
                    if (!isStreaming || Thread.currentThread().isInterrupted) {
                        break
                    }

                    val streamDecoder = JpgStreamDecoder(surface)
                    decoder = streamDecoder
                    streamDecoder.start(socket.getInputStream())
                    streamDecoder.join()
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {
                    sleepQuietly(RETRY_DELAY_MS)
                } finally {
                    decoder?.stop()
                    decoder = null
                    try {
                        socket?.close()
                    } catch (_: Exception) {
                    }
                    if (currentSocket === socket) {
                        currentSocket = null
                    }
                }
            }
        }, "WifiStreamConnectThread").apply {
            isDaemon = true
            start()
        }

        socketThread = thread
    }

    private fun stopStreaming() {
        isStreaming = false

        val thread = socketThread
        socketThread = null
        thread?.interrupt()

        try {
            currentSocket?.close()
        } catch (_: Exception) {
        }
        currentSocket = null

        decoder?.stop()
        decoder = null
    }

    private fun sleepQuietly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
