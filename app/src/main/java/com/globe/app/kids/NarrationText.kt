package com.globe.app.kids

/** Preserve combining marks and punctuation in scripts such as Hindi and Arabic. */
object NarrationText {
    fun clean(text: String): String = text
        .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\p{P}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
