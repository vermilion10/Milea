package com.github.vermilion10.milea.util

import androidx.compose.runtime.staticCompositionLocalOf
import com.github.vermilion10.milea.data.repository.CurrencySettings
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formats money amounts with the user's chosen currency symbol and number of
 * decimals. Amounts are stored as plain numbers; the currency is display-only.
 */
class MoneyFormat(val settings: CurrencySettings = CurrencySettings()) {
    private val symbols = DecimalFormatSymbols.getInstance(Locale.getDefault())

    private fun pattern(decimals: Int): DecimalFormat {
        val fraction = if (decimals > 0) "." + "0".repeat(decimals) else ""
        return DecimalFormat("#,##0$fraction", symbols)
    }

    private val amountFormat = pattern(settings.decimals)

    // Small unit prices (per liter / per km) need more precision than bills
    // do, otherwise a 0-decimal currency shows every cost-per-km as "0".
    private val preciseFormat = pattern((settings.decimals + 1).coerceAtMost(3))

    private fun withSymbol(number: String): String {
        val symbol = settings.symbol.trim()
        if (symbol.isEmpty()) return number
        // Multi-letter codes (Rp, IDR, EUR) read better with a space.
        return if (symbol.length > 1 && symbol.last().isLetter()) "$symbol $number" else "$symbol$number"
    }

    fun format(amount: Float): String = withSymbol(amountFormat.format(amount.toDouble()))

    fun formatPrecise(amount: Float): String =
        if (kotlin.math.abs(amount) >= 100f) format(amount)
        else withSymbol(preciseFormat.format(amount.toDouble()))

    /** Compact form for chart axes and tight tiles: 1.2k, 3.4M. */
    fun formatCompact(amount: Float): String {
        val abs = kotlin.math.abs(amount)
        val (value, suffix) = when {
            abs >= 1_000_000_000f -> amount / 1_000_000_000f to "B"
            abs >= 1_000_000f -> amount / 1_000_000f to "M"
            abs >= 1_000f -> amount / 1_000f to "k"
            else -> return format(amount)
        }
        val number = if (kotlin.math.abs(value) >= 100f) String.format(Locale.getDefault(), "%.0f", value)
        else String.format(Locale.getDefault(), "%.1f", value)
        return withSymbol(number + suffix)
    }
}

val LocalMoney = staticCompositionLocalOf { MoneyFormat() }
