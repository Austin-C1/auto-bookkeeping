package com.wrbug.polymarketbot.service.bookkeeping

import com.wrbug.polymarketbot.dto.BookkeepingCrownAccountDto
import com.wrbug.polymarketbot.dto.BookkeepingCrownWagerDto
import com.wrbug.polymarketbot.dto.BookkeepingDailySummaryDto
import com.wrbug.polymarketbot.dto.BookkeepingReconciliationResultDto
import com.wrbug.polymarketbot.dto.BookkeepingWhatsappGroupDto
import com.wrbug.polymarketbot.dto.BookkeepingWhatsappOrderDto
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.VerticalAlignment
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
    fun withAutoSettlementResults(
        businessDate: String,
        orders: List<BookkeepingWhatsappOrderDto>
    ): List<BookkeepingWhatsappOrderDto> {
        if (orders.isEmpty()) return orders

        val scoreLookup = scoreResultReader.loadScoreLookup(businessDate)
        return orders.map { order ->
            val actualScore = actualScoreText(order, scoreLookup)
            val settlementResult = effectiveSettlementResult(order, actualScore)
            if (settlementResult.isNullOrBlank() || settlementResult == order.settlementResult) {
                order
            } else {
                order.copy(settlementResult = settlementResult)
            }
        }
    }

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
        val rows = wagers.mapIndexed { index, wager ->
            listOf(
                if (index == 0) wager.businessDate else "",
                index + 1,
                wager.leagueName,
                betTypeText(wager.marketType, wager.selectionName),
                crownMatchName(wager),
                crownMarketAndOdds(wager),
                "",
                actualScoreText(wager, scoreLookup),
                amountWithCurrency(wager.stakeAmount, wager.currency),
                wagerOutcomeText(wager),
                wager.winLossAmount
            )
        }
        val totalProfit = wagers.fold(BigDecimal.ZERO) { total, wager ->
            total.add(wager.winLossAmount)
        }
        val totalStake = wagers.fold(BigDecimal.ZERO) { total, wager ->
            total.add(wager.stakeAmount)
        }
        val totalStakeCurrency = commonCurrencySuffix(wagers.map { it.currency })
        addSheet(
            name,
            accountBillHeaders(),
            accountBillRowsWithTotal(rows, amountWithCurrency(totalStake, totalStakeCurrency), totalProfit),
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
        val rowItems = orders.mapIndexed { index, order ->
            val odds = oddsResolver(order)
            val actualScore = actualScoreText(order, scoreLookup)
            val settlementResult = effectiveSettlementResult(order, actualScore)
            val settlementOrder = order.copy(settlementResult = settlementResult)
            val settlement = settlementResolver(settlementOrder, groupById)
            val currency = currencyForOrder(order, groupById)
            listOf(
                if (index == 0) order.businessDate else "",
                index + 1,
                order.leagueName,
                betTypeText(order.marketText),
                order.matchName,
                betMarketAndOdds(order.marketText, odds),
                bettingScoreText(order.rawMessage),
                actualScore,
                amountWithCurrency(order.amount, currency),
                settlementResult.orEmpty(),
                settlement
            ) to (settlement to currency)
        }
        val rows = rowItems.map { it.first }
        val totalStake = orders.fold(BigDecimal.ZERO) { total, order ->
            total.add(order.amount ?: BigDecimal.ZERO)
        }
        val totalStakeCurrency = commonCurrencySuffix(rowItems.map { it.second.second })
        val totalProfit = rowItems.fold(BigDecimal.ZERO) { total, item -> total.add(item.second.first) }
        addSheet(
            name,
            accountBillHeaders(),
            accountBillRowsWithTotal(rows, amountWithCurrency(totalStake, totalStakeCurrency), totalProfit),
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
            val actualScore = actualScoreText(order, scoreLookup)
            val settlementResult = effectiveSettlementResult(order, actualScore)
            val settlement = if (order.direction == "upstream") {
                calculateUpstreamSettlement(stake, odds, settlementResult)
            } else {
                calculateDownstreamSettlement(stake, odds, settlementResult)
            }
            listOf(
                if (index == 0) order.businessDate else "",
                index + 1,
                order.leagueName,
                betTypeText(order.marketText),
                order.matchName,
                betMarketAndOdds(order.marketText, order.oddsValue),
                bettingScoreText(order.rawMessage),
                actualScore,
                amountWithCurrency(order.amount, currencyForOrder(order, groupById)),
                settlementResult.orEmpty(),
                settlement,
                orderSourceText(order, groupById)
            )
        } + listOf(
            listOf("", "", "", "", "", "", "", "", "", "盈亏水金额", summary.waterLossAmount, ""),
            listOf(businessDate, "", "", "", "", "", "", "", "", "日盈亏", summary.companyNetProfit, "")
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
        style.alignment = HorizontalAlignment.CENTER
        style.verticalAlignment = VerticalAlignment.CENTER
        return style
    }

    private fun XSSFWorkbook.createCenteredDataStyle(): CellStyle {
        val style = createCellStyle()
        style.alignment = HorizontalAlignment.CENTER
        style.verticalAlignment = VerticalAlignment.CENTER
        return style
    }

    private fun XSSFWorkbook.addSheet(
        name: String,
        headers: List<String>,
        rows: List<List<Any?>>,
        headerStyle: CellStyle
    ) {
        val sheet = createSheet(name)
        val dataStyle = createCenteredDataStyle()
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.setCellStyle(headerStyle)
        }

        rows.forEachIndexed { rowIndex, values ->
            val row = sheet.createRow(rowIndex + 1)
            values.forEachIndexed { cellIndex, value ->
                val cell = row.createCell(cellIndex)
                cell.setAnyValue(value)
                cell.setCellStyle(dataStyle)
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
        listOf("日期", "序号", "联赛类型", "投注类型", "比赛队伍", "投注盘口及赔率", "投注时比分", "实际比分", "投注额度", "赛果", "盈亏")

    private fun accountBillRowsWithTotal(
        rows: List<List<Any?>>,
        totalStakeText: String,
        totalProfit: BigDecimal
    ): List<List<Any?>> =
        if (rows.isEmpty()) {
            rows
        } else {
            rows + listOf(listOf<Any?>("", "", "", "", "", "", "", "总投注额度", totalStakeText, "总盈亏", totalProfit))
        }

    private fun columnWidthForHeader(header: String): Int = when (header) {
        "联赛类型" -> 25
        "比赛队伍" -> 35
        "投注盘口及赔率" -> 35
        else -> 20
    }

    private fun betMarketAndOdds(marketText: String?, odds: BigDecimal?): String {
        val rawMarket = marketText?.trim().orEmpty()
        val market = marketWithoutOddsSuffix(rawMarket)
        val oddsText = oddsTextFromMarket(rawMarket) ?: odds?.stripTrailingZeros()?.toPlainString().orEmpty()
        return when {
            market.isNotEmpty() && oddsText.isNotEmpty() -> "$market @ $oddsText"
            market.isNotEmpty() -> market
            else -> oddsText
        }
    }

    private fun bettingScoreText(rawMessage: String?): String {
        val header = rawMessage
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            .orEmpty()
        if (!header.contains("滚球")) return ""
        val match = BETTING_SCORE_REGEX.find(header) ?: return ""
        return "${match.groupValues[1]}-${match.groupValues[2]}"
    }

    private fun amountWithCurrency(amount: BigDecimal?, currency: String?): String {
        val amountText = amount?.stripTrailingZeros()?.toPlainString().orEmpty()
        if (amountText.isEmpty()) return ""
        return amountText + currencySuffix(currency)
    }

    private fun currencyForOrder(
        order: BookkeepingWhatsappOrderDto,
        groups: Map<Long, BookkeepingWhatsappGroupDto>
    ): String? =
        order.currency?.takeIf { it.isNotBlank() }
            ?: order.groupId?.let { groups[it]?.currency }?.takeIf { it.isNotBlank() }

    private fun commonCurrencySuffix(currencies: List<String?>): String? {
        val suffixes = currencies.map { currencySuffix(it) }.filter { it.isNotEmpty() }.distinct()
        return suffixes.singleOrNull()
    }

    private fun currencySuffix(currency: String?): String {
        val normalized = currency?.trim().orEmpty()
        if (normalized.isEmpty()) return ""
        return when (normalized.uppercase()) {
            "R", "RMB", "CNY" -> "R"
            "U", "USDT", "USD" -> "U"
            else -> normalized
        }
    }

    private fun betTypeText(vararg values: String?): String {
        val text = values
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" ")
        val compact = text.lowercase().replace(Regex("\\s+"), "")
        if (compact.isBlank()) return ""
        return when {
            compact.contains("大小") ||
                compact.contains("大/小") ||
                compact.contains("over") ||
                compact.contains("under") ||
                compact.startsWith("大") ||
                compact.startsWith("小") -> "大小"
            compact.contains("让球") ||
                Regex("""(^|\s)[+-]?\d+(?:\.\d+)?(?:\s*/\s*[+-]?\d+(?:\.\d+)?)?($|\s)""").containsMatchIn(text) -> "让球"
            else -> ""
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
        wager.status.trim().lowercase() in SETTLED_CROWN_STATUSES -> "走盘"
        else -> ""
    }

    private fun actualScoreText(order: BookkeepingWhatsappOrderDto, scoreLookup: BookkeepingTitan007ScoreLookup): String =
        scoreLookup.actualScore(order.matchName)

    private fun actualScoreText(wager: BookkeepingCrownWagerDto, scoreLookup: BookkeepingTitan007ScoreLookup): String =
        scoreLookup.actualScore(wager.homeTeam, wager.awayTeam)

    private fun effectiveSettlementResult(order: BookkeepingWhatsappOrderDto, actualScore: String): String? {
        val manualSettlement = order.settlementResult?.trim()?.takeIf { it.isNotEmpty() }
        if (manualSettlement != null) {
            return manualSettlement.toSettlementOutcome()?.displayText() ?: manualSettlement
        }
        return deriveSettlementOutcome(order.marketText, order.matchName, actualScore)?.displayText()
    }

    private fun deriveSettlementOutcome(
        marketText: String?,
        matchName: String?,
        actualScore: String
    ): SettlementOutcome? {
        val market = marketWithoutOddsSuffix(marketText?.trim().orEmpty()).takeIf { it.isNotEmpty() } ?: return null
        val score = parseMatchScore(actualScore) ?: return null
        return if (isTotalMarket(market)) {
            deriveTotalOutcome(market, score)
        } else {
            deriveHandicapOutcome(market, matchName, score)
        }
    }

    private fun deriveTotalOutcome(market: String, score: MatchScore): SettlementOutcome? {
        val compact = market.lowercase().replace(Regex("\\s+"), "")
        val isOver = compact.startsWith("大") || compact.contains("over")
        val isUnder = compact.startsWith("小") || compact.contains("under")
        if (!isOver && !isUnder) return null
        val lines = extractAsianLines(market, inheritSign = false)
        if (lines.isEmpty()) return null
        val totalGoals = BigDecimal(score.home + score.away)
        return combineAsianOutcomes(
            lines.map { line ->
                val margin = if (isOver) totalGoals.subtract(line) else line.subtract(totalGoals)
                singleLineOutcome(margin)
            }
        )
    }

    private fun deriveHandicapOutcome(
        market: String,
        matchName: String?,
        score: MatchScore
    ): SettlementOutcome? {
        val teams = splitMatchName(matchName) ?: return null
        val selectedSide = selectedSideForHandicap(market, teams) ?: return null
        val handicaps = extractAsianLines(market, inheritSign = true)
        if (handicaps.isEmpty()) return null
        val selectedGoalDiff = if (selectedSide == SelectedSide.HOME) {
            BigDecimal(score.home - score.away)
        } else {
            BigDecimal(score.away - score.home)
        }
        return combineAsianOutcomes(
            handicaps.map { handicap ->
                singleLineOutcome(selectedGoalDiff.add(handicap))
            }
        )
    }

    private fun parseMatchScore(actualScore: String): MatchScore? {
        val match = SCORE_REGEX.find(actualScore.trim()) ?: return null
        return MatchScore(
            home = match.groupValues[1].toInt(),
            away = match.groupValues[2].toInt()
        )
    }

    private fun isTotalMarket(market: String): Boolean {
        val compact = market.lowercase().replace(Regex("\\s+"), "")
        return compact.contains("大小") ||
            compact.contains("大/小") ||
            compact.contains("over") ||
            compact.contains("under") ||
            compact.startsWith("大") ||
            compact.startsWith("小")
    }

    private fun extractAsianLines(text: String, inheritSign: Boolean): List<BigDecimal> {
        val expression = ASIAN_LINE_EXPRESSION_REGEX.findAll(text).lastOrNull()?.value ?: return emptyList()
        var inheritedSign: String? = null
        return expression.split("/").mapNotNull { rawPart ->
            val part = rawPart.trim()
            if (part.isEmpty()) return@mapNotNull null
            val explicitSign = part.firstOrNull()?.takeIf { it == '+' || it == '-' }?.toString()
            val signedPart = if (inheritSign && explicitSign == null && inheritedSign != null) {
                inheritedSign + part
            } else {
                part
            }
            if (inheritSign && explicitSign != null) {
                inheritedSign = explicitSign
            }
            signedPart.removePrefix("+").toBigDecimalOrNull()
        }
    }

    private fun singleLineOutcome(margin: BigDecimal): SettlementOutcome = when {
        margin > BigDecimal.ZERO -> SettlementOutcome.WIN
        margin < BigDecimal.ZERO -> SettlementOutcome.LOSE
        else -> SettlementOutcome.PUSH
    }

    private fun combineAsianOutcomes(outcomes: List<SettlementOutcome>): SettlementOutcome? {
        if (outcomes.isEmpty()) return null
        val score = outcomes.fold(0) { total, outcome ->
            total + when (outcome) {
                SettlementOutcome.WIN -> 1
                SettlementOutcome.WIN_HALF -> 1
                SettlementOutcome.PUSH -> 0
                SettlementOutcome.LOSE_HALF -> -1
                SettlementOutcome.LOSE -> -1
            }
        }
        return when {
            score == outcomes.size -> SettlementOutcome.WIN
            score > 0 -> SettlementOutcome.WIN_HALF
            score == 0 -> SettlementOutcome.PUSH
            score > -outcomes.size -> SettlementOutcome.LOSE_HALF
            else -> SettlementOutcome.LOSE
        }
    }

    private fun splitMatchName(matchName: String?): MatchTeams? {
        val parts = matchName
            ?.trim()
            ?.split(MATCH_NAME_SPLIT_REGEX, limit = 2)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: return null
        if (parts.size != 2) return null
        return MatchTeams(parts[0], parts[1])
    }

    private fun selectedSideForHandicap(market: String, teams: MatchTeams): SelectedSide? {
        val selectionText = marketWithoutAsianLine(market)
        if (HOME_TOKEN_REGEX.containsMatchIn(selectionText)) return SelectedSide.HOME
        if (AWAY_TOKEN_REGEX.containsMatchIn(selectionText)) return SelectedSide.AWAY

        val selection = normaliseTeamForSettlement(selectionText)
        if (selection.isBlank()) return null
        val home = normaliseTeamForSettlement(teams.home)
        val away = normaliseTeamForSettlement(teams.away)
        return when {
            teamNameMatches(selection, away) -> SelectedSide.AWAY
            teamNameMatches(selection, home) -> SelectedSide.HOME
            else -> null
        }
    }

    private fun marketWithoutAsianLine(market: String): String {
        val match = ASIAN_LINE_EXPRESSION_REGEX.findAll(market).lastOrNull() ?: return market
        return market.removeRange(match.range.first, match.range.last + 1).trim()
    }

    private fun marketWithoutOddsSuffix(market: String): String =
        market.replace(ODDS_SUFFIX_REGEX, "").trim()

    private fun oddsTextFromMarket(market: String): String? =
        ODDS_SUFFIX_REGEX.find(market)?.value?.substringAfter("@")?.trim()?.takeIf { it.isNotEmpty() }

    private fun normaliseTeamForSettlement(value: String): String =
        value.lowercase()
            .replace("让球", "")
            .replace("主队", "主")
            .replace("客队", "客")
            .replace(TEAM_TEXT_CLEANUP_REGEX, "")

    private fun teamNameMatches(selection: String, team: String): Boolean =
        selection == team ||
            (selection.length >= 2 && team.contains(selection)) ||
            (team.length >= 2 && selection.contains(team))

    private fun SettlementOutcome.displayText(): String = when (this) {
        SettlementOutcome.WIN -> "赢"
        SettlementOutcome.WIN_HALF -> "赢半"
        SettlementOutcome.PUSH -> "走盘"
        SettlementOutcome.LOSE_HALF -> "输半"
        SettlementOutcome.LOSE -> "输"
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
            "push", "draw", "void", "走盘", "走水", "和" -> SettlementOutcome.PUSH
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

    private enum class SelectedSide {
        HOME,
        AWAY
    }

    private data class MatchScore(
        val home: Int,
        val away: Int
    )

    private data class MatchTeams(
        val home: String,
        val away: String
    )

    private companion object {
        const val MAX_EXCEL_SHEET_NAME_LENGTH: Int = 31
        const val EXCEL_COLUMN_WIDTH_UNIT: Int = 256
        val SETTLED_CROWN_STATUSES: Set<String> = setOf("settled", "closed", "completed", "resolved")
        val HALF: BigDecimal = BigDecimal("0.5")
        val SCORE_REGEX: Regex = Regex("""^\s*(\d+)\s*[-:：]\s*(\d+)\s*$""")
        val BETTING_SCORE_REGEX: Regex = Regex("""[(（]\s*(\d+)\s*[-:：]\s*(\d+)\s*[)）]""")
        val ASIAN_LINE_EXPRESSION_REGEX: Regex =
            Regex("""[+-]?\d+(?:\.\d+)?(?:\s*/\s*[+-]?\d+(?:\.\d+)?)*""")
        val ODDS_SUFFIX_REGEX: Regex = Regex("""\s*@\s*[+-]?\d+(?:\.\d+)?\s*$""")
        val MATCH_NAME_SPLIT_REGEX: Regex = Regex("""\s+(?:vs?\.?|对)\s+""", RegexOption.IGNORE_CASE)
        val TEAM_TEXT_CLEANUP_REGEX: Regex = Regex("""[\s·.\-_/（）()\[\]【】]+""")
        val HOME_TOKEN_REGEX: Regex = Regex("""(^|\s)(主|主队|home)($|\s)""", RegexOption.IGNORE_CASE)
        val AWAY_TOKEN_REGEX: Regex = Regex("""(^|\s)(客|客队|away)($|\s)""", RegexOption.IGNORE_CASE)
    }
}
