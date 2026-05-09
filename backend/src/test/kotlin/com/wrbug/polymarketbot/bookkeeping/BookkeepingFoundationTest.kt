package com.wrbug.polymarketbot.bookkeeping

import com.wrbug.polymarketbot.dto.BookkeepingDailySummaryDto
import com.wrbug.polymarketbot.dto.BookkeepingCrownWagerDto
import com.wrbug.polymarketbot.dto.BookkeepingWhatsappGroupDto
import com.wrbug.polymarketbot.dto.BookkeepingWhatsappOrderDto
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingExcelReportWriter
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingWhatsappAmountParser
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

class BookkeepingFoundationTest {
    @Test
    fun `migration creates bookkeeping foundation tables`() {
        val sql = Files.readString(Path.of("src/main/resources/db/migration/V56__create_bookkeeping_foundation.sql"))
        listOf(
            "bookkeeping_crown_accounts",
            "bookkeeping_crown_wagers",
            "bookkeeping_whatsapp_groups",
            "bookkeeping_whatsapp_orders",
            "bookkeeping_tasks",
            "bookkeeping_reconciliation_results"
        ).forEach { table ->
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS $table"), "missing table $table")
        }
    }

    @Test
    fun `backend exposes bookkeeping controller and whatsapp contract`() {
        val controller = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/controller/bookkeeping/BookkeepingController.kt")
        )
        listOf(
            "@RequestMapping(\"/api/bookkeeping\")",
            "@PostMapping(\"/crown/accounts/list\")",
            "@PostMapping(\"/crown/accounts/save\")",
            "@PostMapping(\"/crown/accounts/test-login\")",
            "@PostMapping(\"/crown/wagers/import\")",
            "@PostMapping(\"/whatsapp/groups/list\")",
            "@PostMapping(\"/whatsapp/groups/save\")",
            "@PostMapping(\"/whatsapp/bootstrap\")",
            "@PostMapping(\"/whatsapp/chats/sync\")",
            "@PostMapping(\"/whatsapp/status\")",
            "@PostMapping(\"/whatsapp/scan\")",
            "@PostMapping(\"/whatsapp/orders/import\")",
            "@PostMapping(\"/tasks/run\")"
        ).forEach { marker ->
            assertTrue(controller.contains(marker), "missing controller marker $marker")
        }
        assertTrue(!controller.contains("OpenClaw"), "OpenClaw controller contract should be removed")
    }

    @Test
    fun `crown password is encrypted before storage`() {
        val service = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingService.kt")
        )
        assertTrue(service.contains("encryptCrownPassword"), "missing crown password encryption helper")
        assertTrue(service.contains("enc:\${cryptoUtils.encrypt(value)}"), "crown password should be stored encrypted")
        assertTrue(service.contains("decryptCrownPassword"), "missing crown password decrypt before login")
    }

    @Test
    fun `whatsapp groups support pending upstream downstream company follow rolling and ignored roles`() {
        val service = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingService.kt")
        )
        assertTrue(service.contains("\"pending\""), "missing pending group role")
        assertTrue(service.contains("\"upstream\""), "missing upstream group role")
        assertTrue(service.contains("\"downstream\""), "missing downstream group role")
        assertTrue(service.contains("\"company_follow\""), "missing company follow group role")
        assertTrue(service.contains("\"rolling\""), "missing rolling group role")
        assertTrue(service.contains("\"ignored\""), "missing ignored group role")
    }

    @Test
    fun `whatsapp groups store currency exchange rate rebate and source chat id`() {
        val migration = Files.readString(
            Path.of("src/main/resources/db/migration/V57__add_whatsapp_group_settlement_fields.sql")
        )
        listOf(
            "source_chat_id",
            "currency",
            "exchange_rate",
            "rebate_points",
            "rebate_rate",
            "rebate_rule",
            "last_scanned_message_id",
            "configured"
        ).forEach { column ->
            assertTrue(
                migration.contains(column) || Files.readString(
                    Path.of("src/main/resources/db/migration/V58__add_whatsapp_group_configured_flag.sql")
                ).contains(column) || Files.readString(
                    Path.of("src/main/resources/db/migration/V59__add_whatsapp_group_rebate_rules.sql")
                ).contains(column),
                "missing migration column $column"
            )
        }

        val dto = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/dto/BookkeepingDto.kt")
        )
        listOf(
            "sourceChatId",
            "currency",
            "exchangeRate",
            "rebatePoints",
            "rebateRate",
            "rebateRule",
            "lastScannedMessageId",
            "configured",
            "BookkeepingWhatsappChatSyncResultDto",
            "BookkeepingWhatsappStatusDto",
            "ScanBookkeepingWhatsappMessagesRequest",
            "BookkeepingWhatsappMessageScanResultDto"
        ).forEach { marker ->
            assertTrue(dto.contains(marker), "missing DTO marker $marker")
        }
    }

    @Test
    fun `whatsapp orders carry settlement result into cashflow calculator`() {
        val migration = Files.readString(
            Path.of("src/main/resources/db/migration/V60__add_bookkeeping_whatsapp_order_settlement_result.sql")
        )
        assertTrue(migration.contains("settlement_result"), "missing whatsapp settlement result column")

        val dto = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/dto/BookkeepingDto.kt")
        )
        assertTrue(dto.contains("settlementResult"), "missing settlement result DTO field")

        val entity = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/entity/BookkeepingEntities.kt")
        )
        assertTrue(entity.contains("settlementResult"), "missing settlement result entity field")

        val service = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingService.kt")
        )
        assertTrue(service.contains("BookkeepingCalculatorWhatsappGroup"), "summary should pass group rebate config")
        assertTrue(service.contains("settlementResult = settlementResult"), "orders should pass settlement result to calculator")
    }

    @Test
    fun `task run performs reconciliation and writes excel report`() {
        val service = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingService.kt")
        )
        assertTrue(service.contains("BookkeepingCalculator"), "task should use bookkeeping calculator")
        assertTrue(service.contains("reconciliationRepository.saveAll"), "task should store reconciliation results")
        assertTrue(service.contains("excelReportWriter.writeDailyReport"), "task should write Excel report")
    }

    @Test
    fun `task run supports separate report types`() {
        val dto = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/dto/BookkeepingDto.kt")
        )
        assertTrue(dto.contains("reportType"), "task request should carry report type")
        assertTrue(dto.contains("workspaceType"), "task request should carry workspace type")

        val service = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingService.kt")
        )
        listOf(
            "crown_wagers",
            "downstream_before_rebate",
            "downstream_after_rebate",
            "upstream_orders",
            "company_orders",
            "prematch_settlement",
            "prematch_profit",
            "prematch_excel",
            "rolling_group_orders",
            "rolling_reconcile",
            "rolling_profit",
            "rolling_excel"
        ).forEach { reportType ->
            assertTrue(service.contains(reportType), "missing service report type $reportType")
        }
        listOf("prematch", "rolling", "normaliseWorkspaceType", "validateReportTypeForWorkspace").forEach { marker ->
            assertTrue(service.contains(marker), "missing workspace marker $marker")
        }
        assertTrue(service.contains("buildPrematchSummaryDto"), "prematch should use its own summary path")
        assertTrue(service.contains("buildRollingSummaryDto"), "rolling should use its own summary path")
        assertTrue(service.contains("downloadTaskFile"), "generated files should be downloadable")
        assertTrue(service.contains("clearGeneratedFiles"), "generated files should be clearable")
        assertTrue(service.contains("deleteByExcelPathIsNotNull"), "clearing generated files should clear task rows")

        val controller = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/controller/bookkeeping/BookkeepingController.kt")
        )
        assertTrue(controller.contains("/tasks/{taskId}/download"), "missing generated file download endpoint")
        assertTrue(controller.contains("/tasks/generated-files/clear"), "missing generated file clear endpoint")

        val migration = Files.readString(
            Path.of("src/main/resources/db/migration/V61__add_bookkeeping_task_workspace_type.sql")
        )
        assertTrue(migration.contains("workspace_type"), "tasks should persist workspace type")

        val entity = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/entity/BookkeepingEntities.kt")
        )
        assertTrue(entity.contains("workspaceType"), "task entity should expose workspace type")

        val writer = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingExcelReportWriter.kt")
        )
        listOf(
            "皇冠注单",
            "下游群注单（退水前）",
            "下游群注单（退水后）",
            "上游群注单",
            "公司跟单表",
            "公司盈亏表",
            "滚球群注单",
            "滚球对账表",
            "滚球盈亏表"
        ).forEach { sheetName ->
            assertTrue(writer.contains(sheetName), "missing report sheet $sheetName")
        }
        assertTrue(!writer.contains("下游群主单"), "downstream report name should use 注单")
        assertTrue(!writer.contains("退水表"), "rebate should be part of downstream sheets")
        assertTrue(!writer.contains("亏水表"), "water profit or loss should be part of company profit")
    }

    @Test
    fun `dashboard summary exposes prematch and rolling business totals`() {
        val dto = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/dto/BookkeepingDto.kt")
        )
        listOf(
            "upstreamTotalStake",
            "downstreamTotalStake",
            "downstreamRebateAmount",
            "upstreamCashflow",
            "downstreamCashflow",
            "waterLossAmount",
            "grossProfit",
            "companyNetProfit",
            "rollingGroupStake",
            "rollingGroupSettlement",
            "rollingProfitDiff"
        ).forEach { field ->
            assertTrue(dto.contains(field), "missing summary field $field")
        }

        val calculator = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingCalculator.kt")
        )
        assertTrue(calculator.contains("calculateWaterLossAmount"), "prematch should expose water-loss calculation path")
        assertTrue(calculator.contains("calculateDownstreamRebateAmount"), "prematch should expose downstream rebate amount")
        assertTrue(calculator.contains("rollingProfitDiff"), "rolling should expose profit diff")
    }

    @Test
    fun `active bookkeeping backend text is readable Chinese`() {
        val files = listOf(
            "src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingCalculator.kt",
            "src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingExcelReportWriter.kt",
            "src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingService.kt",
            "src/main/kotlin/com/wrbug/polymarketbot/dto/BookkeepingDto.kt"
        )
        val combined = files.joinToString("\n") { Files.readString(Path.of(it)) }

        mojibakeMarkers().forEach { marker ->
            assertTrue(!combined.contains(marker), "mojibake marker remains: $marker")
        }

        listOf(
            "WhatsApp 有单，Crown 没有匹配投注",
            "Crown 有投注，WhatsApp 没有匹配订单",
            "下游群注单（退水前）",
            "下游群注单（退水后）",
            "盈亏水金额"
        ).forEach { marker ->
            assertTrue(combined.contains(marker), "missing readable bookkeeping text: $marker")
        }
    }

    @Test
    fun `backend start script writes logs as utf8`() {
        listOf(
            "../start-odds-backend.ps1",
            "../start-blackcat-backend.ps1"
        ).forEach { scriptPath ->
            val script = Files.readString(Path.of(scriptPath))
            listOf(
                "-Dfile.encoding=UTF-8",
                "-Dsun.stdout.encoding=UTF-8",
                "-Dsun.stderr.encoding=UTF-8"
            ).forEach { marker ->
                assertTrue(script.contains(marker), "missing backend utf8 log flag $marker in $scriptPath")
            }
        }
    }

    @Test
    fun `excel writer creates selected report workbook only`() {
        val writer = BookkeepingExcelReportWriter()
        val summary = BookkeepingDailySummaryDto(
            crownAccountTotal = 0,
            crownSuccessCount = 0,
            crownFailedCount = 0,
            crownManualCount = 0,
            crownUntestedCount = 0,
            crownTurnover = BigDecimal.ZERO,
            settledWinLoss = BigDecimal.ZERO,
            unsettledAmount = BigDecimal.ZERO,
            whatsappOrderCount = 0,
            upstreamValidCount = 0,
            downstreamValidCount = 0,
            companyFollowCount = 0,
            companyFollowAmount = BigDecimal.ZERO,
            suspiciousCount = 0,
            cancelledCount = 0,
            differenceCount = 0,
            todayProfit = BigDecimal.ZERO
        )

        mapOf(
            "crown_wagers" to "皇冠注单",
            "downstream_before_rebate" to "下游群注单（退水前）",
            "downstream_after_rebate" to "下游群注单（退水后）",
            "upstream_orders" to "上游群注单",
            "company_orders" to "公司跟单表"
        ).entries.forEachIndexed { index, (reportType, sheetName) ->
            val file = Path.of(
                writer.writeDailyReport(
                    businessDate = "2026-05-08",
                    taskId = 9000L + index,
                    workspaceType = if (reportType == "crown_wagers") "rolling" else "prematch",
                    reportType = reportType,
                    summary = summary,
                    accounts = emptyList(),
                    wagers = emptyList(),
                    whatsappOrders = emptyList(),
                    reconciliationResults = emptyList()
                )
            )

            WorkbookFactory.create(file.toFile()).use { workbook ->
                assertEquals(1, workbook.numberOfSheets)
                assertNotNull(workbook.getSheet(sheetName), "missing selected sheet $sheetName")
            }
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `prematch account sheets use requested bill columns and first date only`() {
        val writer = BookkeepingExcelReportWriter()
        val file = Path.of(
            writer.writeDailyReport(
                businessDate = "2026-05-08",
                taskId = 9801L,
                workspaceType = "prematch",
                reportType = "prematch_excel",
                summary = BookkeepingDailySummaryDto(
                    crownAccountTotal = 0,
                    crownSuccessCount = 0,
                    crownFailedCount = 0,
                    crownManualCount = 0,
                    crownUntestedCount = 0,
                    crownTurnover = BigDecimal.ZERO,
                    settledWinLoss = BigDecimal.ZERO,
                    unsettledAmount = BigDecimal.ZERO,
                    whatsappOrderCount = 0,
                    upstreamValidCount = 0,
                    downstreamValidCount = 0,
                    companyFollowCount = 0,
                    companyFollowAmount = BigDecimal("5000"),
                    suspiciousCount = 0,
                    cancelledCount = 0,
                    differenceCount = 0,
                    todayProfit = BigDecimal.ZERO,
                    upstreamTotalStake = BigDecimal("40000"),
                    downstreamTotalStake = BigDecimal("45000"),
                    downstreamRebateAmount = BigDecimal("500"),
                    upstreamCashflow = BigDecimal("-18700"),
                    downstreamCashflow = BigDecimal("18400"),
                    waterLossAmount = BigDecimal("-300"),
                    grossProfit = BigDecimal("-300"),
                    companyNetProfit = BigDecimal("-300")
                ),
                accounts = emptyList(),
                wagers = emptyList(),
                whatsappOrders = listOf(
                    whatsappOrder("UP-1", "upstream", groupId = 1, amount = "40000", odds = "1.87"),
                    whatsappOrder("UP-2", "upstream", groupId = 1, amount = "20000", odds = "1.80"),
                    whatsappOrder("DOWN-1", "downstream", groupId = 2, amount = "10000", odds = "1.89"),
                    whatsappOrder("FOLLOW-1", "company_follow", groupId = 3, amount = "5000", odds = "1.86")
                ),
                whatsappGroups = listOf(
                    whatsappGroup(1, "上游A群"),
                    whatsappGroup(2, "下游A群"),
                    whatsappGroup(3, "公司跟单群")
                ),
                reconciliationResults = emptyList()
            )
        )

        WorkbookFactory.create(file.toFile()).use { workbook ->
            assertRowEquals(
                listOf("日期", "序号", "联赛类型", "比赛队伍", "投注盘口及赔率", "实际比分", "投注额度", "盈亏情况"),
                sheetRow(workbook.getSheet("上游群注单"), 0)
            )
            assertRowEquals(
                listOf("2026-05-08", "1", "英超", "诺丁汉 vs 曼城", "让球 主 -0.5 @ 1.87", "", "40000", "-74800"),
                sheetRow(workbook.getSheet("上游群注单"), 1)
            )
            assertEquals("", sheetRow(workbook.getSheet("上游群注单"), 2)[0])

            assertRowEquals(
                listOf("日期", "序号", "联赛类型", "比赛队伍", "投注盘口及赔率", "实际比分", "投注额度", "盈亏情况"),
                sheetRow(workbook.getSheet("下游群注单（退水后）"), 0)
            )

            assertRowEquals(
                listOf("日期", "序号", "联赛类型", "比赛队伍", "投注盘口及赔率", "实际比分", "投注额度", "盈亏情况", "订单来源"),
                sheetRow(workbook.getSheet("公司盈亏表"), 0)
            )
            val waterRow = sheetRow(workbook.getSheet("公司盈亏表"), 5)
            assertEquals("盈亏水金额", waterRow[6])
            assertEquals("-300", waterRow[7])
            val dailyRow = sheetRow(workbook.getSheet("公司盈亏表"), 6)
            assertEquals("日盈亏", dailyRow[6])
            assertEquals("-300", dailyRow[7])
        }
        Files.deleteIfExists(file)
    }

    @Test
    fun `rolling group sheet uses bill columns and score from message`() {
        val writer = BookkeepingExcelReportWriter()
        val file = Path.of(
            writer.writeDailyReport(
                businessDate = "2026-05-08",
                taskId = 9802L,
                workspaceType = "rolling",
                reportType = "rolling_excel",
                summary = BookkeepingDailySummaryDto(
                    crownAccountTotal = 0,
                    crownSuccessCount = 0,
                    crownFailedCount = 0,
                    crownManualCount = 0,
                    crownUntestedCount = 0,
                    crownTurnover = BigDecimal.ZERO,
                    settledWinLoss = BigDecimal.ZERO,
                    unsettledAmount = BigDecimal.ZERO,
                    whatsappOrderCount = 0,
                    upstreamValidCount = 0,
                    downstreamValidCount = 0,
                    companyFollowCount = 0,
                    companyFollowAmount = BigDecimal.ZERO,
                    suspiciousCount = 0,
                    cancelledCount = 0,
                    differenceCount = 0,
                    todayProfit = BigDecimal.ZERO,
                    upstreamTotalStake = BigDecimal.ZERO,
                    downstreamTotalStake = BigDecimal.ZERO,
                    downstreamRebateAmount = BigDecimal.ZERO,
                    upstreamCashflow = BigDecimal.ZERO,
                    downstreamCashflow = BigDecimal.ZERO,
                    waterLossAmount = BigDecimal.ZERO,
                    grossProfit = BigDecimal.ZERO,
                    companyNetProfit = BigDecimal.ZERO
                ),
                accounts = emptyList(),
                wagers = emptyList(),
                whatsappOrders = listOf(
                    whatsappOrder(
                        "ROLL-1",
                        "rolling",
                        groupId = 4,
                        amount = "10000",
                        odds = "0.83",
                        rawMessage = """
                            足球（滚球）让球（0 - 1）
                            日本 J1百年构想联赛
                            清水心跳 v 大阪樱花
                            大阪樱花 +0 / 0.5 @ 0.83
                            1au 确认 1
                        """.trimIndent()
                    )
                ),
                whatsappGroups = listOf(whatsappGroup(4, "滚球A群", role = "rolling")),
                reconciliationResults = emptyList()
            )
        )

        WorkbookFactory.create(file.toFile()).use { workbook ->
            assertRowEquals(
                listOf("日期", "序号", "联赛类型", "比赛队伍", "投注盘口及赔率", "实际比分", "投注额度", "盈亏情况"),
                sheetRow(workbook.getSheet("滚球群注单"), 0)
            )
            assertRowEquals(
                listOf("2026-05-08", "1", "英超", "诺丁汉 vs 曼城", "让球 主 -0.5 @ 0.83", "", "10000", "8300"),
                sheetRow(workbook.getSheet("滚球群注单"), 1)
            )
        }
        Files.deleteIfExists(file)
    }

    @Test
    fun `crown wager sheet uses same bill format and merges accounts`() {
        val writer = BookkeepingExcelReportWriter()
        val file = Path.of(
            writer.writeDailyReport(
                businessDate = "2026-05-08",
                taskId = 9803L,
                workspaceType = "rolling",
                reportType = "crown_wagers",
                summary = emptySummary(),
                accounts = emptyList(),
                wagers = listOf(
                    BookkeepingCrownWagerDto(
                        id = 1,
                        taskId = null,
                        accountId = 88,
                        businessDate = "2026-05-08",
                        ticketId = "TICKET-001",
                        wagerTime = null,
                        leagueName = "日本 J1百年构想联赛",
                        homeTeam = "清水心跳",
                        awayTeam = "大阪樱花",
                        marketType = "让球",
                        selectionName = "大阪樱花 +0 / 0.5",
                        oddsValue = BigDecimal("0.83"),
                        stakeAmount = BigDecimal("10000"),
                        winLossAmount = BigDecimal("8300"),
                        currency = "USDT",
                        status = "settled",
                        createdAt = 0,
                        updatedAt = 0
                    ),
                    BookkeepingCrownWagerDto(
                        id = 2,
                        taskId = null,
                        accountId = 99,
                        businessDate = "2026-05-08",
                        ticketId = "TICKET-002",
                        wagerTime = null,
                        leagueName = "日本 J2 J3百年构想联赛",
                        homeTeam = "RB大宫松鼠",
                        awayTeam = "磐城",
                        marketType = "大小",
                        selectionName = "大 3 / 3.5",
                        oddsValue = BigDecimal("1.07"),
                        stakeAmount = BigDecimal("10000"),
                        winLossAmount = BigDecimal("-10000"),
                        currency = "USDT",
                        status = "settled",
                        createdAt = 0,
                        updatedAt = 0
                    )
                ),
                whatsappOrders = emptyList(),
                reconciliationResults = emptyList()
            )
        )

        WorkbookFactory.create(file.toFile()).use { workbook ->
            assertRowEquals(
                listOf("日期", "序号", "联赛类型", "比赛队伍", "投注盘口及赔率", "实际比分", "投注额度", "盈亏情况"),
                sheetRow(workbook.getSheet("皇冠注单"), 0)
            )
            assertRowEquals(
                listOf("2026-05-08", "1", "日本 J1百年构想联赛", "清水心跳 vs 大阪樱花", "让球 大阪樱花 +0 / 0.5 @ 0.83", "", "10000", "8300"),
                sheetRow(workbook.getSheet("皇冠注单"), 1)
            )
            assertEquals("", sheetRow(workbook.getSheet("皇冠注单"), 2)[0])
            assertEquals("2", sheetRow(workbook.getSheet("皇冠注单"), 2)[1])
            assertEquals("RB大宫松鼠 vs 磐城", sheetRow(workbook.getSheet("皇冠注单"), 2)[3])
        }
        Files.deleteIfExists(file)
    }

    @Test
    fun `whatsapp amount parser treats a as ten thousand units`() {
        assertEquals(BigDecimal("10000"), BookkeepingWhatsappAmountParser.parseAmount("1au 确认 1"))
        assertEquals(BigDecimal("20000"), BookkeepingWhatsappAmountParser.parseAmount("2a 确认 2"))
        assertEquals(BigDecimal("500"), BookkeepingWhatsappAmountParser.parseAmount("500U 确认"))
    }

    @Test
    fun `bookkeeping dto exposes daily dashboard and crown wager import contract`() {
        val dto = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/dto/BookkeepingDto.kt")
        )
        listOf(
            "BookkeepingDailySummaryDto",
            "ImportBookkeepingCrownWagersRequest",
            "ImportBookkeepingCrownWagersResultDto",
            "BookkeepingCrownWagerImportDto",
            "BookkeepingWhatsappBootstrapDto",
            "BookkeepingWhatsappChatSyncResultDto",
            "BookkeepingReconciliationResultDto"
        ).forEach { marker ->
            assertTrue(dto.contains(marker), "missing DTO marker $marker")
        }
        assertTrue(!dto.contains("OpenClaw"), "OpenClaw DTO should be removed")
    }

    @Test
    fun `whatsapp bridge reader is wired to prematch and rolling scan`() {
        val bridge = Files.readString(Path.of("../whatsapp-bridge/server.mjs"))
        listOf(
            "request.url === '/messages'",
            "fetchMessages",
            "chatIds",
            "startTime",
            "endTime",
            "limit"
        ).forEach { marker ->
            assertTrue(bridge.contains(marker), "missing bridge message marker $marker")
        }

        val service = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingService.kt")
        )
        listOf(
            "scanWhatsappMessages",
            "fetchWhatsappBridgeMessages",
            "rolesForWhatsappScan",
            "\"upstream\", \"downstream\", \"company_follow\"",
            "\"rolling\""
        ).forEach { marker ->
            assertTrue(service.contains(marker), "missing scan service marker $marker")
        }
    }

    private fun mojibakeMarkers(): List<String> =
        listOf(
            0x9A9E,
            0x9428,
            0x947B,
            0x59AF,
            0x7481,
            0x6FB6,
            0x7459,
            0x9427,
            0x7035,
            0x93CC,
            0x7EC1,
            0x6960,
            0x9351,
            0x95BF,
            0x95C2,
            0x5A75,
            0x7F02,
            0x95C1,
            0x6FE0,
            0x95BC,
            0x9420,
            0x95B9,
            0x9477,
            0x934B,
            0x6769,
            0x7490,
            0x93B6,
            0x68E3
        ).map { it.toChar().toString() }

    private fun whatsappOrder(
        orderKey: String,
        direction: String,
        groupId: Long,
        amount: String,
        odds: String,
        rawMessage: String = "测试订单"
    ) = BookkeepingWhatsappOrderDto(
        id = orderKey.hashCode().toLong(),
        taskId = null,
        groupId = groupId,
        businessDate = "2026-05-08",
        orderKey = orderKey,
        direction = direction,
        messageTime = null,
        senderName = "小Q",
        rawMessage = rawMessage,
        leagueName = "英超",
        matchName = "诺丁汉 vs 曼城",
        marketText = "让球 主 -0.5",
        oddsValue = BigDecimal(odds),
        amount = BigDecimal(amount),
        currency = "USDT",
        parseStatus = "valid",
        settlementResult = "赢",
        createdAt = 0,
        updatedAt = 0
    )

    private fun emptySummary() = BookkeepingDailySummaryDto(
        crownAccountTotal = 0,
        crownSuccessCount = 0,
        crownFailedCount = 0,
        crownManualCount = 0,
        crownUntestedCount = 0,
        crownTurnover = BigDecimal.ZERO,
        settledWinLoss = BigDecimal.ZERO,
        unsettledAmount = BigDecimal.ZERO,
        whatsappOrderCount = 0,
        upstreamValidCount = 0,
        downstreamValidCount = 0,
        companyFollowCount = 0,
        companyFollowAmount = BigDecimal.ZERO,
        suspiciousCount = 0,
        cancelledCount = 0,
        differenceCount = 0,
        todayProfit = BigDecimal.ZERO,
        upstreamTotalStake = BigDecimal.ZERO,
        downstreamTotalStake = BigDecimal.ZERO,
        downstreamRebateAmount = BigDecimal.ZERO,
        upstreamCashflow = BigDecimal.ZERO,
        downstreamCashflow = BigDecimal.ZERO,
        waterLossAmount = BigDecimal.ZERO,
        grossProfit = BigDecimal.ZERO,
        companyNetProfit = BigDecimal.ZERO
    )

    private fun whatsappGroup(
        id: Long,
        displayName: String,
        role: String = "downstream"
    ) = BookkeepingWhatsappGroupDto(
        id = id,
        groupKey = "group-$id",
        sourceChatId = null,
        displayName = displayName,
        chatName = displayName,
        role = role,
        currency = "USDT",
        exchangeRate = BigDecimal.ONE,
        rebatePoints = if (id == 2L) BigDecimal("5") else BigDecimal.ZERO,
        rebateRate = BigDecimal.ZERO,
        rebateRule = "none",
        lastScannedMessageId = null,
        configured = true,
        enabled = true,
        updatedAt = 0
    )

    private fun sheetRow(sheet: org.apache.poi.ss.usermodel.Sheet, rowIndex: Int): List<String> {
        val formatter = DataFormatter()
        val row = sheet.getRow(rowIndex)
        return (0 until row.lastCellNum).map { index -> formatter.formatCellValue(row.getCell(index)) }
    }

    private fun assertRowEquals(expected: List<String>, actual: List<String>) {
        assertEquals(expected, actual.take(expected.size))
    }
}
