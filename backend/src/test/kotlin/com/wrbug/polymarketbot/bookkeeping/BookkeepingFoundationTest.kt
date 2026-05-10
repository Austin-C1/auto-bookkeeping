package com.wrbug.polymarketbot.bookkeeping

import com.wrbug.polymarketbot.dto.BookkeepingDailySummaryDto
import com.wrbug.polymarketbot.dto.BookkeepingCrownWagerDto
import com.wrbug.polymarketbot.dto.BookkeepingWhatsappGroupDto
import com.wrbug.polymarketbot.dto.BookkeepingWhatsappOrderDto
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingCalculator
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingCalculatorAccount
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingCalculatorWhatsappOrder
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingExcelReportWriter
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingTitan007ScoreResultReader
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingWhatsappAmountParser
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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
    fun `backend exposes telegram group scan contract`() {
        val controller = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/controller/bookkeeping/BookkeepingController.kt")
        )
        listOf(
            "@PostMapping(\"/telegram/groups/list\")",
            "@PostMapping(\"/telegram/groups/save\")",
            "@PostMapping(\"/telegram/api-config\")",
            "@PostMapping(\"/telegram/api-config/save\")",
            "@PostMapping(\"/telegram/status\")",
            "@PostMapping(\"/telegram/chats/sync\")",
            "@PostMapping(\"/telegram/scan\")",
            "@PostMapping(\"/score-results/titan007/fetch\")"
        ).forEach { marker ->
            assertTrue(controller.contains(marker), "missing telegram controller marker $marker")
        }

        val dto = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/dto/BookkeepingDto.kt")
        )
        listOf(
            "BookkeepingTelegramApiConfigDto",
            "SaveBookkeepingTelegramApiConfigRequest",
            "BookkeepingTelegramGroupDto",
            "SaveBookkeepingTelegramGroupRequest",
            "BookkeepingTelegramChatSyncResultDto",
            "BookkeepingTelegramStatusDto",
            "ScanBookkeepingTelegramMessagesRequest",
            "BookkeepingTelegramMessageScanResultDto",
            "FetchBookkeepingTitan007ScoresRequest",
            "BookkeepingTitan007ScoreFetchResultDto"
        ).forEach { marker ->
            assertTrue(dto.contains(marker), "missing telegram DTO marker $marker")
        }

        val service = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingService.kt")
        )
        listOf(
            "telegramApiConfig",
            "saveTelegramApiConfig",
            "restartTelegramBridge",
            "syncTelegramChats",
            "telegramStatus",
            "scanTelegramMessages",
            "fetchTitan007ScoreResults",
            "BookkeepingTitan007ScoreCrawler",
            "fetchTelegramBridgeGroups",
            "fetchTelegramBridgeMessages",
            "buildTelegramGroupKey",
            "telegram_"
        ).forEach { marker ->
            assertTrue(service.contains(marker), "missing telegram service marker $marker")
        }
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
    fun `message group roles distinguish prematch and rolling upstream downstream company`() {
        val service = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingService.kt")
        )
        listOf(
            "\"upstream\"",
            "\"downstream\"",
            "\"company_follow\"",
            "\"rolling_upstream\"",
            "\"rolling_downstream\"",
            "\"rolling_company\"",
            "PREMATCH_GROUP_ROLES",
            "ROLLING_GROUP_ROLES"
        ).forEach { marker ->
            assertTrue(service.contains(marker), "missing explicit workspace group role marker $marker")
        }
    }

    @Test
    fun `message imports resolve group by source type and group key`() {
        val service = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingService.kt")
        )
        listOf(
            "it.sourceType to it.groupKey",
            "groups[sourceType to item.groupKey]?.id"
        ).forEach { marker ->
            assertTrue(service.contains(marker), "missing source isolated import marker $marker")
        }
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
            "downstream_orders",
            "upstream_orders",
            "company_orders",
            "prematch_settlement",
            "prematch_profit",
            "prematch_excel",
            "rolling_upstream_orders",
            "rolling_downstream_orders",
            "rolling_water",
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
            "下游群注单",
            "上游群注单",
            "公司跟单表",
            "公司盈亏表",
            "滚球上游各群表格",
            "滚球下游各群表格",
            "盈亏水表格",
            "滚球群注单",
            "滚球对账表",
            "滚球盈亏表"
        ).forEach { sheetName ->
            assertTrue(writer.contains(sheetName), "missing report sheet $sheetName")
        }
        assertTrue(!writer.contains("下游群主单"), "downstream report name should use 注单")
        assertTrue(!writer.contains("退水表"), "downstream reports should not generate rebate sheets")
        assertTrue(writer.contains("盈亏水表格"), "water profit or loss should have its own requested report")
    }

    @Test
    fun `dashboard summary exposes prematch and rolling business totals`() {
        val dto = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/dto/BookkeepingDto.kt")
        )
        listOf(
            "upstreamTotalStake",
            "downstreamTotalStake",
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
        assertTrue(!calculator.contains("calculateDownstreamRebateAmount"), "downstream rebate amount should be removed")
        assertTrue(calculator.contains("rollingProfitDiff"), "rolling should expose profit diff")
    }

    @Test
    fun `bookkeeping task and dashboard isolate prematch and rolling orders`() {
        val service = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingService.kt")
        )
        listOf(
            "ordersForWorkspace(workspaceType, allWhatsappOrders.map { it.toDto() })",
            "withAutoSettlementResults(",
            "workspaceWhatsappOrders = allWhatsappOrders.filter { orderMatchesWorkspace(workspaceType, it.direction) }",
            "private fun ordersForWorkspace",
            "private fun orderMatchesWorkspace"
        ).forEach { marker ->
            assertTrue(service.contains(marker), "missing workspace order isolation marker $marker")
        }
    }

    @Test
    fun `message scan marks incomplete order blocks suspicious`() {
        val service = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingService.kt")
        )
        assertTrue(
            service.contains("parseStatus = if (amount != null && odds != null) \"parsed\" else \"suspicious\""),
            "message parser should require both amount and odds before marking parsed"
        )
    }

    @Test
    fun `message scan uses last scanned id unless force rescans`() {
        val service = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingService.kt")
        )
        listOf(
            "messagesAfterLastScanned(groups, bridgeResponse.messages, request.force)",
            "if (force) return messages",
            "chatMessages.drop(lastSeenIndex + 1)"
        ).forEach { marker ->
            assertTrue(service.contains(marker), "missing incremental scan marker $marker")
        }
    }

    @Test
    fun `bookkeeping task records failure status when generation fails`() {
        val service = Files.readString(
            Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/bookkeeping/BookkeepingService.kt")
        )
        listOf(
            "runCatching {",
            "status = \"failed\"",
            "resultSummaryJson = error.message"
        ).forEach { marker ->
            assertTrue(service.contains(marker), "missing task failure marker $marker")
        }
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
            "下游群注单",
            "盈亏水金额"
        ).forEach { marker ->
            assertTrue(combined.contains(marker), "missing readable bookkeeping text: $marker")
        }
        assertTrue(!combined.contains("退水前"), "downstream before rebate text should be removed")
        assertTrue(!combined.contains("退水后"), "downstream after rebate text should be removed")
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
            "downstream_orders" to "下游群注单",
            "upstream_orders" to "上游群注单",
            "company_orders" to "公司跟单表",
            "prematch_settlement" to "赛前结算表",
            "rolling_upstream_orders" to "滚球上游各群表格",
            "rolling_downstream_orders" to "滚球下游各群表格",
            "rolling_water" to "盈亏水表格"
        ).entries.forEachIndexed { index, (reportType, sheetName) ->
            val file = Path.of(
                writer.writeDailyReport(
                    businessDate = "2026-05-08",
                    taskId = 9000L + index,
                    workspaceType = if (reportType == "crown_wagers" || reportType.startsWith("rolling_")) "rolling" else "prematch",
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
    fun `prematch upstream and downstream group reports keep every group in its own sheet`() {
        val writer = BookkeepingExcelReportWriter()
        val groups = listOf(
            whatsappGroup(11, "上游A群", role = "upstream"),
            whatsappGroup(12, "上游B群", role = "upstream"),
            whatsappGroup(21, "下游A群", role = "downstream"),
            whatsappGroup(22, "下游B群", role = "downstream")
        )
        val orders = listOf(
            whatsappOrder("UP-A-1", "upstream", groupId = 11, amount = "10000", odds = "1.50"),
            whatsappOrder("UP-B-1", "upstream", groupId = 12, amount = "20000", odds = "1.60"),
            whatsappOrder("DOWN-A-1", "downstream", groupId = 21, amount = "30000", odds = "1.70"),
            whatsappOrder("DOWN-B-1", "downstream", groupId = 22, amount = "40000", odds = "1.80")
        )

        val upstreamFile = Path.of(
            writer.writeDailyReport(
                businessDate = "2026-05-08",
                taskId = 9901L,
                workspaceType = "prematch",
                reportType = "upstream_orders",
                summary = emptySummary(),
                accounts = emptyList(),
                wagers = emptyList(),
                whatsappOrders = orders,
                whatsappGroups = groups,
                reconciliationResults = emptyList()
            )
        )
        val downstreamFile = Path.of(
            writer.writeDailyReport(
                businessDate = "2026-05-08",
                taskId = 9902L,
                workspaceType = "prematch",
                reportType = "downstream_orders",
                summary = emptySummary(),
                accounts = emptyList(),
                wagers = emptyList(),
                whatsappOrders = orders,
                whatsappGroups = groups,
                reconciliationResults = emptyList()
            )
        )

        WorkbookFactory.create(upstreamFile.toFile()).use { workbook ->
            assertEquals(2, workbook.numberOfSheets)
            assertEquals("10000U", sheetRow(workbook.getSheet("上游A群"), 1)[8])
            assertEquals("20000U", sheetRow(workbook.getSheet("上游B群"), 1)[8])
            assertBillTotalRow(workbook.getSheet("上游A群"), 2, "10000U", "-15000")
            assertBillTotalRow(workbook.getSheet("上游B群"), 2, "20000U", "-32000")
            assertEquals(2, workbook.getSheet("上游A群").lastRowNum)
            assertEquals(2, workbook.getSheet("上游B群").lastRowNum)
            assertNull(workbook.getSheet("下游A群"))
        }
        WorkbookFactory.create(downstreamFile.toFile()).use { workbook ->
            assertEquals(2, workbook.numberOfSheets)
            assertEquals("30000U", sheetRow(workbook.getSheet("下游A群"), 1)[8])
            assertEquals("40000U", sheetRow(workbook.getSheet("下游B群"), 1)[8])
            assertBillTotalRow(workbook.getSheet("下游A群"), 2, "30000U", "51000")
            assertBillTotalRow(workbook.getSheet("下游B群"), 2, "40000U", "72000")
            assertEquals(2, workbook.getSheet("下游A群").lastRowNum)
            assertEquals(2, workbook.getSheet("下游B群").lastRowNum)
            assertNull(workbook.getSheet("上游A群"))
        }

        Files.deleteIfExists(upstreamFile)
        Files.deleteIfExists(downstreamFile)
    }

    @Test
    fun `rolling upstream and downstream group reports keep every group in its own sheet`() {
        val writer = BookkeepingExcelReportWriter()
        val groups = listOf(
            whatsappGroup(31, "滚球上游A群", role = "rolling_upstream"),
            whatsappGroup(32, "滚球上游B群", role = "rolling_upstream"),
            whatsappGroup(41, "滚球下游A群", role = "rolling_downstream"),
            whatsappGroup(42, "滚球下游B群", role = "rolling_downstream")
        )
        val orders = listOf(
            whatsappOrder("ROLL-UP-A-1", "rolling_upstream", groupId = 31, amount = "11000", odds = "1.50"),
            whatsappOrder("ROLL-UP-B-1", "rolling_upstream", groupId = 32, amount = "22000", odds = "1.60"),
            whatsappOrder("ROLL-DOWN-A-1", "rolling_downstream", groupId = 41, amount = "33000", odds = "1.70"),
            whatsappOrder("ROLL-DOWN-B-1", "rolling_downstream", groupId = 42, amount = "44000", odds = "1.80")
        )

        val upstreamFile = Path.of(
            writer.writeDailyReport(
                businessDate = "2026-05-08",
                taskId = 9903L,
                workspaceType = "rolling",
                reportType = "rolling_upstream_orders",
                summary = emptySummary(),
                accounts = emptyList(),
                wagers = emptyList(),
                whatsappOrders = orders,
                whatsappGroups = groups,
                reconciliationResults = emptyList()
            )
        )
        val downstreamFile = Path.of(
            writer.writeDailyReport(
                businessDate = "2026-05-08",
                taskId = 9904L,
                workspaceType = "rolling",
                reportType = "rolling_downstream_orders",
                summary = emptySummary(),
                accounts = emptyList(),
                wagers = emptyList(),
                whatsappOrders = orders,
                whatsappGroups = groups,
                reconciliationResults = emptyList()
            )
        )

        WorkbookFactory.create(upstreamFile.toFile()).use { workbook ->
            assertEquals(2, workbook.numberOfSheets)
            assertEquals("11000U", sheetRow(workbook.getSheet("滚球上游A群"), 1)[8])
            assertEquals("22000U", sheetRow(workbook.getSheet("滚球上游B群"), 1)[8])
            assertBillTotalRow(workbook.getSheet("滚球上游A群"), 2, "11000U", "-16500")
            assertBillTotalRow(workbook.getSheet("滚球上游B群"), 2, "22000U", "-35200")
            assertNull(workbook.getSheet("滚球下游A群"))
        }
        WorkbookFactory.create(downstreamFile.toFile()).use { workbook ->
            assertEquals(2, workbook.numberOfSheets)
            assertEquals("33000U", sheetRow(workbook.getSheet("滚球下游A群"), 1)[8])
            assertEquals("44000U", sheetRow(workbook.getSheet("滚球下游B群"), 1)[8])
            assertBillTotalRow(workbook.getSheet("滚球下游A群"), 2, "33000U", "56100")
            assertBillTotalRow(workbook.getSheet("滚球下游B群"), 2, "44000U", "79200")
            assertNull(workbook.getSheet("滚球上游A群"))
        }

        Files.deleteIfExists(upstreamFile)
        Files.deleteIfExists(downstreamFile)
    }

    @Test
    fun `prematch company profit report includes water sheet in same workbook`() {
        val writer = BookkeepingExcelReportWriter()
        val file = Path.of(
            writer.writeDailyReport(
                businessDate = "2026-05-08",
                taskId = 9905L,
                workspaceType = "prematch",
                reportType = "prematch_profit",
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
                    upstreamCashflow = BigDecimal("-18700"),
                    downstreamCashflow = BigDecimal("18900"),
                    waterLossAmount = BigDecimal("200"),
                    grossProfit = BigDecimal("200"),
                    companyNetProfit = BigDecimal("200")
                ),
                accounts = emptyList(),
                wagers = emptyList(),
                whatsappOrders = listOf(
                    whatsappOrder("UP-PROFIT-1", "upstream", groupId = 1, amount = "40000", odds = "1.87"),
                    whatsappOrder("DOWN-PROFIT-1", "downstream", groupId = 2, amount = "10000", odds = "1.89")
                ),
                whatsappGroups = listOf(
                    whatsappGroup(1, "上游A群", role = "upstream"),
                    whatsappGroup(2, "下游A群", role = "downstream")
                ),
                reconciliationResults = emptyList()
            )
        )

        WorkbookFactory.create(file.toFile()).use { workbook ->
            assertEquals(2, workbook.numberOfSheets)
            assertNotNull(workbook.getSheet("公司盈亏表"))
            assertNotNull(workbook.getSheet("盈亏水表格"))
            assertRowEquals(
                listOf("日期", "上游结算现金流", "下游结算现金流", "公司跟单额", "盈亏水金额", "公司总盈利"),
                sheetRow(workbook.getSheet("盈亏水表格"), 0)
            )
            assertEquals("200", sheetRow(workbook.getSheet("盈亏水表格"), 1)[4])
        }
        Files.deleteIfExists(file)
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
                    upstreamCashflow = BigDecimal("-18700"),
                    downstreamCashflow = BigDecimal("18900"),
                    waterLossAmount = BigDecimal("200"),
                    grossProfit = BigDecimal("200"),
                    companyNetProfit = BigDecimal("200")
                ),
                accounts = emptyList(),
                wagers = emptyList(),
                whatsappOrders = listOf(
                    whatsappOrder("UP-1", "upstream", groupId = 1, amount = "40000", odds = "1.87"),
                    whatsappOrder("UP-2", "upstream", groupId = 1, amount = "20000", odds = "1.80", marketText = "大 2.5"),
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
                billHeaders(),
                sheetRow(workbook.getSheet("上游群注单"), 0)
            )
            assertRowEquals(
                listOf("2026-05-08", "1", "英超", "让球", "诺丁汉 vs 曼城", "让球 主 -0.5 @ 1.87", "", "", "40000U", "赢", "-74800"),
                sheetRow(workbook.getSheet("上游群注单"), 1)
            )
            assertEquals("", sheetRow(workbook.getSheet("上游群注单"), 2)[0])
            assertEquals("大小", sheetRow(workbook.getSheet("上游群注单"), 2)[3])

            assertRowEquals(
                billHeaders(),
                sheetRow(workbook.getSheet("下游群注单"), 0)
            )

            assertRowEquals(
                billHeaders() + "订单来源",
                sheetRow(workbook.getSheet("公司盈亏表"), 0)
            )
            val waterRow = sheetRow(workbook.getSheet("公司盈亏表"), 5)
            assertEquals("盈亏水金额", waterRow[9])
            assertEquals("200", waterRow[10])
            val dailyRow = sheetRow(workbook.getSheet("公司盈亏表"), 6)
            assertEquals("日盈亏", dailyRow[9])
            assertEquals("200", dailyRow[10])
            (0 until workbook.numberOfSheets).forEach { index ->
                assertRequestedColumnWidths(workbook.getSheetAt(index))
                assertSheetValuesCentered(workbook.getSheetAt(index))
            }
        }
        Files.deleteIfExists(file)
    }

    @Test
    fun `rolling group sheet leaves score blank without titan007 match`() {
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
                billHeaders(),
                sheetRow(workbook.getSheet("滚球群注单"), 0)
            )
            assertRowEquals(
                listOf("2026-05-08", "1", "英超", "让球", "诺丁汉 vs 曼城", "让球 主 -0.5 @ 0.83", "0-1", "", "10000U", "赢", "8300"),
                sheetRow(workbook.getSheet("滚球群注单"), 1)
            )
            assertRequestedColumnWidths(workbook.getSheet("滚球群注单"))
        }
        Files.deleteIfExists(file)
    }

    @Test
    fun `excel writer uses titan007 score workbook instead of message score`(@TempDir scoreDir: Path) {
        writeTitanScoreWorkbook(scoreDir, "2026-05-08", "英超", "诺丁汉", "曼城", "2-1")
        val writer = BookkeepingExcelReportWriter(BookkeepingTitan007ScoreResultReader(listOf(scoreDir)))
        val file = Path.of(
            writer.writeDailyReport(
                businessDate = "2026-05-08",
                taskId = 9804L,
                workspaceType = "prematch",
                reportType = "upstream_orders",
                summary = emptySummary(),
                accounts = emptyList(),
                wagers = emptyList(),
                whatsappOrders = listOf(
                    whatsappOrder(
                        "UP-SCORE-1",
                        "upstream",
                        groupId = 1,
                        amount = "40000",
                        odds = "1.87",
                        rawMessage = "实际比分: 0-0"
                    )
                ),
                whatsappGroups = listOf(whatsappGroup(1, "上游A群", role = "upstream")),
                reconciliationResults = emptyList()
            )
        )

        WorkbookFactory.create(file.toFile()).use { workbook ->
            assertEquals("2-1", sheetRow(workbook.getSheet("上游A群"), 1)[7])
        }
        Files.deleteIfExists(file)
    }

    @Test
    fun `excel writer leaves actual score blank when titan007 score is absent`(@TempDir scoreDir: Path) {
        val writer = BookkeepingExcelReportWriter(BookkeepingTitan007ScoreResultReader(listOf(scoreDir)))
        val file = Path.of(
            writer.writeDailyReport(
                businessDate = "2026-05-08",
                taskId = 9805L,
                workspaceType = "prematch",
                reportType = "upstream_orders",
                summary = emptySummary(),
                accounts = emptyList(),
                wagers = emptyList(),
                whatsappOrders = listOf(
                    whatsappOrder(
                        "UP-SCORE-2",
                        "upstream",
                        groupId = 1,
                        amount = "40000",
                        odds = "1.87",
                        rawMessage = "实际比分: 9-9"
                    )
                ),
                whatsappGroups = listOf(whatsappGroup(1, "上游A群", role = "upstream")),
                reconciliationResults = emptyList()
            )
        )

        WorkbookFactory.create(file.toFile()).use { workbook ->
            assertEquals("", sheetRow(workbook.getSheet("上游A群"), 1)[7])
        }
        Files.deleteIfExists(file)
    }

    @Test
    fun `excel writer derives settlement result and upstream profit from titan007 score and market`(@TempDir scoreDir: Path) {
        writeTitanScoreWorkbook(
            scoreDir,
            "2026-05-10",
            listOf(
                ScoreFixture("阿塞超", "卡巴拉", "卡拉巴克", "1-1"),
                ScoreFixture("斯伐超降", "扑雷索夫", "斯卡利卡", "3-0"),
                ScoreFixture("捷甲冠", "布拉格斯拉维亚", "布拉格斯巴达", "3-2"),
                ScoreFixture("法丙", "普瑞兰斯", "瓦朗谢纳", "1-0"),
                ScoreFixture("瑞典甲", "桑德维根斯", "卢恩斯基尔", "1-0"),
                ScoreFixture("巴拉甲春", "五月二日体育会", "鲁毕奥", "2-0"),
                ScoreFixture("玻利甲", "皇家托马亚波", "圣安东尼奥布鲁布鲁", "1-0")
            )
        )
        val writer = BookkeepingExcelReportWriter(BookkeepingTitan007ScoreResultReader(listOf(scoreDir)))
        val file = Path.of(
            writer.writeDailyReport(
                businessDate = "2026-05-10",
                taskId = 9806L,
                workspaceType = "prematch",
                reportType = "upstream_orders",
                summary = emptySummary(),
                accounts = emptyList(),
                wagers = emptyList(),
                whatsappOrders = listOf(
                    whatsappOrder("AUTO-1", "upstream", groupId = 1, amount = "7500", odds = "0.9", marketText = "大 2.5 / 3", matchName = "卡巴拉 v 卡拉巴克", leagueName = "阿塞拜疆超级联赛", settlementResult = null),
                    whatsappOrder("AUTO-2", "upstream", groupId = 1, amount = "2000", odds = "0.94", marketText = "大 2 / 2.5", matchName = "扑雷索夫 v 斯卡利卡", leagueName = "斯洛伐克超级联赛-附加赛", settlementResult = null),
                    whatsappOrder("AUTO-3", "upstream", groupId = 1, amount = "7500", odds = "0.89", marketText = "大 2.5", matchName = "布拉格斯拉维亚 v 布拉格斯巴达", leagueName = "捷克甲组联赛-附加赛", settlementResult = null),
                    whatsappOrder("AUTO-4", "upstream", groupId = 1, amount = "7500", odds = "0.86", marketText = "瓦朗谢讷 +0.5", matchName = "布尔格佩罗纳斯 v 瓦朗谢讷", leagueName = "法国全国联赛", settlementResult = null),
                    whatsappOrder("AUTO-5", "upstream", groupId = 1, amount = "7500", odds = "0.93", marketText = "大 2.5 / 3", matchName = "桑德维肯斯 v 卢恩斯基尔", leagueName = "瑞典超级甲组联赛", settlementResult = null),
                    whatsappOrder("AUTO-6", "upstream", groupId = 1, amount = "7500", odds = "0.82", marketText = "马约 0", matchName = "马约 v 鲁毕奥", leagueName = "巴拉圭甲组联赛", settlementResult = null),
                    whatsappOrder("AUTO-7", "upstream", groupId = 1, amount = "2000", odds = "0.88", marketText = "小 3 / 3.5", matchName = "皇家托马亚波 v 圣安东尼奥布鲁布鲁", leagueName = "玻利维亚甲组联赛", settlementResult = null)
                ),
                whatsappGroups = listOf(whatsappGroup(1, "上游A群", role = "upstream")),
                reconciliationResults = emptyList()
            )
        )

        WorkbookFactory.create(file.toFile()).use { workbook ->
            val sheet = workbook.getSheet("上游A群")
            assertEquals("输", sheetRow(sheet, 1)[9])
            assertEquals("7500", sheetRow(sheet, 1)[10])
            assertEquals("赢", sheetRow(sheet, 2)[9])
            assertEquals("-1880", sheetRow(sheet, 2)[10])
            assertEquals("赢", sheetRow(sheet, 3)[9])
            assertEquals("-6675", sheetRow(sheet, 3)[10])
            assertEquals("输", sheetRow(sheet, 4)[9])
            assertEquals("7500", sheetRow(sheet, 4)[10])
            assertEquals("输", sheetRow(sheet, 5)[9])
            assertEquals("7500", sheetRow(sheet, 5)[10])
            assertEquals("赢", sheetRow(sheet, 6)[9])
            assertEquals("-6150", sheetRow(sheet, 6)[10])
            assertEquals("赢", sheetRow(sheet, 7)[9])
            assertEquals("-1760", sheetRow(sheet, 7)[10])
            assertBillTotalRow(sheet, 8, "41500U", "6035")
        }
        Files.deleteIfExists(file)
    }

    @Test
    fun `excel writer strips odds suffix before deriving settlement from market text`(@TempDir scoreDir: Path) {
        writeTitanScoreWorkbook(scoreDir, "2026-05-10", "波兰超", "琴斯托霍瓦", "哥罗纳", "2-0")
        val writer = BookkeepingExcelReportWriter(BookkeepingTitan007ScoreResultReader(listOf(scoreDir)))
        val file = Path.of(
            writer.writeDailyReport(
                businessDate = "2026-05-10",
                taskId = 9808L,
                workspaceType = "rolling",
                reportType = "rolling_group_orders",
                summary = emptySummary(),
                accounts = emptyList(),
                wagers = emptyList(),
                whatsappOrders = listOf(
                    whatsappOrder(
                        "ROLLING-ODDS-SUFFIX",
                        "rolling_company",
                        groupId = 1,
                        amount = "104000",
                        odds = "1.00",
                        rawMessage = """
                            1.足球 (滚球) 大 / 小 (0 - 0)（大鬼）
                            波兰超级联赛
                            琴斯托霍瓦 v 科罗纳
                            大 2.5 @ 1.00
                            部门跟104000r 确认
                        """.trimIndent(),
                        marketText = "大 2.5 @ 1.00",
                        matchName = "琴斯托霍瓦 v 科罗纳",
                        leagueName = "波兰超级联赛",
                        settlementResult = null
                    )
                ),
                whatsappGroups = listOf(whatsappGroup(1, "公司部门", role = "rolling_company")),
                reconciliationResults = emptyList()
            )
        )

        WorkbookFactory.create(file.toFile()).use { workbook ->
            val row = sheetRow(workbook.getSheet("滚球群注单"), 1)
            assertEquals("大 2.5 @ 1.00", row[5])
            assertEquals("0-0", row[6])
            assertEquals("2-0", row[7])
            assertEquals("104000U", row[8])
            assertEquals("输", row[9])
            assertEquals("-104000", row[10])
            assertEquals("总投注额度", sheetRow(workbook.getSheet("滚球群注单"), 2)[7])
            assertEquals("104000U", sheetRow(workbook.getSheet("滚球群注单"), 2)[8])
            assertEquals("总盈亏", sheetRow(workbook.getSheet("滚球群注单"), 2)[9])
            assertEquals("-104000", sheetRow(workbook.getSheet("滚球群注单"), 2)[10])
        }
        Files.deleteIfExists(file)
    }

    @Test
    fun `auto settlement results are reused by rolling summary calculations`(@TempDir scoreDir: Path) {
        writeTitanScoreWorkbook(scoreDir, "2026-05-10", "波兰超", "琴斯托霍瓦", "哥罗纳", "2-0")
        val writer = BookkeepingExcelReportWriter(BookkeepingTitan007ScoreResultReader(listOf(scoreDir)))
        val orders = writer.withAutoSettlementResults(
            "2026-05-10",
            listOf(
                whatsappOrder(
                    "ROLLING-SUMMARY-AUTO",
                    "rolling_company",
                    groupId = 1,
                    amount = "104000",
                    odds = "0.9",
                    marketText = "大 2.5 @ 0.9",
                    matchName = "琴斯托霍瓦 v 科罗纳",
                    leagueName = "波兰超级联赛",
                    settlementResult = null
                )
            )
        )

        assertEquals("输", orders.single().settlementResult)

        val summary = BookkeepingCalculator().buildRollingSummary(
            accounts = listOf(BookkeepingCalculatorAccount(lastLoginStatus = "success")),
            wagers = emptyList(),
            whatsappOrders = orders.map {
                BookkeepingCalculatorWhatsappOrder(
                    id = it.id,
                    groupId = it.groupId,
                    orderKey = it.orderKey,
                    direction = it.direction,
                    parseStatus = it.parseStatus,
                    leagueName = it.leagueName,
                    matchName = it.matchName,
                    marketText = it.marketText,
                    oddsValue = it.oddsValue,
                    amount = it.amount,
                    settlementResult = it.settlementResult
                )
            }
        )

        assertEquals("-104000", summary.rollingGroupSettlement.stripTrailingZeros().toPlainString())
        assertEquals("104000", summary.todayProfit.stripTrailingZeros().toPlainString())
    }

    @Test
    fun `excel writer derives asian half win push and half lose outcomes from titan007 score`(@TempDir scoreDir: Path) {
        writeTitanScoreWorkbook(
            scoreDir,
            "2026-05-10",
            listOf(
                ScoreFixture("测试联赛", "大赢半主", "大赢半客", "3-0"),
                ScoreFixture("测试联赛", "走盘主", "走盘客", "1-1"),
                ScoreFixture("测试联赛", "小赢半主", "小赢半客", "1-1"),
                ScoreFixture("测试联赛", "小输半主", "小输半客", "2-1"),
                ScoreFixture("测试联赛", "让赢半主", "让赢半客", "1-0"),
                ScoreFixture("测试联赛", "让走主", "让走客", "1-1")
            )
        )
        val writer = BookkeepingExcelReportWriter(BookkeepingTitan007ScoreResultReader(listOf(scoreDir)))
        val file = Path.of(
            writer.writeDailyReport(
                businessDate = "2026-05-10",
                taskId = 9807L,
                workspaceType = "prematch",
                reportType = "upstream_orders",
                summary = emptySummary(),
                accounts = emptyList(),
                wagers = emptyList(),
                whatsappOrders = listOf(
                    whatsappOrder("HALF-1", "upstream", groupId = 1, amount = "1000", odds = "1", marketText = "大 2.5 / 3", matchName = "大赢半主 v 大赢半客", leagueName = "测试联赛", settlementResult = null),
                    whatsappOrder("HALF-2", "upstream", groupId = 1, amount = "1000", odds = "1", marketText = "大 2", matchName = "走盘主 v 走盘客", leagueName = "测试联赛", settlementResult = null),
                    whatsappOrder("HALF-3", "upstream", groupId = 1, amount = "1000", odds = "1", marketText = "小 2 / 2.5", matchName = "小赢半主 v 小赢半客", leagueName = "测试联赛", settlementResult = null),
                    whatsappOrder("HALF-4", "upstream", groupId = 1, amount = "1000", odds = "1", marketText = "小 2.5 / 3", matchName = "小输半主 v 小输半客", leagueName = "测试联赛", settlementResult = null),
                    whatsappOrder("HALF-5", "upstream", groupId = 1, amount = "1000", odds = "1", marketText = "让赢半主 -0.5 / -1", matchName = "让赢半主 v 让赢半客", leagueName = "测试联赛", settlementResult = null),
                    whatsappOrder("HALF-6", "upstream", groupId = 1, amount = "1000", odds = "1", marketText = "让走主 0", matchName = "让走主 v 让走客", leagueName = "测试联赛", settlementResult = null)
                ),
                whatsappGroups = listOf(whatsappGroup(1, "上游A群", role = "upstream")),
                reconciliationResults = emptyList()
            )
        )

        WorkbookFactory.create(file.toFile()).use { workbook ->
            val sheet = workbook.getSheet("上游A群")
            assertEquals("赢半", sheetRow(sheet, 1)[9])
            assertEquals("-500", sheetRow(sheet, 1)[10])
            assertEquals("走盘", sheetRow(sheet, 2)[9])
            assertEquals("0", sheetRow(sheet, 2)[10])
            assertEquals("赢半", sheetRow(sheet, 3)[9])
            assertEquals("-500", sheetRow(sheet, 3)[10])
            assertEquals("输半", sheetRow(sheet, 4)[9])
            assertEquals("500", sheetRow(sheet, 4)[10])
            assertEquals("赢半", sheetRow(sheet, 5)[9])
            assertEquals("-500", sheetRow(sheet, 5)[10])
            assertEquals("走盘", sheetRow(sheet, 6)[9])
            assertEquals("0", sheetRow(sheet, 6)[10])
            assertBillTotalRow(sheet, 7, "6000U", "-1000")
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
                billHeaders(),
                sheetRow(workbook.getSheet("皇冠注单"), 0)
            )
            assertRowEquals(
                listOf("2026-05-08", "1", "日本 J1百年构想联赛", "让球", "清水心跳 vs 大阪樱花", "让球 大阪樱花 +0 / 0.5 @ 0.83", "", "", "10000U", "赢", "8300"),
                sheetRow(workbook.getSheet("皇冠注单"), 1)
            )
            assertEquals("", sheetRow(workbook.getSheet("皇冠注单"), 2)[0])
            assertEquals("2", sheetRow(workbook.getSheet("皇冠注单"), 2)[1])
            assertEquals("RB大宫松鼠 vs 磐城", sheetRow(workbook.getSheet("皇冠注单"), 2)[4])
            assertEquals("输", sheetRow(workbook.getSheet("皇冠注单"), 2)[9])
            assertBillTotalRow(workbook.getSheet("皇冠注单"), 3, "20000U", "-1700")
            assertRequestedColumnWidths(workbook.getSheet("皇冠注单"))
            assertSheetValuesCentered(workbook.getSheet("皇冠注单"))
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

    @Test
    fun `telegram bridge uses web qr login without api id or api hash`() {
        val bridge = Files.readString(Path.of("../telegram-bridge/server.mjs"))
        listOf(
            "TelegramClient",
            "StringSession",
            "QRCode.toDataURL",
            "TELEGRAM_API_ID",
            "TELEGRAM_API_HASH",
            "client.getDialogs",
            "readMessagesForChats"
        ).forEach { marker ->
            assertTrue(bridge.contains(marker), "missing telegram api bridge marker $marker")
        }
        assertTrue(!bridge.contains("web.telegram.org"), "telegram api bridge should not use Telegram Web QR login")
        assertTrue(!bridge.contains("puppeteer.launch"), "telegram api bridge should not launch Telegram Web")
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
        rawMessage: String = "测试订单",
        marketText: String = "让球 主 -0.5",
        matchName: String = "诺丁汉 vs 曼城",
        leagueName: String = "英超",
        settlementResult: String? = "赢"
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
        leagueName = leagueName,
        matchName = matchName,
        marketText = marketText,
        oddsValue = BigDecimal(odds),
        amount = BigDecimal(amount),
        currency = "USDT",
        parseStatus = "valid",
        settlementResult = settlementResult,
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

    private fun writeTitanScoreWorkbook(
        dir: Path,
        businessDate: String,
        leagueName: String,
        homeTeam: String,
        awayTeam: String,
        score: String
    ): Path = writeTitanScoreWorkbook(dir, businessDate, listOf(ScoreFixture(leagueName, homeTeam, awayTeam, score)))

    private fun writeTitanScoreWorkbook(
        dir: Path,
        businessDate: String,
        scores: List<ScoreFixture>
    ): Path {
        Files.createDirectories(dir)
        val file = dir.resolve("${businessDate.replace("-", "")}_比赛数据.xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("比赛数据")
            val header = sheet.createRow(0)
            listOf("日期", "联赛", "详细时间", "主队", "比分", "客队").forEachIndexed { index, value ->
                header.createCell(index).setCellValue(value)
            }
            scores.forEachIndexed { rowIndex, fixture ->
                val row = sheet.createRow(rowIndex + 1)
                listOf(businessDate, fixture.leagueName, "20:00", fixture.homeTeam, fixture.score, fixture.awayTeam).forEachIndexed { index, value ->
                    row.createCell(index).setCellValue(value)
                }
            }
            Files.newOutputStream(file).use { workbook.write(it) }
        }
        return file
    }

    private data class ScoreFixture(
        val leagueName: String,
        val homeTeam: String,
        val awayTeam: String,
        val score: String
    )

    private fun sheetRow(sheet: org.apache.poi.ss.usermodel.Sheet, rowIndex: Int): List<String> {
        val formatter = DataFormatter()
        val row = sheet.getRow(rowIndex)
        return (0 until row.lastCellNum).map { index -> formatter.formatCellValue(row.getCell(index)) }
    }

    private fun assertRowEquals(expected: List<String>, actual: List<String>) {
        assertEquals(expected, actual.take(expected.size))
    }

    private fun billHeaders() =
        listOf("日期", "序号", "联赛类型", "投注类型", "比赛队伍", "投注盘口及赔率", "投注时比分", "实际比分", "投注额度", "赛果", "盈亏")

    private fun assertRequestedColumnWidths(sheet: Sheet) {
        val formatter = DataFormatter()
        val header = sheet.getRow(0)
        for (index in 0 until header.lastCellNum) {
            val headerText = formatter.formatCellValue(header.getCell(index))
            val expectedWidth = when (headerText) {
                "联赛类型" -> 25
                "比赛队伍" -> 35
                "投注盘口及赔率" -> 35
                else -> 20
            }
            assertEquals(expectedWidth * 256, sheet.getColumnWidth(index), "wrong width for ${sheet.sheetName}!$headerText")
        }
    }

    private fun assertBillTotalRow(sheet: Sheet, rowIndex: Int, totalStake: String, totalProfit: String) {
        val row = sheetRow(sheet, rowIndex)
        assertEquals("总投注额度", row[7])
        assertEquals(totalStake, row[8])
        assertEquals("总盈亏", row[9])
        assertEquals(totalProfit, row[10])
    }

    private fun assertSheetValuesCentered(sheet: Sheet) {
        val formatter = DataFormatter()
        (0..sheet.lastRowNum).forEach rows@{ rowIndex ->
            val row = sheet.getRow(rowIndex) ?: return@rows
            (0 until row.lastCellNum).forEach cells@{ cellIndex ->
                val cell = row.getCell(cellIndex) ?: return@cells
                if (formatter.formatCellValue(cell).isNotBlank()) {
                    assertEquals(
                        HorizontalAlignment.CENTER,
                        cell.cellStyle.alignment,
                        "cell ${sheet.sheetName}!${cell.address} should be centered"
                    )
                }
            }
        }
    }
}
