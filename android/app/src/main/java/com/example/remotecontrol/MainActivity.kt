package com.example.usbremote

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : AppCompatActivity() {
    companion object {
        private const val SERVER_HOST = "10.186.244.30" // Ví dụ: "127.0.0.1" hoặc "192.168.1.50"
        private const val SERVER_PORT = 5555
    }

    private var decoder: JpgStreamDecoder? = null
    private var socketThread: Thread? = null
    @Volatile
    private var isStreaming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide()

        val surfaceView = findViewById<SurfaceView>(R.id.surfaceView)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                startStreaming(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                stopStreaming()
            }
        })
    }

    private fun startStreaming(surface: android.view.Surface) {
        stopStreaming()
        isStreaming = true

        socketThread = Thread({
            while (isStreaming && !Thread.currentThread().isInterrupted) {
                var socket: Socket? = null
                try {
                    socket = Socket()
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(SERVER_HOST, SERVER_PORT), 3000)

                    val activeDecoder = JpgStreamDecoder(surface)
                    decoder = activeDecoder
                    activeDecoder.start(socket.getInputStream())
                    activeDecoder.join()
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {
                    try {
                        Thread.sleep(1500)
                    } catch (_: InterruptedException) {
                        break
                    }
                } finally {
                    try {
                        socket?.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }, "SocketConnectThread").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopStreaming() {
        isStreaming = false
        socketThread?.interrupt()
        socketThread = null
        decoder?.stop()
        decoder = null
    }

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }
}
