package com.wrbug.polymarketbot.bookkeeping

import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingCalculator
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingCalculatorAccount
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingCalculatorCrownWager
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingCalculatorWhatsappGroup
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingCalculatorWhatsappOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BookkeepingCalculatorTest {
    private val calculator = BookkeepingCalculator()

    @Test
    fun `downstream full win uses confirmed odds without rebate adjustment`() {
        val cashflow = calculator.calculateDownstreamCashflow(
            stake = BigDecimal("10000"),
            odds = BigDecimal("1.89"),
            settlementResult = "win"
        )

        assertMoneyEquals("18900", cashflow)
    }

    @Test
    fun `downstream half win uses confirmed odds and then halves settlement`() {
        val cashflow = calculator.calculateDownstreamCashflow(
            stake = BigDecimal("10000"),
            odds = BigDecimal("1.89"),
            settlementResult = "win_half"
        )

        assertMoneyEquals("9450", cashflow)
    }

    @Test
    fun `downstream losing and push results use direct settlement rules`() {
        assertMoneyEquals(
            "-10000",
            calculator.calculateDownstreamCashflow(
                stake = BigDecimal("10000"),
                odds = BigDecimal("1.89"),
                settlementResult = "lose"
            )
        )
        assertMoneyEquals(
            "-5000",
            calculator.calculateDownstreamCashflow(
                stake = BigDecimal("10000"),
                odds = BigDecimal("1.89"),
                settlementResult = "lose_half"
            )
        )
        assertMoneyEquals(
            "0",
            calculator.calculateDownstreamCashflow(
                stake = BigDecimal("10000"),
                odds = BigDecimal("1.89"),
                settlementResult = "push"
            )
        )
    }

    @Test
    fun `summary uses confirmed downstream odds when results are present`() {
        val summary = calculator.buildDailySummary(
            accounts = emptyList(),
            wagers = emptyList(),
            whatsappOrders = listOf(
                order("UP-1", "upstream", "valid", amount = "10000", odds = "1.87", settlementResult = "win"),
                order(
                    "DOWN-1",
                    "downstream",
                    "valid",
                    amount = "10000",
                    odds = "1.89",
                    groupId = 1,
                    settlementResult = "win"
                )
            ),
            differences = 0,
            whatsappGroups = listOf(
                BookkeepingCalculatorWhatsappGroup(id = 1, rebatePoints = BigDecimal("5"))
            )
        )

        assertMoneyEquals("200", summary.todayProfit)
        assertMoneyEquals("200", summary.waterLossAmount)
        assertMoneyEquals("200", summary.companyNetProfit)
    }

    @Test
    fun `prematch summary ignores rolling orders`() {
        val summary = calculator.buildDailySummary(
            accounts = emptyList(),
            wagers = emptyList(),
            whatsappOrders = listOf(
                order("UP-1", "upstream", "valid", amount = "20000"),
                order("DOWN-1", "downstream", "valid", amount = "12000"),
                order("ROLL-1", "rolling", "valid", amount = "99999", odds = "0.83", settlementResult = "win")
            ),
            differences = 0
        )

        assertEquals(2, summary.whatsappOrderCount)
        assertMoneyEquals("20000", summary.upstreamTotalStake)
        assertMoneyEquals("12000", summary.downstreamTotalStake)
        assertMoneyEquals("8000", summary.todayProfit)
        assertMoneyEquals("0", summary.rollingGroupStake)
    }

    @Test
    fun `prematch summary keeps unsettled orders when other orders are settled`() {
        val summary = calculator.buildDailySummary(
            accounts = emptyList(),
            wagers = emptyList(),
            whatsappOrders = listOf(
                order("UP-SETTLED", "upstream", "valid", amount = "10000", odds = "1.87", settlementResult = "win"),
                order(
                    "DOWN-SETTLED",
                    "downstream",
                    "valid",
                    amount = "10000",
                    odds = "1.89",
                    groupId = 1,
                    settlementResult = "win"
                ),
                order("UP-PENDING", "upstream", "valid", amount = "20000"),
                order("DOWN-PENDING", "downstream", "valid", amount = "15000")
            ),
            differences = 0,
            whatsappGroups = listOf(
                BookkeepingCalculatorWhatsappGroup(id = 1, rebatePoints = BigDecimal("5"))
            )
        )

        assertMoneyEquals("200", summary.upstreamCashflow.add(summary.downstreamCashflow))
        assertMoneyEquals("5200", summary.todayProfit)
        assertMoneyEquals("5200", summary.companyNetProfit)
    }

    @Test
    fun `summary calculates company follow amount from downstream exposure over upstream stake`() {
        val summary = calculator.buildDailySummary(
            accounts = emptyList(),
            wagers = emptyList(),
            whatsappOrders = listOf(
                order("UP-FOLLOW", "upstream", "valid", amount = "40000", odds = "1.87", settlementResult = "赢"),
                order("DOWN-FOLLOW-A", "downstream", "valid", amount = "10000", odds = "1.89", settlementResult = "赢"),
                order("DOWN-FOLLOW-B", "downstream", "valid", amount = "35000", odds = "1.86", settlementResult = "赢")
            ),
            differences = 0
        )

        assertMoneyEquals("45000", summary.downstreamTotalStake)
        assertMoneyEquals("5000", summary.companyFollowAmount)
    }

    @Test
    fun `summary calculates company follow amount per order without offsetting other orders`() {
        val summary = calculator.buildDailySummary(
            accounts = emptyList(),
            wagers = emptyList(),
            whatsappOrders = listOf(
                order("UP-OVER", "upstream", "valid", amount = "40000", matchName = "诺丁汉 vs 曼城", settlementResult = "赢"),
                order("DOWN-OVER-A", "downstream", "valid", amount = "20000", matchName = "诺丁汉 vs 曼城", settlementResult = "赢"),
                order("DOWN-OVER-B", "downstream", "valid", amount = "25000", matchName = "诺丁汉 vs 曼城", settlementResult = "赢"),
                order("UP-UNDER", "upstream", "valid", amount = "40000", matchName = "清水心跳 vs 大阪樱花", settlementResult = "输"),
                order("DOWN-UNDER", "downstream", "valid", amount = "35000", matchName = "清水心跳 vs 大阪樱花", settlementResult = "输")
            ),
            differences = 0
        )

        assertMoneyEquals("80000", summary.upstreamTotalStake)
        assertMoneyEquals("80000", summary.downstreamTotalStake)
        assertMoneyEquals("5000", summary.companyFollowAmount)
    }

    @Test
    fun `summary keeps water profit inside company net profit when downstream covers upstream`() {
        val summary = calculator.buildDailySummary(
            accounts = emptyList(),
            wagers = emptyList(),
            whatsappOrders = listOf(
                order("UP-2", "upstream", "valid", amount = "10000", odds = "1.84", settlementResult = "win"),
                order(
                    "DOWN-2",
                    "downstream",
                    "valid",
                    amount = "10000",
                    odds = "1.89",
                    groupId = 1,
                    settlementResult = "win"
                )
            ),
            differences = 0,
            whatsappGroups = listOf(
                BookkeepingCalculatorWhatsappGroup(id = 1, rebatePoints = BigDecimal("2"))
            )
        )

        assertMoneyEquals("500", summary.waterLossAmount)
        assertMoneyEquals("500", summary.companyNetProfit)
    }

    @Test
    fun `summary counts settled unsettled and cancelled wagers correctly`() {
        val summary = calculator.buildDailySummary(
            accounts = listOf(
                BookkeepingCalculatorAccount("success"),
                BookkeepingCalculatorAccount("failed"),
                BookkeepingCalculatorAccount(null)
            ),
            wagers = listOf(
                wager("T-1", "settled", stake = "100.00", winLoss = "12.50"),
                wager("T-2", "unsettled", stake = "80.00", winLoss = "0.00"),
                wager("T-3", "cancelled", stake = "40.00", winLoss = "0.00")
            ),
            whatsappOrders = listOf(
                order("W-1", "upstream", "valid", amount = "100.00"),
                order("W-2", "downstream", "valid", amount = "80.00"),
                order("W-5", "company_follow", "valid", amount = "20.00"),
                order("W-3", "upstream", "cancelled", amount = "40.00"),
                order("W-4", "upstream", "suspicious", amount = "10.00")
            ),
            differences = 2
        )

        assertEquals(3, summary.crownAccountTotal)
        assertEquals(1, summary.crownSuccessCount)
        assertEquals(1, summary.crownFailedCount)
        assertEquals(1, summary.crownUntestedCount)
        assertEquals(BigDecimal("180.00"), summary.crownTurnover)
        assertEquals(BigDecimal("12.50"), summary.settledWinLoss)
        assertEquals(BigDecimal("80.00"), summary.unsettledAmount)
        assertEquals(5, summary.whatsappOrderCount)
        assertEquals(1, summary.upstreamValidCount)
        assertEquals(1, summary.downstreamValidCount)
        assertEquals(1, summary.companyFollowCount)
        assertEquals(BigDecimal("0.00"), summary.companyFollowAmount)
        assertEquals(1, summary.suspiciousCount)
        assertEquals(2, summary.differenceCount)
        assertEquals(BigDecimal("32.50"), summary.todayProfit)
    }

    @Test
    fun `rolling summary uses crown wagers only`() {
        val summary = calculator.buildRollingSummary(
            accounts = listOf(BookkeepingCalculatorAccount("success")),
            wagers = listOf(
                wager("ROLL-1", "settled", stake = "100.00", winLoss = "12.00"),
                wager("ROLL-2", "unsettled", stake = "80.00", winLoss = "0.00"),
                wager("ROLL-3", "cancelled", stake = "40.00", winLoss = "0.00")
            )
        )

        assertEquals(1, summary.crownAccountTotal)
        assertEquals(1, summary.crownSuccessCount)
        assertEquals(BigDecimal("180.00"), summary.crownTurnover)
        assertEquals(BigDecimal("12.00"), summary.settledWinLoss)
        assertEquals(BigDecimal("80.00"), summary.unsettledAmount)
        assertEquals(0, summary.whatsappOrderCount)
        assertEquals(0, summary.upstreamValidCount)
        assertEquals(0, summary.downstreamValidCount)
        assertEquals(0, summary.companyFollowCount)
        assertEquals(BigDecimal.ZERO, summary.companyFollowAmount)
        assertEquals(BigDecimal("12.00"), summary.todayProfit)
    }

    @Test
    fun `rolling summary ignores prematch orders`() {
        val summary = calculator.buildRollingSummary(
            accounts = emptyList(),
            wagers = listOf(wager("ROLL-1", "settled", stake = "10000", winLoss = "8300")),
            whatsappOrders = listOf(
                order("ROLL-ORDER", "rolling", "valid", amount = "10000", odds = "0.83", settlementResult = "win"),
                order("UP-ORDER", "upstream", "valid", amount = "50000", odds = "1.87", settlementResult = "win")
            )
        )

        assertEquals(1, summary.whatsappOrderCount)
        assertMoneyEquals("10000", summary.rollingGroupStake)
        assertMoneyEquals("8300", summary.rollingGroupSettlement)
        assertMoneyEquals("0", summary.rollingProfitDiff)
    }

    @Test
    fun `rolling summary accepts explicit rolling upstream downstream and company orders`() {
        val summary = calculator.buildRollingSummary(
            accounts = emptyList(),
            wagers = listOf(wager("ROLL-1", "settled", stake = "60000", winLoss = "108000")),
            whatsappOrders = listOf(
                order("ROLL-UP", "rolling_upstream", "valid", amount = "10000", odds = "1.80", settlementResult = "win"),
                order("ROLL-DOWN", "rolling_downstream", "valid", amount = "20000", odds = "1.80", settlementResult = "win"),
                order("ROLL-COMPANY", "rolling_company", "valid", amount = "30000", odds = "1.80", settlementResult = "win"),
                order("PRE-UP", "upstream", "valid", amount = "99999", odds = "1.80", settlementResult = "win")
            )
        )

        assertEquals(3, summary.whatsappOrderCount)
        assertMoneyEquals("60000", summary.rollingGroupStake)
        assertMoneyEquals("108000", summary.rollingGroupSettlement)
        assertMoneyEquals("0", summary.rollingProfitDiff)
    }

    @Test
    fun `rolling summary exposes upstream downstream cashflow and water amount`() {
        val summary = calculator.buildRollingSummary(
            accounts = emptyList(),
            wagers = emptyList(),
            whatsappOrders = listOf(
                order("ROLL-UP", "rolling_upstream", "valid", amount = "10000", odds = "1.80", settlementResult = "win"),
                order("ROLL-DOWN", "rolling_downstream", "valid", amount = "20000", odds = "1.80", settlementResult = "win")
            )
        )

        assertMoneyEquals("-18000", summary.upstreamCashflow)
        assertMoneyEquals("36000", summary.downstreamCashflow)
        assertMoneyEquals("18000", summary.waterLossAmount)
    }

    @Test
    fun `reconciliation finds missing and mismatched orders`() {
        val results = calculator.reconcile(
            wagers = listOf(
                wager("T-1", "settled", stake = "100.00", winLoss = "10.00"),
                wager("T-2", "settled", stake = "50.00", winLoss = "-5.00", home = "曼联", away = "切尔西")
            ),
            whatsappOrders = listOf(
                order("W-1", "upstream", "valid", amount = "100.00"),
                order("W-2", "upstream", "valid", amount = "60.00", matchName = "曼彻斯特联 vs 切尔西"),
                order("W-3", "upstream", "valid", amount = "30.00", matchName = "拜仁 vs 多特蒙德")
            ),
            aliases = mapOf("曼联" to "曼彻斯特联")
        )

        assertEquals(listOf("matched", "amount_mismatch", "missing_crown"), results.map { it.issueType })
        assertEquals(BigDecimal("10.00"), results[1].amountDiff)
        assertEquals("W-3", results[2].whatsappOrderKey)
    }

    private fun wager(
        ticketId: String,
        status: String,
        stake: String,
        winLoss: String,
        league: String = "英超",
        home: String = "曼联",
        away: String = "切尔西",
        market: String = "让球",
        selection: String = "主 -0.5",
        odds: String = "1.95"
    ) = BookkeepingCalculatorCrownWager(
        id = ticketId.hashCode().toLong(),
        ticketId = ticketId,
        leagueName = league,
        homeTeam = home,
        awayTeam = away,
        marketType = market,
        selectionName = selection,
        oddsValue = BigDecimal(odds),
        stakeAmount = BigDecimal(stake),
        winLossAmount = BigDecimal(winLoss),
        status = status
    )

    private fun order(
        orderKey: String,
        direction: String,
        parseStatus: String,
        amount: String,
        league: String = "英超",
        matchName: String = "曼联 vs 切尔西",
        marketText: String = "让球 主 -0.5",
        odds: String = "1.95",
        groupId: Long? = null,
        settlementResult: String? = null
    ) = BookkeepingCalculatorWhatsappOrder(
        id = orderKey.hashCode().toLong(),
        groupId = groupId,
        orderKey = orderKey,
        direction = direction,
        parseStatus = parseStatus,
        leagueName = league,
        matchName = matchName,
        marketText = marketText,
        oddsValue = BigDecimal(odds),
        amount = BigDecimal(amount),
        settlementResult = settlementResult
    )

    private fun assertMoneyEquals(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected but was $actual")
    }
}
