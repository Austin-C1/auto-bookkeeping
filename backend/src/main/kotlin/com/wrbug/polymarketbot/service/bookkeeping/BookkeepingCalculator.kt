package com.wrbug.polymarketbot.service.bookkeeping

import org.springframework.stereotype.Component
import java.math.BigDecimal

data class BookkeepingCalculatorAccount(
    val lastLoginStatus: String?
)

data class BookkeepingCalculatorWhatsappGroup(
    val id: Long?,
    val rebatePoints: BigDecimal = BigDecimal.ZERO
)

data class BookkeepingCalculatorCrownWager(
    val id: Long?,
    val ticketId: String,
    val leagueName: String?,
    val homeTeam: String?,
    val awayTeam: String?,
    val marketType: String?,
    val selectionName: String?,
    val oddsValue: BigDecimal?,
    val stakeAmount: BigDecimal,
    val winLossAmount: BigDecimal,
    val status: String
)

data class BookkeepingCalculatorWhatsappOrder(
    val id: Long?,
    val groupId: Long? = null,
    val orderKey: String,
    val direction: String,
    val parseStatus: String,
    val leagueName: String?,
    val matchName: String?,
    val marketText: String?,
    val oddsValue: BigDecimal?,
    val amount: BigDecimal?,
    val settlementResult: String? = null
)

data class BookkeepingDailySummary(
    val crownAccountTotal: Int,
    val crownSuccessCount: Int,
    val crownFailedCount: Int,
    val crownManualCount: Int,
    val crownUntestedCount: Int,
    val crownTurnover: BigDecimal,
    val settledWinLoss: BigDecimal,
    val unsettledAmount: BigDecimal,
    val whatsappOrderCount: Int,
    val upstreamValidCount: Int,
    val downstreamValidCount: Int,
    val companyFollowCount: Int,
    val companyFollowAmount: BigDecimal,
    val suspiciousCount: Int,
    val cancelledCount: Int,
    val differenceCount: Int,
    val todayProfit: BigDecimal,
    val upstreamTotalStake: BigDecimal = BigDecimal.ZERO,
    val downstreamTotalStake: BigDecimal = BigDecimal.ZERO,
    val downstreamRebateAmount: BigDecimal = BigDecimal.ZERO,
    val upstreamCashflow: BigDecimal = BigDecimal.ZERO,
    val downstreamCashflow: BigDecimal = BigDecimal.ZERO,
    val waterLossAmount: BigDecimal = BigDecimal.ZERO,
    val grossProfit: BigDecimal = BigDecimal.ZERO,
    val companyNetProfit: BigDecimal = BigDecimal.ZERO,
    val rollingGroupStake: BigDecimal = BigDecimal.ZERO,
    val rollingGroupSettlement: BigDecimal = BigDecimal.ZERO,
    val rollingProfitDiff: BigDecimal = BigDecimal.ZERO
)

data class BookkeepingReconciliationItem(
    val issueType: String,
    val matchStatus: String,
    val crownWagerId: Long?,
    val crownTicketId: String?,
    val whatsappOrderId: Long?,
    val whatsappOrderKey: String?,
    val amountDiff: BigDecimal?,
    val oddsDiff: BigDecimal?,
    val profitAmount: BigDecimal,
    val notes: String
)

@Component
class BookkeepingCalculator {
    fun buildDailySummary(
        accounts: List<BookkeepingCalculatorAccount>,
        wagers: List<BookkeepingCalculatorCrownWager>,
        whatsappOrders: List<BookkeepingCalculatorWhatsappOrder>,
        differences: Int,
        whatsappGroups: List<BookkeepingCalculatorWhatsappGroup> = emptyList()
    ): BookkeepingDailySummary {
        val effectiveWagers = wagers.filter { it.isEffective() }
        val settledWagers = effectiveWagers.filter { it.isSettled() }
        val unsettledWagers = effectiveWagers.filterNot { it.isSettled() }
        val effectiveWhatsapp = whatsappOrders.filter { it.isValidOrder() }
        val upstreamTotal = effectiveWhatsapp
            .filter { it.direction.equals("upstream", ignoreCase = true) }
            .sumMoney { it.amount ?: BigDecimal.ZERO }
        val downstreamTotal = effectiveWhatsapp
            .filter { it.direction.equals("downstream", ignoreCase = true) }
            .sumMoney { it.amount ?: BigDecimal.ZERO }
        val companyFollowOrders = effectiveWhatsapp
            .filter { it.direction.equals("company_follow", ignoreCase = true) }
        val companyFollowTotal = companyFollowOrders.sumMoney { it.amount ?: BigDecimal.ZERO }
        val downstreamExposureTotal = downstreamTotal.add(companyFollowTotal)
        val companyFollowExposure = calculateCompanyFollowExposure(effectiveWhatsapp)
        val settledWinLoss = settledWagers.sumMoney { it.winLossAmount }
        val groupById = whatsappGroups.mapNotNull { group -> group.id?.let { it to group } }.toMap()
        val hasSettlementResult = effectiveWhatsapp.any { it.settlementResult.toSettlementOutcome() != null }
        val upstreamCashflow = if (hasSettlementResult) {
            effectiveWhatsapp
                .filter { it.direction.equals("upstream", ignoreCase = true) }
                .sumMoney { order ->
                val stake = order.amount ?: BigDecimal.ZERO
                val odds = order.oddsValue ?: BigDecimal.ZERO
                calculateUpstreamCashflow(stake, odds, order.settlementResult)
            }
        } else {
            BigDecimal.ZERO
        }
        val downstreamCashflow = if (hasSettlementResult) {
            effectiveWhatsapp
                .filter {
                    it.direction.equals("downstream", ignoreCase = true) ||
                        it.direction.equals("company_follow", ignoreCase = true)
                }
                .sumMoney { order ->
                    val stake = order.amount ?: BigDecimal.ZERO
                    val odds = order.oddsValue ?: BigDecimal.ZERO
                    val rebatePoints = groupById[order.groupId]?.rebatePoints ?: BigDecimal.ZERO
                    calculateDownstreamCashflow(stake, odds, rebatePoints, order.settlementResult)
                }
        } else {
            BigDecimal.ZERO
        }
        val downstreamRebateAmount = if (hasSettlementResult) {
            effectiveWhatsapp
                .filter {
                    it.direction.equals("downstream", ignoreCase = true) ||
                        it.direction.equals("company_follow", ignoreCase = true)
                }
                .sumMoney { order ->
                    val stake = order.amount ?: BigDecimal.ZERO
                    val rebatePoints = groupById[order.groupId]?.rebatePoints ?: BigDecimal.ZERO
                    calculateDownstreamRebateAmount(stake, rebatePoints, order.settlementResult)
                }
        } else {
            BigDecimal.ZERO
        }
        val grossProfit = if (hasSettlementResult) {
            upstreamCashflow.add(downstreamCashflow)
        } else {
            upstreamTotal.subtract(downstreamTotal).add(settledWinLoss)
        }
        val waterLossAmount = if (hasSettlementResult) {
            calculateWaterLossAmount(upstreamCashflow, downstreamCashflow)
        } else {
            BigDecimal.ZERO
        }
        val companyNetProfit = grossProfit

        return BookkeepingDailySummary(
            crownAccountTotal = accounts.size,
            crownSuccessCount = accounts.count { it.lastLoginStatus == "success" },
            crownFailedCount = accounts.count { it.lastLoginStatus == "failed" },
            crownManualCount = accounts.count { it.lastLoginStatus == "manual_required" },
            crownUntestedCount = accounts.count { it.lastLoginStatus.isNullOrBlank() },
            crownTurnover = effectiveWagers.sumMoney { it.stakeAmount },
            settledWinLoss = settledWinLoss,
            unsettledAmount = unsettledWagers.sumMoney { it.stakeAmount },
            whatsappOrderCount = whatsappOrders.size,
            upstreamValidCount = effectiveWhatsapp.count { it.direction.equals("upstream", ignoreCase = true) },
            downstreamValidCount = effectiveWhatsapp.count { it.direction.equals("downstream", ignoreCase = true) },
            companyFollowCount = companyFollowOrders.size,
            companyFollowAmount = companyFollowExposure,
            suspiciousCount = whatsappOrders.count { it.parseStatus.equals("suspicious", ignoreCase = true) },
            cancelledCount = whatsappOrders.count { it.parseStatus.isCancelled() },
            differenceCount = differences,
            todayProfit = companyNetProfit,
            upstreamTotalStake = upstreamTotal,
            downstreamTotalStake = downstreamExposureTotal,
            downstreamRebateAmount = downstreamRebateAmount,
            upstreamCashflow = upstreamCashflow,
            downstreamCashflow = downstreamCashflow,
            waterLossAmount = waterLossAmount,
            grossProfit = grossProfit,
            companyNetProfit = companyNetProfit
        )
    }

    fun buildRollingSummary(
        accounts: List<BookkeepingCalculatorAccount>,
        wagers: List<BookkeepingCalculatorCrownWager>,
        whatsappOrders: List<BookkeepingCalculatorWhatsappOrder> = emptyList(),
        differences: Int = 0
    ): BookkeepingDailySummary {
        val effectiveWagers = wagers.filter { it.isEffective() }
        val settledWagers = effectiveWagers.filter { it.isSettled() }
        val unsettledWagers = effectiveWagers.filterNot { it.isSettled() }
        val settledWinLoss = settledWagers.sumMoney { it.winLossAmount }
        val effectiveRollingOrders = whatsappOrders
            .filter { it.isValidOrder() && it.direction.equals("rolling", ignoreCase = true) }
        val hasRollingSettlementResult = effectiveRollingOrders.any { it.settlementResult.toSettlementOutcome() != null }
        val rollingGroupStake = effectiveRollingOrders.sumMoney { it.amount ?: BigDecimal.ZERO }
        val rollingGroupSettlement = if (hasRollingSettlementResult) {
            effectiveRollingOrders.sumMoney { order ->
                calculateRollingGroupSettlement(
                    stake = order.amount ?: BigDecimal.ZERO,
                    odds = order.oddsValue ?: BigDecimal.ZERO,
                    settlementResult = order.settlementResult
                )
            }
        } else {
            BigDecimal.ZERO
        }
        val rollingProfitDiff = settledWinLoss.subtract(rollingGroupSettlement)

        return BookkeepingDailySummary(
            crownAccountTotal = accounts.size,
            crownSuccessCount = accounts.count { it.lastLoginStatus == "success" },
            crownFailedCount = accounts.count { it.lastLoginStatus == "failed" },
            crownManualCount = accounts.count { it.lastLoginStatus == "manual_required" },
            crownUntestedCount = accounts.count { it.lastLoginStatus.isNullOrBlank() },
            crownTurnover = effectiveWagers.sumMoney { it.stakeAmount },
            settledWinLoss = settledWinLoss,
            unsettledAmount = unsettledWagers.sumMoney { it.stakeAmount },
            whatsappOrderCount = whatsappOrders.size,
            upstreamValidCount = 0,
            downstreamValidCount = 0,
            companyFollowCount = 0,
            companyFollowAmount = BigDecimal.ZERO,
            suspiciousCount = whatsappOrders.count { it.parseStatus.equals("suspicious", ignoreCase = true) },
            cancelledCount = whatsappOrders.count { it.parseStatus.isCancelled() },
            differenceCount = differences,
            todayProfit = rollingProfitDiff,
            grossProfit = rollingProfitDiff,
            companyNetProfit = rollingProfitDiff,
            rollingGroupStake = rollingGroupStake,
            rollingGroupSettlement = rollingGroupSettlement,
            rollingProfitDiff = rollingProfitDiff
        )
    }

    fun calculateUpstreamCashflow(
        stake: BigDecimal,
        odds: BigDecimal,
        settlementResult: String?
    ): BigDecimal = when (settlementResult.toSettlementOutcome()) {
        SettlementOutcome.WIN -> stake.multiply(odds).negate()
        SettlementOutcome.WIN_HALF -> stake.multiply(odds).multiply(HALF).negate()
        SettlementOutcome.PUSH -> BigDecimal.ZERO
        SettlementOutcome.LOSE_HALF -> stake.multiply(HALF)
        SettlementOutcome.LOSE -> stake
        null -> BigDecimal.ZERO
    }

    fun calculateDownstreamCashflow(
        stake: BigDecimal,
        odds: BigDecimal,
        rebatePoints: BigDecimal,
        settlementResult: String?
    ): BigDecimal {
        val settlementOdds = odds.subtract(rebatePoints.movePointLeft(2)).nonNegative()
        return when (settlementResult.toSettlementOutcome()) {
            SettlementOutcome.WIN -> stake.multiply(settlementOdds)
            SettlementOutcome.WIN_HALF -> stake.multiply(settlementOdds).multiply(HALF)
            SettlementOutcome.PUSH -> BigDecimal.ZERO
            SettlementOutcome.LOSE_HALF -> stake.multiply(HALF).negate()
            SettlementOutcome.LOSE -> stake.negate()
            null -> BigDecimal.ZERO
        }
    }

    fun calculateRollingGroupSettlement(
        stake: BigDecimal,
        odds: BigDecimal,
        settlementResult: String?
    ): BigDecimal = when (settlementResult.toSettlementOutcome()) {
        SettlementOutcome.WIN -> stake.multiply(odds)
        SettlementOutcome.WIN_HALF -> stake.multiply(odds).multiply(HALF)
        SettlementOutcome.PUSH -> BigDecimal.ZERO
        SettlementOutcome.LOSE_HALF -> stake.multiply(HALF).negate()
        SettlementOutcome.LOSE -> stake.negate()
        null -> BigDecimal.ZERO
    }

    fun calculateDownstreamRebateAmount(
        stake: BigDecimal,
        rebatePoints: BigDecimal,
        settlementResult: String?
    ): BigDecimal {
        val pointValue = rebatePoints.movePointLeft(2).nonNegative()
        return when (settlementResult.toSettlementOutcome()) {
            SettlementOutcome.WIN -> stake.multiply(pointValue)
            SettlementOutcome.WIN_HALF -> stake.multiply(pointValue).multiply(HALF)
            SettlementOutcome.PUSH,
            SettlementOutcome.LOSE_HALF,
            SettlementOutcome.LOSE,
            null -> BigDecimal.ZERO
        }
    }

    fun calculateWaterLossAmount(
        upstreamCashflow: BigDecimal,
        downstreamCashflow: BigDecimal
    ): BigDecimal {
        if (upstreamCashflow >= BigDecimal.ZERO || downstreamCashflow <= BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }
        return downstreamCashflow.subtract(upstreamCashflow.abs())
    }

    private fun calculateCompanyFollowExposure(
        orders: List<BookkeepingCalculatorWhatsappOrder>
    ): BigDecimal =
        orders
            .groupBy { it.prematchExposureKey() }
            .values
            .sumMoney { groupOrders ->
                val upstreamStake = groupOrders
                    .filter { it.direction.equals("upstream", ignoreCase = true) }
                    .sumMoney { it.amount ?: BigDecimal.ZERO }
                val downstreamStake = groupOrders
                    .filter {
                        it.direction.equals("downstream", ignoreCase = true) ||
                            it.direction.equals("company_follow", ignoreCase = true)
                    }
                    .sumMoney { it.amount ?: BigDecimal.ZERO }
                downstreamStake.subtract(upstreamStake).nonNegative()
            }

    fun reconcile(
        wagers: List<BookkeepingCalculatorCrownWager>,
        whatsappOrders: List<BookkeepingCalculatorWhatsappOrder>,
        aliases: Map<String, String> = emptyMap()
    ): List<BookkeepingReconciliationItem> {
        val results = mutableListOf<BookkeepingReconciliationItem>()
        val effectiveWagers = wagers.filter { it.isEffective() }
        val effectiveOrders = whatsappOrders.filter { it.isValidOrder() }
        val unmatchedWagers = effectiveWagers.associateBy { it.ticketId }.toMutableMap()

        for (order in effectiveOrders) {
            val matched = unmatchedWagers.values.firstOrNull { wager ->
                wager.reconciliationKey(aliases) == order.reconciliationKey(aliases)
            }

            if (matched == null) {
                results += BookkeepingReconciliationItem(
                    issueType = "missing_crown",
                    matchStatus = "difference",
                    crownWagerId = null,
                    crownTicketId = null,
                    whatsappOrderId = order.id,
                    whatsappOrderKey = order.orderKey,
                    amountDiff = order.amount,
                    oddsDiff = null,
                    profitAmount = BigDecimal.ZERO,
                    notes = "WhatsApp 有单，Crown 没有匹配投注"
                )
                continue
            }

            unmatchedWagers.remove(matched.ticketId)
            val amountDiff = (order.amount ?: BigDecimal.ZERO).subtract(matched.stakeAmount)
            val oddsDiff = if (order.oddsValue != null && matched.oddsValue != null) {
                order.oddsValue.subtract(matched.oddsValue)
            } else {
                null
            }
            val issueType = when {
                amountDiff.compareTo(BigDecimal.ZERO) != 0 -> "amount_mismatch"
                oddsDiff != null && oddsDiff.compareTo(BigDecimal.ZERO) != 0 -> "odds_mismatch"
                else -> "matched"
            }

            results += BookkeepingReconciliationItem(
                issueType = issueType,
                matchStatus = if (issueType == "matched") "matched" else "difference",
                crownWagerId = matched.id,
                crownTicketId = matched.ticketId,
                whatsappOrderId = order.id,
                whatsappOrderKey = order.orderKey,
                amountDiff = if (amountDiff.compareTo(BigDecimal.ZERO) == 0) null else amountDiff.abs(),
                oddsDiff = oddsDiff?.takeIf { it.compareTo(BigDecimal.ZERO) != 0 }?.abs(),
                profitAmount = if (matched.isSettled()) matched.winLossAmount else BigDecimal.ZERO,
                notes = when (issueType) {
                    "amount_mismatch" -> "金额不一致"
                    "odds_mismatch" -> "赔率不一致"
                    else -> "匹配成功"
                }
            )
        }

        for (wager in unmatchedWagers.values) {
            results += BookkeepingReconciliationItem(
                issueType = "missing_whatsapp",
                matchStatus = "difference",
                crownWagerId = wager.id,
                crownTicketId = wager.ticketId,
                whatsappOrderId = null,
                whatsappOrderKey = null,
                amountDiff = wager.stakeAmount,
                oddsDiff = null,
                profitAmount = if (wager.isSettled()) wager.winLossAmount else BigDecimal.ZERO,
                notes = "Crown 有投注，WhatsApp 没有匹配订单"
            )
        }

        return results
    }

    private fun BookkeepingCalculatorCrownWager.reconciliationKey(aliases: Map<String, String>): String =
        listOf(
            leagueName.orEmpty(),
            "${homeTeam.orEmpty()} vs ${awayTeam.orEmpty()}",
            listOf(marketType.orEmpty(), selectionName.orEmpty()).joinToString(" ")
        ).joinToString("|").normaliseForMatch(aliases)

    private fun BookkeepingCalculatorWhatsappOrder.reconciliationKey(aliases: Map<String, String>): String =
        listOf(
            leagueName.orEmpty(),
            matchName.orEmpty(),
            marketText.orEmpty()
        ).joinToString("|").normaliseForMatch(aliases)

    private fun BookkeepingCalculatorWhatsappOrder.prematchExposureKey(): String {
        val normalized = listOf(
            leagueName.orEmpty(),
            matchName.orEmpty(),
            marketText.orEmpty().substringBefore("@")
        ).joinToString("|").normaliseForMatch(emptyMap())
        return normalized.ifBlank { orderKey }
    }

    private fun BookkeepingCalculatorCrownWager.isEffective(): Boolean = !status.isCancelled()

    private fun BookkeepingCalculatorCrownWager.isSettled(): Boolean {
        val text = status.trim().lowercase()
        return text in setOf("settled", "won", "lost", "win", "loss", "已结算", "赢", "输")
    }

    private fun BookkeepingCalculatorWhatsappOrder.isValidOrder(): Boolean {
        val text = parseStatus.trim().lowercase()
        return text in setOf("valid", "parsed", "effective", "confirmed", "有效订单")
    }

    private fun String?.toSettlementOutcome(): SettlementOutcome? {
        val text = this?.trim()?.lowercase().orEmpty()
            .replace("-", "_")
            .replace(" ", "_")
        return when (text) {
            "win", "won", "full_win", "赢", "全赢" -> SettlementOutcome.WIN
            "win_half", "half_win", "halfwon", "赢半", "半赢" -> SettlementOutcome.WIN_HALF
            "push", "draw", "void", "走水", "和" -> SettlementOutcome.PUSH
            "lose_half", "half_lose", "halflost", "输半", "半输" -> SettlementOutcome.LOSE_HALF
            "lose", "lost", "loss", "full_lose", "输", "全输" -> SettlementOutcome.LOSE
            else -> null
        }
    }

    private fun BigDecimal.nonNegative(): BigDecimal =
        if (compareTo(BigDecimal.ZERO) < 0) BigDecimal.ZERO else this

    private fun String.isCancelled(): Boolean {
        val text = trim().lowercase()
        return text in setOf("cancelled", "canceled", "cancel", "void", "refunded", "refund", "取消", "作废", "退单")
    }

    private fun String.normaliseForMatch(aliases: Map<String, String>): String {
        val canonical = aliases.entries
            .sortedByDescending { it.key.length }
            .fold(this) { value, (source, target) ->
                value.replace(source, target, ignoreCase = true)
            }

        return canonical
            .lowercase()
            .replace(Regex("\\b(vs|v|versus)\\b"), "")
            .replace(Regex("[\\s\\-_/|:：,，.。()（）\\[\\]]+"), "")
            .replace("队", "")
    }

    private fun <T> Iterable<T>.sumMoney(selector: (T) -> BigDecimal): BigDecimal =
        fold(BigDecimal.ZERO) { acc, item -> acc.add(selector(item)) }

    private enum class SettlementOutcome {
        WIN,
        WIN_HALF,
        PUSH,
        LOSE_HALF,
        LOSE
    }

    private companion object {
        val HALF: BigDecimal = BigDecimal("0.5")
    }
}
