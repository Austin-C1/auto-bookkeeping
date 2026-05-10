package com.wrbug.polymarketbot.service.bookkeeping

import java.math.BigDecimal

object BookkeepingWhatsappAmountParser {
    private val amountPattern = Regex("(\\d+(?:\\.\\d+)?)\\s*(au|a|usdt|u|r|rmb|cny)\\b", RegexOption.IGNORE_CASE)
    private val tenThousand = BigDecimal("10000")

    fun containsAmount(value: String): Boolean = amountPattern.containsMatchIn(value)

    fun parseAmount(value: String): BigDecimal? {
        val match = amountPattern.find(value) ?: return null
        val amount = match.groupValues.getOrNull(1)?.toBigDecimalOrNull() ?: return null
        val unit = match.groupValues.getOrNull(2)?.lowercase().orEmpty()
        return if (unit == "a" || unit == "au") amount.multiply(tenThousand) else amount
    }
}
