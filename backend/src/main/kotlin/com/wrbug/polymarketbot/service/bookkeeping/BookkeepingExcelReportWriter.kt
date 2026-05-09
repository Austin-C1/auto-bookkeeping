package com.wrbug.polymarketbot.service.bookkeeping

import com.wrbug.polymarketbot.dto.BookkeepingCrownAccountDto
import com.wrbug.polymarketbot.dto.BookkeepingCrownWagerDto
import com.wrbug.polymarketbot.dto.BookkeepingDailySummaryDto
import com.wrbug.polymarketbot.dto.BookkeepingReconciliationResultDto
import com.wrbug.polymarketbot.dto.BookkeepingWhatsappGroupDto
import com.wrbug.polymarketbot.dto.BookkeepingWhatsappOrderDto
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths

@Component
class BookkeepingExcelReportWriter {
    fun writeDailyReport(
        businessDate: String,
        taskId: Long,
        workspaceType: String = "prematch",
        reportType: String = "daily",
        summary: BookkeepingDailySummaryDto,
        accounts: List<BookkeepingCrownAccountDto>,
        wagers: List<BookkeepingCrownWagerDto>,
        whatsappOrders: List<BookkeepingWhatsappOrderDto>,
        whatsappGroups: List<BookkeepingWhatsappGroupDto> = emptyList(),
        reconciliationResults: List<BookkeepingReconciliationResultDto>
    ): String {
        val outputDir = Paths.get("output", "bookkeeping")
        Files.createDirectories(outputDir)
        val normalizedWorkspaceType = normaliseWorkspaceType(workspaceType)
        val normalizedReportType = normaliseReportType(reportType)
        val reportPath = outputDir.resolve("bookkeeping-$businessDate-$normalizedWorkspaceType-$normalizedReportType-$taskId.xlsx")

        XSSFWorkbook().use { workbook ->
            val headerStyle = workbook.createHeaderStyle()
            when (normalizedReportType) {
                "crown_wagers" -> workbook.addCrownWagersSheet("皇冠注单", wagers, headerStyle)
                "downstream_before_rebate" -> workbook.addDownstreamOrdersBeforeRebateSheet(
                    "下游群注单（退水前）",
                    whatsappOrders.filter { it.direction == "downstream" },
                    whatsappGroups,
                    headerStyle
                )
                "downstream_after_rebate" -> workbook.addDownstreamOrdersAfterRebateSheet(
                    "下游群注单（退水后）",
                    whatsappOrders.filter { it.direction == "downstream" },
                    whatsappGroups,
                    headerStyle
                )
                "upstream_orders" -> workbook.addPrematchBillSheet(
                    "上游群注单",
                    whatsappOrders.filter { it.direction == "upstream" },
                    whatsappGroups,
                    headerStyle
                ) { order, _ ->
                    calculateUpstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
                }
                "company_orders" -> workbook.addPrematchBillSheet(
                    "公司跟单表",
                    whatsappOrders.filter { it.direction == "company_follow" },
                    whatsappGroups,
                    headerStyle
                ) { order, _ ->
                    calculateDownstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
                }
                "prematch_settlement" -> workbook.addSettlementSheet("赛前结算表", businessDate, summary, headerStyle)
                "prematch_profit" -> workbook.addCompanyProfitSheet(
                    "公司盈亏表",
                    businessDate,
                    summary,
                    whatsappOrders.filter { it.direction in setOf("upstream", "downstream", "company_follow") },
                    whatsappGroups,
                    headerStyle
                )
                "rolling_group_orders" -> workbook.addPrematchBillSheet(
                    "滚球群注单",
                    whatsappOrders.filter { it.direction == "rolling" },
                    whatsappGroups,
                    headerStyle
                ) { order, _ ->
                    calculateDownstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
                }
                "rolling_reconcile" -> workbook.addReconciliationSheet("滚球对账表", reconciliationResults, headerStyle)
                "rolling_profit" -> workbook.addRollingProfitSheet("滚球盈亏表", businessDate, summary, headerStyle)
                "prematch_excel" -> workbook.addPrematchWorkbookSheets(
                    businessDate,
                    summary,
                    whatsappOrders,
                    whatsappGroups,
                    reconciliationResults,
                    headerStyle
                )
                "rolling_excel" -> workbook.addRollingWorkbookSheets(
                    businessDate,
                    summary,
                    wagers,
                    whatsappOrders,
                    whatsappGroups,
                    reconciliationResults,
                    headerStyle
                )
                else -> workbook.addDailyWorkbookSheets(
                    businessDate,
                    summary,
                    accounts,
                    wagers,
                    whatsappOrders,
                    reconciliationResults,
                    headerStyle
                )
            }

            Files.newOutputStream(reportPath).use { workbook.write(it) }
        }

        return reportPath.toString()
    }

    private fun XSSFWorkbook.addPrematchWorkbookSheets(
        businessDate: String,
        summary: BookkeepingDailySummaryDto,
        whatsappOrders: List<BookkeepingWhatsappOrderDto>,
        whatsappGroups: List<BookkeepingWhatsappGroupDto>,
        reconciliationResults: List<BookkeepingReconciliationResultDto>,
        headerStyle: CellStyle
    ) {
        addPrematchBillSheet(
            "上游群注单",
            whatsappOrders.filter { it.direction == "upstream" },
            whatsappGroups,
            headerStyle
        ) { order, _ ->
            calculateUpstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
        }
        addDownstreamOrdersBeforeRebateSheet(
            "下游群注单（退水前）",
            whatsappOrders.filter { it.direction == "downstream" },
            whatsappGroups,
            headerStyle
        )
        addDownstreamOrdersAfterRebateSheet(
            "下游群注单（退水后）",
            whatsappOrders.filter { it.direction == "downstream" },
            whatsappGroups,
            headerStyle
        )
        addPrematchBillSheet(
            "公司跟单表",
            whatsappOrders.filter { it.direction == "company_follow" },
            whatsappGroups,
            headerStyle
        ) { order, _ ->
            calculateDownstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
        }
        addSettlementSheet("赛前结算表", businessDate, summary, headerStyle)
        addCompanyProfitSheet(
            "公司盈亏表",
            businessDate,
            summary,
            whatsappOrders.filter { it.direction in setOf("upstream", "downstream", "company_follow") },
            whatsappGroups,
            headerStyle
        )
        addReconciliationSheet("异常订单", reconciliationResults.filter { it.issueType != "matched" }, headerStyle)
    }

    private fun XSSFWorkbook.addRollingWorkbookSheets(
        businessDate: String,
        summary: BookkeepingDailySummaryDto,
        wagers: List<BookkeepingCrownWagerDto>,
        whatsappOrders: List<BookkeepingWhatsappOrderDto>,
        whatsappGroups: List<BookkeepingWhatsappGroupDto>,
        reconciliationResults: List<BookkeepingReconciliationResultDto>,
        headerStyle: CellStyle
    ) {
        addCrownWagersSheet("皇冠注单", wagers, headerStyle)
        addPrematchBillSheet(
            "滚球群注单",
            whatsappOrders.filter { it.direction == "rolling" },
            whatsappGroups,
            headerStyle
        ) { order, _ ->
            calculateDownstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
        }
        addReconciliationSheet("滚球对账表", reconciliationResults, headerStyle)
        addRollingProfitSheet("滚球盈亏表", businessDate, summary, headerStyle)
        addReconciliationSheet("滚球异常表", reconciliationResults.filter { it.issueType != "matched" }, headerStyle)
    }

    private fun XSSFWorkbook.addDailyWorkbookSheets(
        businessDate: String,
        summary: BookkeepingDailySummaryDto,
        accounts: List<BookkeepingCrownAccountDto>,
        wagers: List<BookkeepingCrownWagerDto>,
        whatsappOrders: List<BookkeepingWhatsappOrderDto>,
        reconciliationResults: List<BookkeepingReconciliationResultDto>,
        headerStyle: CellStyle
    ) {
        addSheet(
            "Crown账号池",
            listOf("账号", "账号ID", "域名", "登录状态", "最后测试时间", "启用"),
            accounts.map {
                listOf(it.displayName, it.accountKey, it.baseUrl, it.lastLoginStatus.orEmpty(), it.lastLoginAt, it.enabled)
            },
            headerStyle
        )
        addCrownWagersSheet("皇冠注单", wagers, headerStyle)
        addPrematchBillSheet("上游群注单", whatsappOrders.filter { it.direction == "upstream" }, emptyList(), headerStyle) { order, _ ->
            calculateUpstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
        }
        addDownstreamOrdersBeforeRebateSheet("下游群注单", whatsappOrders.filter { it.direction == "downstream" }, emptyList(), headerStyle)
        addPrematchBillSheet("公司跟单表", whatsappOrders.filter { it.direction == "company_follow" }, emptyList(), headerStyle) { order, _ ->
            calculateDownstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
        }
        addReconciliationSheet("对账结果", reconciliationResults, headerStyle)
        addCompanyProfitSheet(
            "公司盈亏表",
            businessDate,
            summary,
            whatsappOrders.filter { it.direction in setOf("upstream", "downstream", "company_follow") },
            emptyList(),
            headerStyle
        )
    }

    private fun XSSFWorkbook.addCrownWagersSheet(
        name: String,
        wagers: List<BookkeepingCrownWagerDto>,
        headerStyle: CellStyle
    ) {
        addSheet(
            name,
            accountBillHeaders(),
            wagers.mapIndexed { index, wager ->
                listOf(
                    if (index == 0) wager.businessDate else "",
                    index + 1,
                    wager.leagueName,
                    crownMatchName(wager),
                    crownMarketAndOdds(wager),
                    "",
                    wager.stakeAmount,
                    wager.winLossAmount
                )
            },
            headerStyle
        )
    }

    private fun XSSFWorkbook.addWhatsappOrdersSheet(
        name: String,
        orders: List<BookkeepingWhatsappOrderDto>,
        headerStyle: CellStyle
    ) {
        addSheet(
            name,
            listOf("日期", "订单号", "发送人", "联赛", "比赛", "盘口", "赔率", "金额", "赛果", "状态", "原文"),
            orders.map { it.orderRow() },
            headerStyle
        )
    }

    private fun XSSFWorkbook.addDownstreamOrdersBeforeRebateSheet(
        name: String,
        orders: List<BookkeepingWhatsappOrderDto>,
        groups: List<BookkeepingWhatsappGroupDto>,
        headerStyle: CellStyle
    ) {
        addPrematchBillSheet(name, orders, groups, headerStyle) { order, _ ->
                val stake = order.amount ?: BigDecimal.ZERO
                val odds = order.oddsValue ?: BigDecimal.ZERO
                calculateDownstreamSettlement(stake, odds, order.settlementResult)
        }
    }

    private fun XSSFWorkbook.addDownstreamOrdersAfterRebateSheet(
        name: String,
        orders: List<BookkeepingWhatsappOrderDto>,
        groups: List<BookkeepingWhatsappGroupDto>,
        headerStyle: CellStyle
    ) {
        val groupById = groups.mapNotNull { group -> group.id?.let { it to group } }.toMap()
        val adjustedOddsByOrderKey = orders.associate { order ->
            val odds = order.oddsValue ?: BigDecimal.ZERO
            val rebatePoints = groupById[order.groupId]?.rebatePoints ?: BigDecimal.ZERO
            order.orderKey to odds.subtract(rebatePoints.movePointLeft(2)).nonNegative()
        }
        addPrematchBillSheet(
            name,
            orders,
            groups,
            headerStyle,
            oddsResolver = { order -> adjustedOddsByOrderKey[order.orderKey] ?: order.oddsValue }
        ) { order, _ ->
            val stake = order.amount ?: BigDecimal.ZERO
            val adjustedOdds = adjustedOddsByOrderKey[order.orderKey] ?: BigDecimal.ZERO
            calculateDownstreamSettlement(stake, adjustedOdds, order.settlementResult)
        }
    }

    private fun XSSFWorkbook.addPrematchBillSheet(
        name: String,
        orders: List<BookkeepingWhatsappOrderDto>,
        groups: List<BookkeepingWhatsappGroupDto>,
        headerStyle: CellStyle,
        oddsResolver: (BookkeepingWhatsappOrderDto) -> BigDecimal? = { it.oddsValue },
        settlementResolver: (BookkeepingWhatsappOrderDto, Map<Long, BookkeepingWhatsappGroupDto>) -> BigDecimal
    ) {
        val groupById = groups.mapNotNull { group -> group.id?.let { it to group } }.toMap()
        addSheet(
            name,
            accountBillHeaders(),
            orders.mapIndexed { index, order ->
                val odds = oddsResolver(order)
                listOf(
                    if (index == 0) order.businessDate else "",
                    index + 1,
                    order.leagueName,
                    order.matchName,
                    betMarketAndOdds(order.marketText, odds),
                    actualScoreText(order),
                    order.amount,
                    settlementResolver(order, groupById)
                )
            },
            headerStyle
        )
    }

    private fun XSSFWorkbook.addCompanyProfitSheet(
        name: String,
        businessDate: String,
        summary: BookkeepingDailySummaryDto,
        orders: List<BookkeepingWhatsappOrderDto>,
        groups: List<BookkeepingWhatsappGroupDto>,
        headerStyle: CellStyle
    ) {
        val groupById = groups.mapNotNull { group -> group.id?.let { it to group } }.toMap()
        val rows = orders.mapIndexed { index, order ->
            val stake = order.amount ?: BigDecimal.ZERO
            val odds = order.oddsValue ?: BigDecimal.ZERO
            val rebatePoints = groupById[order.groupId]?.rebatePoints ?: BigDecimal.ZERO
            val settlement = if (order.direction == "upstream") {
                calculateUpstreamSettlement(stake, odds, order.settlementResult)
            } else {
                val adjustedOdds = odds.subtract(rebatePoints.movePointLeft(2)).nonNegative()
                calculateDownstreamSettlement(stake, adjustedOdds, order.settlementResult)
            }
            listOf(
                if (index == 0) order.businessDate else "",
                index + 1,
                order.leagueName,
                order.matchName,
                betMarketAndOdds(order.marketText, order.oddsValue),
                actualScoreText(order),
                order.amount,
                settlement,
                orderSourceText(order, groupById)
            )
        } + listOf(
            listOf("", "", "", "", "", "", "盈亏水金额", summary.waterLossAmount, ""),
            listOf(businessDate, "", "", "", "", "", "日盈亏", summary.companyNetProfit, "")
        )

        addSheet(
            name,
            accountBillHeaders() + "订单来源",
            rows,
            headerStyle
        )
    }

    private fun XSSFWorkbook.addSettlementSheet(
        name: String,
        businessDate: String,
        summary: BookkeepingDailySummaryDto,
        headerStyle: CellStyle
    ) {
        addSheet(
            name,
            listOf("日期", "上游结算现金流", "下游结算现金流", "公司跟单额", "盈亏水金额", "公司总盈利"),
            listOf(
                listOf(
                    businessDate,
                    summary.upstreamCashflow,
                    summary.downstreamCashflow,
                    summary.companyFollowAmount,
                    summary.waterLossAmount,
                    summary.companyNetProfit
                )
            ),
            headerStyle
        )
    }

    private fun XSSFWorkbook.addProfitSheet(
        name: String,
        businessDate: String,
        summary: BookkeepingDailySummaryDto,
        headerStyle: CellStyle
    ) {
        addSheet(
            name,
            listOf("日期", "上游总投注额", "下游总投放额", "公司跟单额", "公司毛利润", "盈亏水金额", "公司总盈利", "异常笔数"),
            listOf(
                listOf(
                    businessDate,
                    summary.upstreamTotalStake,
                    summary.downstreamTotalStake,
                    summary.companyFollowAmount,
                    summary.grossProfit,
                    summary.waterLossAmount,
                    summary.companyNetProfit,
                    summary.differenceCount
                )
            ),
            headerStyle
        )
    }

    private fun XSSFWorkbook.addRollingProfitSheet(
        name: String,
        businessDate: String,
        summary: BookkeepingDailySummaryDto,
        headerStyle: CellStyle
    ) {
        addSheet(
            name,
            listOf("日期", "皇冠总盈亏", "滚球群总盈亏", "对账差额", "公司滚球利润"),
            listOf(
                listOf(
                    businessDate,
                    summary.settledWinLoss,
                    summary.rollingGroupSettlement,
                    summary.rollingProfitDiff,
                    summary.companyNetProfit
                )
            ),
            headerStyle
        )
    }

    private fun XSSFWorkbook.addReconciliationSheet(
        name: String,
        reconciliationResults: List<BookkeepingReconciliationResultDto>,
        headerStyle: CellStyle
    ) {
        addSheet(
            name,
            listOf("状态", "问题", "Crown投注ID", "WhatsApp订单ID", "金额差异", "赔率差异", "利润", "说明"),
            reconciliationResults.map {
                listOf(
                    it.matchStatus,
                    it.issueType,
                    it.crownWagerId,
                    it.whatsappOrderId,
                    it.amountDiff,
                    it.oddsDiff,
                    it.profitAmount,
                    it.notes
                )
            },
            headerStyle
        )
    }

    private fun XSSFWorkbook.createHeaderStyle(): CellStyle {
        val style = createCellStyle()
        val font = createFont()
        font.bold = true
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        return style
    }

    private fun XSSFWorkbook.addSheet(
        name: String,
        headers: List<String>,
        rows: List<List<Any?>>,
        headerStyle: CellStyle
    ) {
        val sheet = createSheet(name)
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.setCellStyle(headerStyle)
        }

        rows.forEachIndexed { rowIndex, values ->
            val row = sheet.createRow(rowIndex + 1)
            values.forEachIndexed { cellIndex, value ->
                row.createCell(cellIndex).setAnyValue(value)
            }
        }

        headers.indices.forEach { sheet.autoSizeColumn(it) }
    }

    private fun BookkeepingWhatsappOrderDto.orderRow(): List<Any?> = listOf(
        businessDate,
        orderKey,
        senderName,
        leagueName,
        matchName,
        marketText,
        oddsValue,
        amount,
        settlementResult,
        parseStatus,
        rawMessage
    )

    private fun accountBillHeaders(): List<String> =
        listOf("日期", "序号", "联赛类型", "比赛队伍", "投注盘口及赔率", "实际比分", "投注额度", "盈亏情况")

    private fun betMarketAndOdds(marketText: String?, odds: BigDecimal?): String {
        val market = marketText?.trim().orEmpty()
        val oddsText = odds?.stripTrailingZeros()?.toPlainString().orEmpty()
        return when {
            market.isNotEmpty() && oddsText.isNotEmpty() -> "$market @ $oddsText"
            market.isNotEmpty() -> market
            else -> oddsText
        }
    }

    private fun crownMatchName(wager: BookkeepingCrownWagerDto): String =
        listOf(wager.homeTeam, wager.awayTeam)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" vs ")

    private fun crownMarketAndOdds(wager: BookkeepingCrownWagerDto): String {
        val market = listOf(wager.marketType, wager.selectionName)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" ")
        return betMarketAndOdds(market, wager.oddsValue)
    }

    private fun actualScoreText(order: BookkeepingWhatsappOrderDto): String {
        return Regex("(?:实际比分|完场比分|全场比分)\\s*[:：]?\\s*(\\d+\\s*[-:：]\\s*\\d+)")
            .find(order.rawMessage)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("\\s+"), "")
            .orEmpty()
    }

    private fun orderSourceText(
        order: BookkeepingWhatsappOrderDto,
        groups: Map<Long, BookkeepingWhatsappGroupDto>
    ): String {
        val sourceGroup = order.groupId?.let { groups[it]?.displayName }?.takeIf { it.isNotBlank() }
        val direction = when (order.direction) {
            "upstream" -> "上游群"
            "downstream" -> "下游群"
            "company_follow" -> "公司跟单"
            "rolling" -> "滚球群"
            else -> order.direction
        }
        return listOf(direction, sourceGroup).filter { !it.isNullOrBlank() }.joinToString(" - ")
    }

    private fun calculateUpstreamSettlement(
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

    private fun calculateDownstreamSettlement(
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

    private fun calculateDownstreamRebateAmount(
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

    private fun org.apache.poi.ss.usermodel.Cell.setAnyValue(value: Any?) {
        when (value) {
            null -> setCellValue("")
            is BigDecimal -> setCellValue(value.stripTrailingZeros().toPlainString())
            is Number -> setCellValue(value.toDouble())
            is Boolean -> setCellValue(if (value) "是" else "否")
            else -> setCellValue(value.toString())
        }
    }

    private fun normaliseWorkspaceType(value: String): String = value.trim().lowercase().ifEmpty { "prematch" }

    private fun normaliseReportType(value: String): String = value.trim().lowercase().ifEmpty { "daily" }

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
