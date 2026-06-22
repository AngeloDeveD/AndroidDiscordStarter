package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object AppStyles {
    // Reusable OutlinedTextField style parameters
    @Composable
    fun textFieldColors() = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = OutlineGrey,
        unfocusedBorderColor = OutlineGrey.copy(alpha = 0.4f),
        focusedTextColor = TextDark,
        unfocusedTextColor = TextDark,
        focusedPlaceholderColor = TextMuted,
        unfocusedPlaceholderColor = TextMuted
    )

    // Reusable standard border modifiers
    fun standardBorder() = BorderStroke(1.dp, DividerPurple.copy(alpha = 0.3f))
    fun terminalBorder() = BorderStroke(1.dp, TerminalGrey)

    // Reusable Card Colors
    @Composable
    fun standardCardColors() = CardDefaults.cardColors(
        containerColor = CardSurfacePurple
    )

    @Composable
    fun terminalCardColors() = CardDefaults.cardColors(
        containerColor = TerminalBackground
    )
}
