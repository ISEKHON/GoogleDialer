package com.dialer.calllogmaker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dialer.calllogmaker.R

// PERSONAL USE ONLY: Google Sans / Google Sans Text are proprietary (extracted from Google Phone) and
// must not ship in a distributed build. To publish, swap these for Google Sans Flex (SIL OFL) from
// github.com/google/fonts/tree/main/ofl/googlesansflex and include its OFL.txt.

/** Body text, as used across Google Phone's lists. */
val GoogleSansText = FontFamily(
    Font(R.font.google_sans_text_regular, FontWeight.Normal),
    Font(R.font.google_sans_text_medium, FontWeight.Medium),
    Font(R.font.google_sans_text_medium, FontWeight.SemiBold),
    Font(R.font.google_sans_text_medium, FontWeight.Bold),
)

/** Display text: keypad digits, typed number, big contact names, avatar letters. */
val GoogleSans = FontFamily(Font(R.font.google_sans_regular, FontWeight.Normal))

private fun TextStyle.with(family: FontFamily) = copy(fontFamily = family, letterSpacing = 0.sp)

private val base = Typography()

val Typography = Typography(
    displayLarge = base.displayLarge.with(GoogleSans),
    displayMedium = base.displayMedium.with(GoogleSans),
    displaySmall = base.displaySmall.with(GoogleSans),
    headlineLarge = base.headlineLarge.with(GoogleSans),
    headlineMedium = base.headlineMedium.with(GoogleSans),
    headlineSmall = base.headlineSmall.with(GoogleSans),
    titleLarge = base.titleLarge.with(GoogleSansText),
    titleMedium = base.titleMedium.with(GoogleSansText),
    titleSmall = base.titleSmall.with(GoogleSansText),
    bodyLarge = base.bodyLarge.with(GoogleSansText),
    bodyMedium = base.bodyMedium.with(GoogleSansText),
    bodySmall = base.bodySmall.with(GoogleSansText),
    labelLarge = base.labelLarge.with(GoogleSansText),
    labelMedium = base.labelMedium.with(GoogleSansText),
    labelSmall = base.labelSmall.with(GoogleSansText),
)
