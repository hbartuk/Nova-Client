package com.radiantbyte.novaclient.overlay

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radiantbyte.novaclient.R
import com.radiantbyte.novaclient.ui.theme.NovaColors

class NovaOverlayButton : OverlayWindow() {

    override val layoutParams by lazy {
        WindowManager.LayoutParams().apply {
            width = 120
            height = 120
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = android.graphics.PixelFormat.TRANSLUCENT

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alpha = 1.0f
                blurBehindRadius = 10
            }
        }
    }

    @Composable
    override fun Content() {
        var isPressed by remember { mutableStateOf(false) }
        val context = LocalContext.current

        // Simple press animation only
        val pressScale by animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            animationSpec = spring(stiffness = Spring.StiffnessHigh),
            label = "press_scale"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                        layoutParams.x += dragAmount.x.toInt()
                        layoutParams.y += dragAmount.y.toInt()
                        try {
                            windowManager.updateViewLayout(composeView, layoutParams)
                        } catch (_: Exception) {
                            // Handle exception silently
                        }
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isPressed = true
                    NovaOverlayManager.showClickGUI()
                },
            contentAlignment = Alignment.Center
        ) {
            // Simple main button - no glow ring
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .scale(pressScale)
                    .background(
                        color = Color.Black,
                        shape = CircleShape
                    )
                    .border(
                        width = 2.dp,
                        color = Color.Cyan,
                        shape = CircleShape
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "N",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.Cyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Reset pressed state
        LaunchedEffect(isPressed) {
            if (isPressed) {
                kotlinx.coroutines.delay(100)
                isPressed = false
            }
        }
    }
}
