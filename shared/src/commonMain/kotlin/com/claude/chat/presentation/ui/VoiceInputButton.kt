package com.claude.chat.presentation.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Voice input button with recording animation
 */
@Composable
fun VoiceInputButton(
    isRecording: Boolean,
    enabled: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulsing animation for recording state
    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice_scale"
    )

    FilledIconButton(
        onClick = {
            if (isRecording) {
                onStopRecording()
            } else {
                onStartRecording()
            }
        },
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (isRecording) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        ),
        modifier = modifier
            .then(
                if (isRecording) {
                    Modifier.scale(scale)
                } else {
                    Modifier
                }
            )
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = if (isRecording) "Stop recording" else "Start voice input",
            modifier = Modifier.size(24.dp),
            tint = if (isRecording) Color.White else LocalContentColor.current
        )
    }
}