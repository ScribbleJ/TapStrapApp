package com.scribblej.tapstrapapp.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Shapes
import androidx.wear.compose.material.Typography



@Composable
fun BasicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        // Default color palettes for light and dark themes are used here
        typography = Typography(),
        shapes = Shapes(),
        content = content
    )
}