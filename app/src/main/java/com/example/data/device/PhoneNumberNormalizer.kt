package com.example.data.device

import java.util.Locale

/**
 * Canonicalizes numbers for local comparisons. The original number is kept
 * for display and SMS delivery.
 */
object PhoneNumberNormalizer {
    fun normalize(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        val digits = trimmed.mapNotNull { toAsciiDigit(it) }.joinToString("")
        if (digits.isNotEmpty()) {
            return when {
                digits.startsWith("0098") -> "+" + digits.drop(2)
                digits.startsWith("98") && digits.length >= 10 -> "+" + digits
                digits.startsWith("0") && digits.length in 10..11 -> "+98" + digits.drop(1)
                trimmed.startsWith("+") -> "+" + digits
                else -> digits
            }
        }
        return trimmed.replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    }

    fun areEquivalent(first: String, second: String): Boolean {
        val left = normalize(first)
        val right = normalize(second)
        return left.isNotEmpty() && left == right
    }

    private fun toAsciiDigit(value: Char): Char? = when (value) {
        in '0'..'9' -> value
        in '۰'..'۹' -> ('0'.code + (value.code - '۰'.code)).toChar()
        in '٠'..'٩' -> ('0'.code + (value.code - '٠'.code)).toChar()
        else -> null
    }
}
