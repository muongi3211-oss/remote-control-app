package com.example.remotecontrol

import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.io.DataInputStream
import java.io.OutputStream
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var hiddenEditText: EditText
    
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    // Độ phân giải gốc PC (khớp với tỷ lệ scale 0.5 bên Python Server)
    private val pcScreenWidth = 1920f
    private val pcScreenHeight = 1080f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.screenImageView)
        hiddenEditText = findViewById(R.id.hiddenEditText)

        // Địa chỉ IP của máy tính trong mạng LAN (Thay bằng IP thực tế của PC bạn)
        val serverIp = "192.168.1.150" 
        val port = 5000

        connectToPcServer(serverIp, port)
    }

    private fun connectToPcServer(ip: String, port: Int) {
        thread {
            try {
                socket = Socket(ip, port)
                outputStream = socket!!.getOutputStream()
                val input = DataInputStream(socket!!.getInputStream())

                runOnUiThread {
                    setupTouchListener()
                    setupKeyboardListener()
                }

                while (true) {
                    val length = input.readInt()
                    val buffer = ByteArray(length)
                    input.readFully(buffer)

                    val bitmap = BitmapFactory.decodeByteArray(buffer, 0, length)
                    runOnUiThread {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupTouchListener() {
        imageView.setOnTouchListener { v, event ->
            val viewWidth = v.width.toFloat()
            val viewHeight = v.height.toFloat()

            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val realX = (event.x / viewWidth) * pcScreenWidth
                    val realY = (event.y / viewHeight) * pcScreenHeight
                    sendDataAsync("MOVE:${realX.toInt()}:${realY.toInt()}\n")
                }
                MotionEvent.ACTION_UP -> {
                    val realX = (event.x / viewWidth) * pcScreenWidth
                    val realY = (event.y / viewHeight) * pcScreenHeight
                    sendDataAsync("CLICK:${realX.toInt()}:${realY.toInt()}\n")
                }
            }
            true
        }
    }

    private fun setupKeyboardListener() {
        hiddenEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) {
                    sendDataAsync("KEY:$s\n")
                    s.clear()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        hiddenEditText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DEL -> {
                        sendDataAsync("SPECIAL:backspace\n")
                        true
                    }
                    KeyEvent.KEYCODE_ENTER -> {
                        sendDataAsync("SPECIAL:enter\n")
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    }

    private fun sendDataAsync(data: String) {
        thread {
            try {
                outputStream?.write(data.toByteArray())
                outputStream?.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
