package com.example.customcontroller.network

class ControllerState {
    @Volatile var digital: Int = 0
    @Volatile var extra: Int = 0
    @Volatile var leftX: Int = 0
    @Volatile var leftY: Int = 0
    @Volatile var rightX: Int = 0
    @Volatile var rightY: Int = 0
    @Volatile var leftTrigger: Int = 0
    @Volatile var rightTrigger: Int = 0
}
