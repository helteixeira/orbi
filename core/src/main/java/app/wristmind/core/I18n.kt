package app.wristmind.core

import java.util.Locale

/** English by default. Portuguese when the device language is Portuguese. */
fun tr(en: String, pt: String): String = if (Locale.getDefault().language == "pt") pt else en

fun devicePrefersPortuguese(): Boolean = Locale.getDefault().language == "pt"
