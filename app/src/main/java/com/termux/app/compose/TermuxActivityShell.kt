package com.termux.app.compose

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.R

/**
 * Phase-1 Compose shell: hosts the existing [R.layout.activity_termux] hierarchy via
 * [AndroidView] so all View IDs, [com.termux.app.terminal.TermuxActivityRootView] measure
 * behavior, terminal, drawer, and dock continue to work with [android.app.Activity.findViewById].
 *
 * Later phases replace drawer / accessory structure with Compose while keeping TerminalView and
 * SuggestionBarView as AndroidViews.
 */
@Composable
fun TermuxActivityShell(
    root: View,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = {
            (root.parent as? ViewGroup)?.removeView(root)
            root
        },
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Java-friendly entry: install Compose content on [activity] with a pre-inflated shell [root].
 *
 * Prefers inflating [R.layout.activity_termux] before calling this so [findViewById] works as soon
 * as composition attaches the tree (same IDs as the pre-Compose path).
 *
 * Uses [ComposeView.createComposition] so the [AndroidView] factory runs before
 * [TermuxActivity] continues [findViewById] setup in [android.app.Activity.onCreate].
 */
object TermuxActivityContent {
    @JvmStatic
    fun install(activity: ComponentActivity, root: View) {
        val composeView = ComposeView(activity).apply {
            // Match activity lifecycle; dispose with the view tree.
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                TermuxComposeTheme {
                    TermuxActivityShell(root = root)
                }
            }
        }
        activity.setContentView(composeView)
        // Eagerly compose so AndroidView attaches [root] before onCreate continues.
        composeView.createComposition()
    }
}
