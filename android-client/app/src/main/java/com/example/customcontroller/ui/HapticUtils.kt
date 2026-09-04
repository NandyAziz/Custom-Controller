package com.example.customcontroller.ui

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Local haptic helper. It stays completely outside the UDP/network hot path.
 */
object HapticUtils {
    fun click(view: View) {
        // Respect the device's haptic/touch-feedback settings.
        view.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY
        )
    }
}
