package com.example.customcontroller

import android.app.Activity
import android.os.Bundle
import com.example.customcontroller.network.UdpControllerSender
import com.example.customcontroller.ui.ControllerView

class MainActivity : Activity() {
    private lateinit var sender: UdpControllerSender
    private lateinit var controllerView: ControllerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sender = UdpControllerSender()
        controllerView = ControllerView(this).apply {
            onStateChanged = sender::onControllerStateChanged
        }

        setContentView(controllerView)
        sender.start()
    }

    override fun onDestroy() {
        sender.stop()
        super.onDestroy()
    }
}
