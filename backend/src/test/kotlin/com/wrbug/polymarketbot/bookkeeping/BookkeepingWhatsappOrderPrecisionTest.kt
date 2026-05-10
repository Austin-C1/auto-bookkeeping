package com.wrbug.polymarketbot.bookkeeping

import com.wrbug.polymarketbot.dto.BookkeepingWhatsappOrderImportDto
import com.wrbug.polymarketbot.entity.BookkeepingWhatsappOrder
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingWhatsappAmountParser
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingWhatsappOrderPrecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BookkeepingWhatsappOrderPrecisionTest {
    @Test
    fun `deduplicates repeated order mentions with different message ids`() {
        val orders = listOf(
            order(
                orderKey = "wa-100-1",
                rawMessage = """
                    足球 大 / 小
                    波兰超级联赛
                    琴斯托霍瓦 v 科罗纳
                    大 2.5 @ 0.9
                    部门跟104000r 确认1
                """.trimIndent()
            ),
            order(
                orderKey = "wa-101-1",
                rawMessage = """
                    足球 大 / 小
                    波兰超级联赛
                    琴斯托霍瓦 v 科罗纳
                    大 2.5 @0.90
                    部门跟 104000R 确认 1
                """.trimIndent()
            )
        )

        val deduped = BookkeepingWhatsappOrderPrecision.deduplicateImports(orders)

        assertEquals(1, deduped.size)
        assertEquals("wa-100-1", deduped.single().orderKey)
    }

    @Test
    fun `keeps same market and amount when confirmation numbers differ`() {
        val orders = listOf(
            order(orderKey = "wa-100-1", rawMessage = "确认1"),
            order(orderKey = "wa-101-1", rawMessage = "确认2")
        )

        val deduped = BookkeepingWhatsappOrderPrecision.deduplicateImports(orders)

        assertEquals(listOf("wa-100-1", "wa-101-1"), deduped.map { it.orderKey })
    }

    @Test
    fun `deduplicates existing entities before reconciliation`() {
        val orders = listOf(
            entity(orderKey = "entity-message-1", rawMessage = "确认1"),
            entity(orderKey = "entity-message-2", rawMessage = "确认 1")
        )

        val deduped = BookkeepingWhatsappOrderPrecision.deduplicateExistingOrders(orders)

        assertEquals(1, deduped.size)
        assertEquals("entity-message-1", deduped.single().orderKey)
    }

    @Test
    fun `recognises only complete order blocks during group scan`() {
        assertTrue(
            BookkeepingWhatsappOrderPrecision.isLikelyOrderBlock(
                """
                    足球 大 / 小
                    波兰超级联赛
                    琴斯托霍瓦 v 科罗纳
                    大 2.5 @ 0.9
                    部门跟104000r 确认
                """.trimIndent()
            )
        )
        assertFalse(BookkeepingWhatsappOrderPrecision.isLikelyOrderBlock("部门今天先跟104000r，晚点确认"))
        assertFalse(BookkeepingWhatsappOrderPrecision.isLikelyOrderBlock("琴斯托霍瓦 v 科罗纳 大 2.5 @ 0.9"))
    }

    @Test
    fun `amount parser recognises r currency from company group messages`() {
        assertEquals(BigDecimal("104000"), BookkeepingWhatsappAmountParser.parseAmount("部门跟104000r 确认"))
    }

    private fun order(
        orderKey: String,
        rawMessage: String,
        confirmationSuffix: String = ""
    ) = BookkeepingWhatsappOrderImportDto(
        groupKey = "group-a",
        orderKey = orderKey,
        sourceType = "whatsapp",
        direction = "rolling",
        messageTime = 1000,
        senderName = "部门",
        rawMessage = rawMessage + confirmationSuffix,
        leagueName = "波兰超级联赛",
        matchName = "琴斯托霍瓦 v 科罗纳",
        marketText = "大 2.5 @ 0.90",
        oddsValue = BigDecimal("0.90"),
        amount = BigDecimal("104000"),
        currency = "R",
        parseStatus = "parsed"
    )

    private fun entity(
        orderKey: String,
        rawMessage: String
    ) = BookkeepingWhatsappOrder(
        id = orderKey.hashCode().toLong(),
        taskId = null,
        groupId = 1,
        sourceType = "whatsapp",
        businessDate = "2026-05-10",
        orderKey = orderKey,
        direction = "rolling",
        messageTime = 1000,
        senderName = "部门",
        rawMessage = rawMessage,
        leagueName = "波兰超级联赛",
        matchName = "琴斯托霍瓦 v 科罗纳",
        marketText = "大 2.5 @ 0.90",
        oddsValue = BigDecimal("0.90"),
        amount = BigDecimal("104000"),
        currency = "R",
        parseStatus = "parsed",
        settlementResult = null,
        createdAt = 1000,
        updatedAt = 1000
    )
}
