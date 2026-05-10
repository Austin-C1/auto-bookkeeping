package com.wrbug.polymarketbot.service.bookkeeping

import com.wrbug.polymarketbot.dto.BookkeepingWhatsappOrderImportDto
import com.wrbug.polymarketbot.entity.BookkeepingWhatsappOrder
import java.math.BigDecimal

object BookkeepingWhatsappOrderPrecision {
    fun isLikelyOrderBlock(rawMessage: String): Boolean {
        val cleaned = rawMessage.trim()
        if (cleaned.isEmpty()) return false
        val usefulLines = cleaned.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return containsOrderOdds(cleaned) &&
            BookkeepingWhatsappAmountParser.containsAmount(cleaned) &&
            usefulLines.size >= MIN_ORDER_LINES
    }

    fun deduplicateImports(orders: List<BookkeepingWhatsappOrderImportDto>): List<BookkeepingWhatsappOrderImportDto> {
        val seen = mutableSetOf<String>()
        return orders.filter { order ->
            val key = importIdentityKey(order) ?: "order:${order.orderKey}"
            seen.add(key)
        }
    }

    fun deduplicateExistingOrders(orders: List<BookkeepingWhatsappOrder>): List<BookkeepingWhatsappOrder> {
        val seen = mutableSetOf<String>()
        return orders.filter { order ->
            val key = existingIdentityKey(order) ?: "order:${order.orderKey}"
            seen.add(key)
        }
    }

    fun importIdentityKey(
        order: BookkeepingWhatsappOrderImportDto,
        groupId: Long? = null,
        sourceTypeOverride: String? = null
    ): String? {
        val groupKey = groupId?.toString() ?: normaliseText(order.groupKey)
        val sourceType = normaliseText(sourceTypeOverride ?: order.sourceType)
        val direction = normaliseText(order.direction)
        val league = normaliseText(order.leagueName)
        val match = normaliseMatch(order.matchName)
        val market = normaliseMarket(order.marketText)
        val odds = normaliseDecimal(order.oddsValue)
        val amount = normaliseDecimal(order.amount)
        val currency = normaliseText(order.currency)
        if (groupKey.isBlank() || direction.isBlank() || match.isBlank() || market.isBlank() || odds.isBlank() || amount.isBlank()) {
            return null
        }
        return listOf(
            sourceType,
            groupKey,
            direction,
            league,
            match,
            market,
            odds,
            amount,
            currency,
            confirmationToken(order.rawMessage)
        ).joinToString("|")
    }

    fun existingIdentityKey(order: BookkeepingWhatsappOrder): String? {
        val groupKey = order.groupId?.toString().orEmpty()
        val sourceType = normaliseText(order.sourceType)
        val direction = normaliseText(order.direction)
        val league = normaliseText(order.leagueName)
        val match = normaliseMatch(order.matchName)
        val market = normaliseMarket(order.marketText)
        val odds = normaliseDecimal(order.oddsValue)
        val amount = normaliseDecimal(order.amount)
        val currency = normaliseText(order.currency)
        if (groupKey.isBlank() || direction.isBlank() || match.isBlank() || market.isBlank() || odds.isBlank() || amount.isBlank()) {
            return null
        }
        return listOf(
            sourceType,
            groupKey,
            direction,
            league,
            match,
            market,
            odds,
            amount,
            currency,
            confirmationToken(order.rawMessage)
        ).joinToString("|")
    }

    private fun containsOrderOdds(value: String): Boolean =
        ORDER_ODDS_REGEX.containsMatchIn(value)

    fun confirmationToken(value: String?): String {
        val compact = value.orEmpty().trim()
        val match = CONFIRMATION_REGEX.find(compact) ?: return ""
        val number = match.groupValues.getOrNull(2).orEmpty().lowercase()
        return if (number.isBlank()) "确认" else "确认$number"
    }

    private fun normaliseMatch(value: String?): String =
        normaliseText(value)
            .replace(MATCH_SEPARATOR_REGEX, "v")
            .replace(SOFT_PUNCTUATION_REGEX, "")

    private fun normaliseMarket(value: String?): String =
        normaliseText(value)
            .replace(ORDER_ODDS_SUFFIX_REGEX, "")
            .replace(SOFT_PUNCTUATION_REGEX, "")
            .replace("+", "")

    private fun normaliseText(value: String?): String =
        value.orEmpty()
            .trim()
            .lowercase()
            .replace('　', ' ')
            .replace(Regex("\\s+"), " ")

    private fun normaliseDecimal(value: BigDecimal?): String =
        value?.stripTrailingZeros()?.toPlainString().orEmpty()

    private const val MIN_ORDER_LINES = 4
    private val ORDER_ODDS_REGEX = Regex("""@\s*[+-]?\d+(?:\.\d+)?""")
    private val ORDER_ODDS_SUFFIX_REGEX = Regex("""\s*@\s*[+-]?\d+(?:\.\d+)?\s*$""")
    private val MATCH_SEPARATOR_REGEX = Regex("""\s+(?:vs?\.?|对)\s+""", RegexOption.IGNORE_CASE)
    private val SOFT_PUNCTUATION_REGEX = Regex("""[\s·.\-_/（）()\[\]【】]+""")
    private val CONFIRMATION_REGEX = Regex("""(?:确认|确单)\s*([:：#-]?\s*)?([A-Za-z0-9一二三四五六七八九十]+)?""")
}
