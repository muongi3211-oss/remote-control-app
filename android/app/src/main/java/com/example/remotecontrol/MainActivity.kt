package com.example.remotecontrol

import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowManager
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
    
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    
    private var isLongPressed = false
    private var scaleFactor = 1.0f

    // Độ phân giải gốc PC (khớp với tỷ lệ scale 0.8 bên Python Server)
    private val pcScreenWidth = 1920f
    private val pcScreenHeight = 1080f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ẩn thanh thông báo (Status Bar) để hiển thị Full màn hình tuyệt đối
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.screenImageView)
        hiddenEditText = findViewById(R.id.hiddenEditText)

        // Địa chỉ IP cố định của máy tính trong mạng LAN
        val serverIp = "10.192.207.30" 
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
                    setupTouchAndGestureListeners()
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

    private fun setupTouchAndGestureListeners() {
        // 1. Bộ thu phóng bằng 2 ngón tay (Pinch-to-zoom)
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 5.0f)) // Giới hạn zoom từ 1x đến 5x
                
                imageView.scaleX = scaleFactor
                imageView.scaleY = scaleFactor
                return true
            }
        })

        // 2. Bộ nhận diện chạm giữ (Long Press) để mở menu chuột phải
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                isLongPressed = true
                val viewWidth = imageView.width.toFloat()
                val viewHeight = imageView.height.toFloat()
                val realX = (e.x / viewWidth) * pcScreenWidth
                val realY = (e.y / viewHeight) * pcScreenHeight
                sendDataAsync("RIGHT_CLICK:${realX.toInt()}:${realY.toInt()}\n")
            }
        })

        // Gộp chung các sự kiện cảm ứng vào ImageView
        imageView.setOnTouchListener { v, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            val viewWidth = v.width.toFloat()
            val viewHeight = v.height.toFloat()

            // Chỉ cho phép điều khiển chuột khi không ở chế độ phóng to (scaleFactor == 1.0f)
            if (scaleFactor == 1.0f) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPressed = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val realX = (event.x / viewWidth) * pcScreenWidth
                        val realY = (event.y / viewHeight) * pcScreenHeight
                        sendDataAsync("MOVE:${realX.toInt()}:${realY.toInt()}\n")
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isLongPressed) {
                            val realX = (event.x / viewWidth) * pcScreenWidth
                            val realY = (event.y / viewHeight) * pcScreenHeight
                            sendDataAsync("CLICK:${realX.toInt()}:${realY.toInt()}\n")
                        }
                    }
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
