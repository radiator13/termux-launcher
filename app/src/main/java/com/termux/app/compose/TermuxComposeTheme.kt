package com.termux.app.compose

import android.content.Context
import android.util.TypedValue
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.termux.shared.R as SharedR

/**
 * Bridges existing Termux XML theme attrs (`termuxColor*`) into Material3 for Compose chrome.
 * Does not replace View theming — only colors Compose-owned shell surfaces.
 */
@Composable
fun TermuxComposeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = remember(context.theme) {
        context.buildTermuxColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

private fun Context.buildTermuxColorScheme(): ColorScheme {
    val primary = resolveThemeColor(SharedR.attr.termuxColorPrimary, 0xFF6750A4.toInt())
    val onPrimary = resolveThemeColor(SharedR.attr.termuxColorOnPrimary, 0xFFFFFFFF.toInt())
    val secondary = resolveThemeColor(SharedR.attr.termuxColorSecondary, 0xFF625B71.toInt())
    val onSecondary = resolveThemeColor(SharedR.attr.termuxColorOnSecondary, 0xFFFFFFFF.toInt())
    val surface = resolveThemeColor(SharedR.attr.termuxColorSurfaceBase, 0xFF1C1B1F.toInt())
    val onSurface = resolveThemeColor(SharedR.attr.termuxColorOnSurface, 0xFFE6E1E5.toInt())
    val onSurfaceVariant = resolveThemeColor(SharedR.attr.termuxColorOnSurfaceVariant, 0xFFCAC4D0.toInt())
    val surfaceContainer = resolveThemeColor(SharedR.attr.termuxColorSurfacePanel, surface)
    val surfaceContainerHigh = resolveThemeColor(SharedR.attr.termuxColorSurfacePanelHigh, surfaceContainer)
    val primaryContainer = resolveThemeColor(SharedR.attr.termuxColorPrimaryContainer, primary)
    val onPrimaryContainer = resolveThemeColor(SharedR.attr.termuxColorOnPrimaryContainer, onPrimary)
    val error = resolveThemeColor(SharedR.attr.termuxColorError, 0xFFB3261E.toInt())
    val outline = resolveThemeColor(SharedR.attr.termuxColorOutlineVariant, onSurfaceVariant)

    // Heuristic: dark surface luminance → dark scheme
    val dark = isColorDark(surface)
    return if (dark) {
        darkColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(onPrimaryContainer),
            secondary = Color(secondary),
            onSecondary = Color(onSecondary),
            background = Color(surface),
            onBackground = Color(onSurface),
            surface = Color(surface),
            onSurface = Color(onSurface),
            surfaceVariant = Color(surfaceContainerHigh),
            onSurfaceVariant = Color(onSurfaceVariant),
            error = Color(error),
            outline = Color(outline),
        )
    } else {
        lightColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(onPrimaryContainer),
            secondary = Color(secondary),
            onSecondary = Color(onSecondary),
            background = Color(surface),
            onBackground = Color(onSurface),
            surface = Color(surface),
            onSurface = Color(onSurface),
            surfaceVariant = Color(surfaceContainerHigh),
            onSurfaceVariant = Color(onSurfaceVariant),
            error = Color(error),
            outline = Color(outline),
        )
    }
}

private fun Context.resolveThemeColor(attr: Int, fallback: Int): Int {
    val typedValue = TypedValue()
    if (!theme.resolveAttribute(attr, typedValue, true)) {
        return fallback
    }
    return when {
        typedValue.resourceId != 0 -> {
            try {
                ContextCompat.getColor(this, typedValue.resourceId)
            } catch (_: Exception) {
                typedValue.data
            }
        }
        typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT -> typedValue.data
        else -> fallback
    }
}

private fun isColorDark(color: Int): Boolean {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    // Relative luminance approximation
    val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    return luminance < 0.5
}
