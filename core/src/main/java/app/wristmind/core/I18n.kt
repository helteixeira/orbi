package app.wristmind.core

import java.util.Locale

/** The app is English only. The second text is kept for a future translation. */
@Suppress("UNUSED_PARAMETER")
fun tr(en: String, pt: String): String = en

fun devicePrefersPortuguese(): Boolean = Locale.getDefault().language == "pt"
