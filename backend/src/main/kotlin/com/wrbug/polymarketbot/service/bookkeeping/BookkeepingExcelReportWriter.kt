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
import org.apache.poi.ss.util.WorkbookUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths

@Component
class BookkeepingExcelReportWriter(
    private val scoreResultReader: BookkeepingTitan007ScoreResultReader = BookkeepingTitan007ScoreResultReader()
) {
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
            val scoreLookup = scoreResultReader.loadScoreLookup(businessDate)
            when (normalizedReportType) {
                "crown_wagers" -> workbook.addCrownWagersSheet("皇冠注单", wagers, headerStyle, scoreLookup)
                "downstream_orders" -> workbook.addDownstreamOrdersGroupSheets(
                    "下游群注单",
                    whatsappOrders.filter { it.direction == "downstream" },
                    whatsappGroups,
                    headerStyle,
                    scoreLookup,
                    groupFilter = { it.role == "downstream" }
                )
                "upstream_orders" -> workbook.addPrematchGroupBillSheets(
                    "上游群注单",
                    whatsappOrders.filter { it.direction == "upstream" },
                    whatsappGroups,
                    headerStyle,
                    scoreLookup,
                    groupFilter = { it.role == "upstream" }
                ) { order, _ ->
                    calculateUpstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
                }
                "company_orders" -> workbook.addPrematchBillSheet(
                    "公司跟单表",
                    whatsappOrders.filter { it.direction == "company_follow" },
                    whatsappGroups,
                    headerStyle,
                    scoreLookup
                ) { order, _ ->
                    calculateDownstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
                }
                "prematch_settlement" -> workbook.addSettlementSheet("赛前结算表", businessDate, summary, headerStyle)
                "prematch_profit" -> workbook.addPrematchCompanyProfitWorkbookSheets(
                    businessDate,
                    summary,
                    whatsappOrders.filter { it.direction in setOf("upstream", "downstream", "company_follow") },
                    whatsappGroups,
                    headerStyle,
                    scoreLookup
                )
                "rolling_group_orders" -> workbook.addPrematchBillSheet(
                    "滚球群注单",
                    whatsappOrders.filter { it.isRollingDirection() },
                    whatsappGroups,
                    headerStyle,
                    scoreLookup
                ) { order, _ ->
                    calculateDownstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
                }
                "rolling_upstream_orders" -> workbook.addPrematchGroupBillSheets(
                    "滚球上游各群表格",
                    whatsappOrders.filter { it.isRollingUpstreamDirection() },
                    whatsappGroups,
                    headerStyle,
                    scoreLookup,
                    groupFilter = { it.role == "rolling_upstream" }
                ) { order, _ ->
                    calculateUpstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
                }
                "rolling_downstream_orders" -> workbook.addPrematchGroupBillSheets(
                    "滚球下游各群表格",
                    whatsappOrders.filter { it.isRollingDownstreamDirection() },
                    whatsappGroups,
                    headerStyle,
                    scoreLookup,
                    groupFilter = { it.role == "rolling" || it.role == "rolling_downstream" }
                ) { order, _ ->
                    calculateDownstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
                }
                "rolling_water" -> workbook.addSettlementSheet("盈亏水表格", businessDate, summary, headerStyle)
                "rolling_reconcile" -> workbook.addReconciliationSheet("滚球对账表", reconciliationResults, headerStyle)
                "rolling_profit" -> workbook.addRollingProfitSheet("滚球盈亏表", businessDate, summary, headerStyle)
                "prematch_excel" -> workbook.addPrematchWorkbookSheets(
                    businessDate,
                    summary,
                    whatsappOrders,
                    whatsappGroups,
                    reconciliationResults,
                    headerStyle,
                    scoreLookup
                )
                "rolling_excel" -> workbook.addRollingWorkbookSheets(
                    businessDate,
                    summary,
                    wagers,
                    whatsappOrders,
                    whatsappGroups,
                    reconciliationResults,
                    headerStyle,
                    scoreLookup
                )
                else -> workbook.addDailyWorkbookSheets(
                    businessDate,
                    summary,
                    accounts,
                    wagers,
                    whatsappOrders,
                    reconciliationResults,
                    headerStyle,
                    scoreLookup
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
        headerStyle: CellStyle,
        scoreLookup: BookkeepingTitan007ScoreLookup
    ) {
        addPrematchBillSheet(
            "上游群注单",
            whatsappOrders.filter { it.direction == "upstream" },
            whatsappGroups,
            headerStyle,
            scoreLookup
        ) { order, _ ->
            calculateUpstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
        }
        addDownstreamOrdersSheet(
            "下游群注单",
            whatsappOrders.filter { it.direction == "downstream" },
            whatsappGroups,
            headerStyle,
            scoreLookup
        )
        addPrematchBillSheet(
            "公司跟单表",
            whatsappOrders.filter { it.direction == "company_follow" },
            whatsappGroups,
            headerStyle,
            scoreLookup
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
            headerStyle,
            scoreLookup
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
        headerStyle: CellStyle,
        scoreLookup: BookkeepingTitan007ScoreLookup
    ) {
        addCrownWagersSheet("皇冠注单", wagers, headerStyle, scoreLookup)
        addPrematchBillSheet(
            "滚球群注单",
            whatsappOrders.filter { it.isRollingDirection() },
            whatsappGroups,
            headerStyle,
            scoreLookup
        ) { order, _ ->
            calculateDownstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
        }
        addReconciliationSheet("滚球对账表", reconciliationResults, headerStyle)
        addRollingProfitSheet("滚球盈亏表", businessDate, summary, headerStyle)
        addReconciliationSheet("滚球异常表", reconciliationResults.filter { it.issueType != "matched" }, headerStyle)
    }

    private fun XSSFWorkbook.addPrematchCompanyProfitWorkbookSheets(
        businessDate: String,
        summary: BookkeepingDailySummaryDto,
        whatsappOrders: List<BookkeepingWhatsappOrderDto>,
        whatsappGroups: List<BookkeepingWhatsappGroupDto>,
        headerStyle: CellStyle,
        scoreLookup: BookkeepingTitan007ScoreLookup
    ) {
        addCompanyProfitSheet(
            "公司盈亏表",
            businessDate,
            summary,
            whatsappOrders,
            whatsappGroups,
            headerStyle,
            scoreLookup
        )
        addSettlementSheet("盈亏水表格", businessDate, summary, headerStyle)
    }

    private fun XSSFWorkbook.addDailyWorkbookSheets(
        businessDate: String,
        summary: BookkeepingDailySummaryDto,
        accounts: List<BookkeepingCrownAccountDto>,
        wagers: List<BookkeepingCrownWagerDto>,
        whatsappOrders: List<BookkeepingWhatsappOrderDto>,
        reconciliationResults: List<BookkeepingReconciliationResultDto>,
        headerStyle: CellStyle,
        scoreLookup: BookkeepingTitan007ScoreLookup
    ) {
        addSheet(
            "Crown账号池",
            listOf("账号", "账号ID", "域名", "登录状态", "最后测试时间", "启用"),
            accounts.map {
                listOf(it.displayName, it.accountKey, it.baseUrl, it.lastLoginStatus.orEmpty(), it.lastLoginAt, it.enabled)
            },
            headerStyle
        )
        addCrownWagersSheet("皇冠注单", wagers, headerStyle, scoreLookup)
        addPrematchBillSheet("上游群注单", whatsappOrders.filter { it.direction == "upstream" }, emptyList(), headerStyle, scoreLookup) { order, _ ->
            calculateUpstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
        }
        addDownstreamOrdersSheet("下游群注单", whatsappOrders.filter { it.direction == "downstream" }, emptyList(), headerStyle, scoreLookup)
        addPrematchBillSheet("公司跟单表", whatsappOrders.filter { it.direction == "company_follow" }, emptyList(), headerStyle, scoreLookup) { order, _ ->
            calculateDownstreamSettlement(order.amount ?: BigDecimal.ZERO, order.oddsValue ?: BigDecimal.ZERO, order.settlementResult)
        }
        addReconciliationSheet("对账结果", reconciliationResults, headerStyle)
        addCompanyProfitSheet(
            "公司盈亏表",
            businessDate,
            summary,
            whatsappOrders.filter { it.direction in setOf("upstream", "downstream", "company_follow") },
            emptyList(),
            headerStyle,
            scoreLookup
        )
    }

    private fun XSSFWorkbook.addCrownWagersSheet(
        name: String,
        wagers: List<BookkeepingCrownWagerDto>,
        headerStyle: CellStyle,
        scoreLookup: BookkeepingTitan007ScoreLookup
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
                    actualScoreText(wager, scoreLookup),
                    wager.stakeAmount,
                    wagerOutcomeText(wager),
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

    private fun XSSFWorkbook.addDownstreamOrdersSheet(
        name: String,
        orders: List<BookkeepingWhatsappOrderDto>,
        groups: List<BookkeepingWhatsappGroupDto>,
        headerStyle: CellStyle,
        scoreLookup: BookkeepingTitan007ScoreLookup
    ) {
        addPrematchBillSheet(name, orders, groups, headerStyle, scoreLookup) { order, _ ->
                val stake = order.amount ?: BigDecimal.ZERO
                val odds = order.oddsValue ?: BigDecimal.ZERO
                calculateDownstreamSettlement(stake, odds, order.settlementResult)
        }
    }

    private fun XSSFWorkbook.addDownstreamOrdersGroupSheets(
        name: String,
        orders: List<BookkeepingWhatsappOrderDto>,
        groups: List<BookkeepingWhatsappGroupDto>,
        headerStyle: CellStyle,
        scoreLookup: BookkeepingTitan007ScoreLookup,
        groupFilter: (BookkeepingWhatsappGroupDto) -> Boolean
    ) {
        addPrematchGroupBillSheets(
            name,
            orders,
            groups,
            headerStyle,
            scoreLookup,
            groupFilter = groupFilter
        ) { order, _ ->
            val stake = order.amount ?: BigDecimal.ZERO
            val odds = order.oddsValue ?: BigDecimal.ZERO
            calculateDownstreamSettlement(stake, odds, order.settlementResult)
        }
    }

    private fun XSSFWorkbook.addPrematchGroupBillSheets(
        name: String,
        orders: List<BookkeepingWhatsappOrderDto>,
        groups: List<BookkeepingWhatsappGroupDto>,
        headerStyle: CellStyle,
        scoreLookup: BookkeepingTitan007ScoreLookup,
        groupFilter: (BookkeepingWhatsappGroupDto) -> Boolean,
        oddsResolver: (BookkeepingWhatsappOrderDto) -> BigDecimal? = { it.oddsValue },
        settlementResolver: (BookkeepingWhatsappOrderDto, Map<Long, BookkeepingWhatsappGroupDto>) -> BigDecimal
    ) {
        val orderGroupIds = orders.mapNotNull { it.groupId }.toSet()
        val groupedOrders = orders.groupBy { it.groupId }
        val relevantGroups = groups.filter { group ->
            val groupId = group.id
            groupId != null && (groupFilter(group) || groupId in orderGroupIds)
        }
        if (relevantGroups.isEmpty() && groupedOrders.size <= 1) {
            addPrematchBillSheet(name, orders, groups, headerStyle, scoreLookup, oddsResolver, settlementResolver)
            return
        }

        val usedNames = mutableSetOf<String>()
        relevantGroups.forEach { group ->
            addPrematchBillSheet(
                uniqueSheetName(group.displayName.ifBlank { group.chatName.ifBlank { group.groupKey.ifBlank { name } } }, usedNames),
                groupedOrders[group.id].orEmpty(),
                groups,
                headerStyle,
                scoreLookup,
                oddsResolver,
                settlementResolver
            )
        }

        val knownGroupIds = relevantGroups.mapNotNull { it.id }.toSet()
        orders
            .filter { order -> order.groupId?.let { it in knownGroupIds } != true }
            .groupBy { it.groupId }
            .forEach { (groupId, groupOrders) ->
                val groupName = groupId?.let { groups.firstOrNull { group -> group.id == it }?.displayName }
                    ?.takeIf { it.isNotBlank() }
                    ?: if (groupId == null) "$name-无群" else "$name-$groupId"
                addPrematchBillSheet(
                    uniqueSheetName(groupName, usedNames),
                    groupOrders,
                    groups,
                    headerStyle,
                    scoreLookup,
                    oddsResolver,
                    settlementResolver
                )
            }
    }

    private fun XSSFWorkbook.addPrematchBillSheet(
        name: String,
        orders: List<BookkeepingWhatsappOrderDto>,
        groups: List<BookkeepingWhatsappGroupDto>,
        headerStyle: CellStyle,
        scoreLookup: BookkeepingTitan007ScoreLookup,
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
                    actualScoreText(order, scoreLookup),
                    order.amount,
                    order.settlementResult.orEmpty(),
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
        headerStyle: CellStyle,
        scoreLookup: BookkeepingTitan007ScoreLookup
    ) {
        val groupById = groups.mapNotNull { group -> group.id?.let { it to group } }.toMap()
        val rows = orders.mapIndexed { index, order ->
            val stake = order.amount ?: BigDecimal.ZERO
            val odds = order.oddsValue ?: BigDecimal.ZERO
            val settlement = if (order.direction == "upstream") {
                calculateUpstreamSettlement(stake, odds, order.settlementResult)
            } else {
                calculateDownstreamSettlement(stake, odds, order.settlementResult)
            }
            listOf(
                if (index == 0) order.businessDate else "",
                index + 1,
                order.leagueName,
                order.matchName,
                betMarketAndOdds(order.marketText, order.oddsValue),
                actualScoreText(order, scoreLookup),
                order.amount,
                order.settlementResult.orEmpty(),
                settlement,
                orderSourceText(order, groupById)
            )
        } + listOf(
            listOf("", "", "", "", "", "", "", "盈亏水金额", summary.waterLossAmount, ""),
            listOf(businessDate, "", "", "", "", "", "", "日盈亏", summary.companyNetProfit, "")
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

        headers.forEachIndexed { index, header ->
            sheet.setColumnWidth(index, columnWidthForHeader(header) * EXCEL_COLUMN_WIDTH_UNIT)
        }
    }

    private fun XSSFWorkbook.uniqueSheetName(rawName: String, usedNames: MutableSet<String>): String {
        val safeName = WorkbookUtil.createSafeSheetName(rawName.ifBlank { "表格" }).ifBlank { "表格" }
        val baseName = safeName.take(MAX_EXCEL_SHEET_NAME_LENGTH)
        var candidate = baseName
        var index = 2
        while (candidate in usedNames || getSheet(candidate) != null) {
            val suffix = "($index)"
            candidate = baseName.take(MAX_EXCEL_SHEET_NAME_LENGTH - suffix.length) + suffix
            index += 1
        }
        usedNames.add(candidate)
        return candidate
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
        listOf("日期", "序号", "联赛类型", "比赛队伍", "投注盘口及赔率", "实际比分", "投注额度", "赛果", "盈亏")

    private fun columnWidthForHeader(header: String): Int = when (header) {
        "联赛类型" -> 25
        "比赛队伍" -> 25
        "投注盘口及赔率" -> 30
        else -> 20
    }

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

    private fun wagerOutcomeText(wager: BookkeepingCrownWagerDto): String = when {
        wager.winLossAmount > BigDecimal.ZERO -> "赢"
        wager.winLossAmount < BigDecimal.ZERO -> "输"
        wager.status.trim().lowercase() in SETTLED_CROWN_STATUSES -> "走水"
        else -> ""
    }

    private fun actualScoreText(order: BookkeepingWhatsappOrderDto, scoreLookup: BookkeepingTitan007ScoreLookup): String =
        scoreLookup.actualScore(order.matchName)

    private fun actualScoreText(wager: BookkeepingCrownWagerDto, scoreLookup: BookkeepingTitan007ScoreLookup): String =
        scoreLookup.actualScore(wager.homeTeam, wager.awayTeam)

    private fun orderSourceText(
        order: BookkeepingWhatsappOrderDto,
        groups: Map<Long, BookkeepingWhatsappGroupDto>
    ): String {
        val sourceGroup = order.groupId?.let { groups[it]?.displayName }?.takeIf { it.isNotBlank() }
        val direction = when (order.direction) {
            "upstream" -> "上游群"
            "downstream" -> "下游群"
            "company_follow" -> "公司跟单"
            "rolling", "rolling_downstream" -> "滚球下游"
            "rolling_upstream" -> "滚球上游"
            "rolling_company" -> "滚球公司"
            else -> order.direction
        }
        return listOf(direction, sourceGroup).filter { !it.isNullOrBlank() }.joinToString(" - ")
    }

    private fun BookkeepingWhatsappOrderDto.isRollingDirection(): Boolean =
        direction in setOf("rolling", "rolling_upstream", "rolling_downstream", "rolling_company")

    private fun BookkeepingWhatsappOrderDto.isRollingUpstreamDirection(): Boolean =
        direction == "rolling_upstream"

    private fun BookkeepingWhatsappOrderDto.isRollingDownstreamDirection(): Boolean =
        direction in setOf("rolling", "rolling_downstream")

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
        const val MAX_EXCEL_SHEET_NAME_LENGTH: Int = 31
        const val EXCEL_COLUMN_WIDTH_UNIT: Int = 256
        val SETTLED_CROWN_STATUSES: Set<String> = setOf("settled", "closed", "completed", "resolved")
        val HALF: BigDecimal = BigDecimal("0.5")
    }
}
