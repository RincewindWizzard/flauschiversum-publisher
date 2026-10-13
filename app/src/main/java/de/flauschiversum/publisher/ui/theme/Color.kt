package de.flauschiversum.publisher.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Farbschema „Flauschi-Nebel“ des Blogs (Quelle: content/style/theme.css im Repository flauschiversum).
 * Alle Farbwerte der App stehen in dieser Datei.
 */
object Nebel {
    object Light {
        val bg = Color(0xFFF6F3FC)
        val card = Color(0xFFFFFFFF)
        val text = Color(0xFF1D1930)
        val muted = Color(0xFF575270)
        val link = Color(0xFF4A38B0)
        val accent = Color(0xFF6D4FE0)
        val accentInk = Color(0xFFFFFFFF)
        val accentSoft = Color(0xFFE9E4FA)
        val border = Color(0xFFDDD7EF)
        val borderStrong = Color(0xFF8F89AB)
        val error = Color(0xFFB3261E)
        val onError = Color(0xFFFFFFFF)
        val gold = Color(0xFF7A5A00)
        val onGold = Color(0xFFFFFFFF)
        val success = Color(0xFF1B6B3A)
    }

    object Dark {
        val bg = Color(0xFF0E0B1F)
        val card = Color(0xFF191537)
        val text = Color(0xFFEBE8F8)
        val muted = Color(0xFFAAA5C8)
        val link = Color(0xFFB4A2FF)
        val accent = Color(0xFF8F73F5)
        val accentInk = Color(0xFF0E0B1F)
        val accentSoft = Color(0xFF2A2459)
        val border = Color(0xFF2D2860)
        val borderStrong = Color(0xFF6F68A0)
        val error = Color(0xFFF2B8B5)
        val onError = Color(0xFF601410)
        val gold = Color(0xFFFFD166)
        val onGold = Color(0xFF0E0B1F)
        val success = Color(0xFF7FD6A0)
    }
}
