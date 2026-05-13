package com.wrbug.polymarketbot.service.bookkeeping

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.wrbug.polymarketbot.dto.*
import com.wrbug.polymarketbot.entity.BookkeepingCrownAccount
import com.wrbug.polymarketbot.entity.BookkeepingCrownWager
import com.wrbug.polymarketbot.entity.BookkeepingReconciliationResult
import com.wrbug.polymarketbot.entity.BookkeepingTask
import com.wrbug.polymarketbot.entity.BookkeepingWhatsappGroup
import com.wrbug.polymarketbot.entity.BookkeepingWhatsappOrder
import com.wrbug.polymarketbot.repository.BookkeepingCrownAccountRepository
import com.wrbug.polymarketbot.repository.BookkeepingCrownWagerRepository
import com.wrbug.polymarketbot.repository.BookkeepingTaskRepository
import com.wrbug.polymarketbot.repository.BookkeepingWhatsappGroupRepository
import com.wrbug.polymarketbot.repository.BookkeepingWhatsappOrderRepository
import com.wrbug.polymarketbot.repository.BookkeepingReconciliationResultRepository
import com.wrbug.polymarketbot.util.CryptoUtils
import com.wrbug.polymarketbot.util.toJson
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.core.io.FileSystemResource
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Service
class BookkeepingService(
    private val crownAccountRepository: BookkeepingCrownAccountRepository,
    private val crownWagerRepository: BookkeepingCrownWagerRepository,
    private val whatsappGroupRepository: BookkeepingWhatsappGroupRepository,
    private val whatsappOrderRepository: BookkeepingWhatsappOrderRepository,
    private val taskRepository: BookkeepingTaskRepository,
    private val reconciliationRepository: BookkeepingReconciliationResultRepository,
    private val crownLoginClient: CrownLoginClient,
    private val cryptoUtils: CryptoUtils,
    private val calculator: BookkeepingCalculator,
    private val excelReportWriter: BookkeepingExcelReportWriter,
    private val titan007ScoreCrawler: BookkeepingTitan007ScoreCrawler,
    private val objectMapper: ObjectMapper,
    private val telegramApiConfigStore: BookkeepingTelegramApiConfigStore
) {
    private companion object {
        const val MESSAGE_SOURCE_WHATSAPP = "whatsapp"
        const val MESSAGE_SOURCE_TELEGRAM = "telegram"
        val PREMATCH_GROUP_ROLES = setOf("upstream", "downstream", "company_follow")
        val ROLLING_GROUP_ROLES = setOf("rolling", "rolling_upstream", "rolling_downstream", "rolling_company")
        val BOOKKEEPING_GROUP_ROLES = setOf("pending", "ignored") + PREMATCH_GROUP_ROLES + ROLLING_GROUP_ROLES
    }

    private data class WhatsappBridgeGroupsResponse(
        val connected: Boolean = false,
        val status: String = "",
        val message: String = "",
        val groups: List<WhatsappBridgeGroup> = emptyList()
    )

    private data class WhatsappBridgeStatusResponse(
        val connected: Boolean = false,
        val status: String = "",
        val message: String = "",
        val qr: String? = null
    )

    private data class WhatsappBridgeMessagesRequest(
        val chatIds: List<String>,
        val startTime: Long,
        val endTime: Long,
        val limit: Int
    )

    private data class WhatsappBridgeMessagesResponse(
        val connected: Boolean = false,
        val status: String = "",
        val message: String = "",
        val messages: List<WhatsappBridgeMessage> = emptyList()
    )

    private data class WhatsappBridgeMessage(
        val chatId: String? = null,
        val chatName: String? = null,
        val messageId: String? = null,
        val timestamp: Long? = null,
        val from: String? = null,
        val author: String? = null,
        val body: String? = null
    )

    private data class ParsedWhatsappOrderBlock(
        val rawMessage: String,
        val leagueName: String?,
        val matchName: String?,
        val marketText: String?,
        val oddsValue: BigDecimal?,
        val amount: BigDecimal?,
        val parseStatus: String
    )

    private data class WhatsappBridgeGroup(
        val id: String? = null,
        val name: String? = null
    )

    fun dashboard(businessDate: String? = null, workspaceTypeValue: String? = null): BookkeepingDashboardDto {
        val date = businessDate?.trim()?.takeIf { it.isNotEmpty() } ?: todayText()
        validateBusinessDate(date)
        val workspaceType = normaliseWorkspaceType(workspaceTypeValue ?: "prematch")
        val accounts = listCrownAccounts()
        val whatsappGroups = listWhatsappGroups()
        val telegramGroups = listTelegramGroups()
        val messageGroups = whatsappGroups + telegramGroups
        val wagers = crownWagerRepository.findByBusinessDateOrderByCreatedAtDesc(date).map { it.toDto() }
        val allWhatsappOrders = BookkeepingWhatsappOrderPrecision.deduplicateExistingOrders(
            whatsappOrderRepository.findByBusinessDateOrderByCreatedAtDesc(date)
        )
        val rawWhatsappOrders = ordersForWorkspace(workspaceType, allWhatsappOrders.map { it.toDto() })
        val whatsappOrders = excelReportWriter.withAutoSettlementResults(date, rawWhatsappOrders)
        val latestTask = taskRepository.findTopByBusinessDateAndWorkspaceTypeOrderByCreatedAtDesc(date, workspaceType)
        val reconciliationResults = latestTask?.id
            ?.let { reconciliationRepository.findByTaskIdOrderByCreatedAtAsc(it).map { item -> item.toDto() } }
            ?: emptyList()
        val differenceCount = reconciliationResults.count { it.issueType != "matched" }
        return BookkeepingDashboardDto(
            businessDate = date,
            summary = if (workspaceType == "rolling") {
                buildRollingSummaryDto(accounts, wagers, whatsappOrders)
            } else {
                buildPrematchSummaryDto(accounts, wagers, whatsappOrders, differenceCount, messageGroups)
            },
            crownAccounts = accounts,
            whatsappGroups = whatsappGroups,
            telegramGroups = telegramGroups,
            crownWagers = wagers,
            whatsappOrders = whatsappOrders,
            reconciliationResults = reconciliationResults,
            tasks = taskRepository.findTop50ByWorkspaceTypeOrderByCreatedAtDesc(workspaceType).map { it.toDto() }
        )
    }

    fun listCrownAccounts(): List<BookkeepingCrownAccountDto> {
        return crownAccountRepository.findAllByOrderByDisplayNameAsc().map { it.toDto() }
    }

    @Transactional
    fun saveCrownAccount(request: SaveBookkeepingCrownAccountRequest): BookkeepingCrownAccountDto {
        val now = System.currentTimeMillis()
        val accountKey = cleanRequired(request.accountKey, "accountKey")
        val existing = request.id?.let { crownAccountRepository.findById(it).orElse(null) }
            ?: crownAccountRepository.findByAccountKey(accountKey)
        val password = request.password?.trim()?.takeIf { it.isNotEmpty() }?.let { encryptCrownPassword(it) }
            ?: existing?.password
        val saved = crownAccountRepository.save(
            BookkeepingCrownAccount(
                id = existing?.id,
                accountKey = accountKey,
                displayName = cleanRequired(request.displayName, "displayName"),
                baseUrl = normaliseBaseUrl(request.baseUrl),
                username = cleanRequired(request.username, "username"),
                password = password,
                enabled = request.enabled,
                timezone = request.timezone.trim().ifEmpty { "GMT-4" },
                lastLoginStatus = existing?.lastLoginStatus,
                lastLoginMessage = existing?.lastLoginMessage,
                lastLoginAt = existing?.lastLoginAt,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
        return saved.toDto()
    }

    @Transactional
    fun testCrownLogin(accountId: Long): BookkeepingLoginTestResultDto {
        val account = crownAccountRepository.findById(accountId).orElseThrow {
            IllegalArgumentException("Crown account not found")
        }
        val checkedAt = System.currentTimeMillis()
        val password = account.password?.takeIf { it.isNotBlank() }?.let { decryptCrownPassword(it) }
            ?: throw IllegalArgumentException("Crown account password is empty")
        val result = runCatching {
            crownLoginClient.login(
                CrownLoginConfig(
                    displayName = account.displayName,
                    username = account.username,
                    password = password,
                    baseUrl = account.baseUrl
                )
            )
        }
        val ok = result.isSuccess
        val message = result.exceptionOrNull()?.message ?: "login ok"
        crownAccountRepository.save(
            account.copy(
                lastLoginStatus = if (ok) "success" else "failed",
                lastLoginMessage = message,
                lastLoginAt = checkedAt,
                updatedAt = checkedAt
            )
        )
        return BookkeepingLoginTestResultDto(account.id ?: accountId, ok, message, checkedAt)
    }

    @Transactional
    fun importCrownWagers(request: ImportBookkeepingCrownWagersRequest): ImportBookkeepingCrownWagersResultDto {
        validateBusinessDate(request.businessDate)
        val accountKey = cleanRequired(request.accountKey, "accountKey")
        val account = crownAccountRepository.findByAccountKey(accountKey)
            ?: throw IllegalArgumentException("Crown account not found")
        val accountId = account.id ?: throw IllegalArgumentException("Crown account id is empty")
        val now = System.currentTimeMillis()
        var imported = 0
        var updated = 0

        val rows = request.wagers.map { item ->
            val ticketId = cleanRequired(item.ticketId, "ticketId")
            val existing = crownWagerRepository.findByAccountIdAndBusinessDateAndTicketId(
                accountId,
                request.businessDate,
                ticketId
            )
            if (existing == null) imported += 1 else updated += 1
            BookkeepingCrownWager(
                id = existing?.id,
                taskId = existing?.taskId,
                accountId = accountId,
                businessDate = request.businessDate,
                ticketId = ticketId,
                wagerTime = item.wagerTime,
                leagueName = item.leagueName?.trim()?.takeIf { it.isNotEmpty() },
                homeTeam = item.homeTeam?.trim()?.takeIf { it.isNotEmpty() },
                awayTeam = item.awayTeam?.trim()?.takeIf { it.isNotEmpty() },
                marketType = item.marketType?.trim()?.takeIf { it.isNotEmpty() },
                selectionName = item.selectionName?.trim()?.takeIf { it.isNotEmpty() },
                oddsValue = item.oddsValue,
                stakeAmount = item.stakeAmount,
                winLossAmount = item.winLossAmount,
                currency = item.currency?.trim()?.takeIf { it.isNotEmpty() },
                status = item.status.trim().ifEmpty { "unknown" },
                rawPayloadJson = item.rawPayloadJson,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        }
        crownWagerRepository.saveAll(rows)
        return ImportBookkeepingCrownWagersResultDto(request.businessDate, accountKey, imported, updated)
    }

    fun listWhatsappGroups(): List<BookkeepingWhatsappGroupDto> {
        return listMessageGroups(MESSAGE_SOURCE_WHATSAPP)
    }

    fun listTelegramGroups(): List<BookkeepingTelegramGroupDto> {
        return listMessageGroups(MESSAGE_SOURCE_TELEGRAM)
    }

    private fun listMessageGroups(sourceType: String): List<BookkeepingWhatsappGroupDto> {
        return whatsappGroupRepository.findAllBySourceTypeOrderByDisplayNameAsc(sourceType).map { it.toDto() }
    }

    @Transactional
    fun saveWhatsappGroup(request: SaveBookkeepingWhatsappGroupRequest): BookkeepingWhatsappGroupDto {
        return saveMessageGroup(request, MESSAGE_SOURCE_WHATSAPP)
    }

    @Transactional
    fun saveTelegramGroup(request: SaveBookkeepingTelegramGroupRequest): BookkeepingTelegramGroupDto {
        return saveMessageGroup(request, MESSAGE_SOURCE_TELEGRAM)
    }

    private fun saveMessageGroup(
        request: SaveBookkeepingWhatsappGroupRequest,
        sourceType: String
    ): BookkeepingWhatsappGroupDto {
        val now = System.currentTimeMillis()
        val groupKey = cleanRequired(request.groupKey, "groupKey")
        val existing = request.id?.let { whatsappGroupRepository.findById(it).orElse(null) }
            ?: whatsappGroupRepository.findByGroupKeyAndSourceType(groupKey, sourceType)
        val role = normaliseGroupRole(request.role)
        val currency = normaliseCurrency(request.currency)
        val rebateRule = normaliseRebateRule(request.rebateRule)
        require(request.exchangeRate > BigDecimal.ZERO) { "exchangeRate must be greater than 0" }
        require(request.rebatePoints >= BigDecimal.ZERO) { "rebatePoints must be greater than or equal to 0" }
        require(request.rebateRate >= BigDecimal.ZERO) { "rebateRate must be greater than or equal to 0" }
        val configured = role != "pending"
        val saved = whatsappGroupRepository.save(
            BookkeepingWhatsappGroup(
                id = existing?.id,
                groupKey = groupKey,
                sourceType = sourceType,
                sourceChatId = request.sourceChatId?.trim()?.takeIf { it.isNotEmpty() } ?: existing?.sourceChatId,
                displayName = cleanRequired(request.displayName, "displayName"),
                chatName = cleanRequired(request.chatName, "chatName"),
                role = role,
                currency = currency,
                exchangeRate = request.exchangeRate,
                rebatePoints = request.rebatePoints,
                rebateRate = request.rebateRate,
                rebateRule = rebateRule,
                lastScannedMessageId = request.lastScannedMessageId?.trim()?.takeIf { it.isNotEmpty() }
                    ?: existing?.lastScannedMessageId,
                configured = configured,
                enabled = request.enabled,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
        return saved.toDto()
    }

    fun whatsappBootstrap(): BookkeepingWhatsappBootstrapDto {
        return BookkeepingWhatsappBootstrapDto(
            crownAccounts = crownAccountRepository.findByEnabledOrderByDisplayNameAsc(true).map { it.toDto() },
            whatsappGroups = whatsappGroupRepository
                .findByEnabledAndSourceTypeOrderByDisplayNameAsc(true, MESSAGE_SOURCE_WHATSAPP)
                .map { it.toDto() },
            endpoints = mapOf(
                "importOrders" to "/api/bookkeeping/whatsapp/orders/import",
                "syncChats" to "/api/bookkeeping/whatsapp/chats/sync",
                "scanMessages" to "/api/bookkeeping/whatsapp/scan",
                "runTask" to "/api/bookkeeping/tasks/run",
                "dashboard" to "/api/bookkeeping/dashboard"
            )
        )
    }

    @Transactional
    fun syncWhatsappChats(): BookkeepingWhatsappChatSyncResultDto {
        val bridgeResponse = runCatching { fetchWhatsappBridgeGroups() }.getOrNull()
            ?: return BookkeepingWhatsappChatSyncResultDto(
                connected = false,
                status = "bridge_unavailable",
                message = "未检测到本机 WhatsApp 读取服务，当前显示已保存的群聊配置。",
                groups = listWhatsappGroups()
            )

        if (!bridgeResponse.connected) {
            return BookkeepingWhatsappChatSyncResultDto(
                connected = false,
                status = bridgeResponse.status.ifEmpty { "not_ready" },
                message = bridgeResponse.message.ifEmpty { "WhatsApp 尚未登录或尚未读取到群聊。" },
                groups = listWhatsappGroups()
            )
        }

        val now = System.currentTimeMillis()
        val existingGroups = whatsappGroupRepository.findAllBySourceTypeOrderByDisplayNameAsc(MESSAGE_SOURCE_WHATSAPP)
        val bySourceChatId = existingGroups
            .mapNotNull { group -> group.sourceChatId?.takeIf { it.isNotBlank() }?.let { it to group } }
            .toMap()
        val byGroupKey = existingGroups.associateBy { it.groupKey }

        val syncedGroups = bridgeResponse.groups.mapNotNull { chat ->
            val sourceChatId = chat.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val chatName = chat.name?.trim()?.takeIf { it.isNotEmpty() } ?: sourceChatId
            val groupKey = buildWhatsappGroupKey(sourceChatId)
            val existing = bySourceChatId[sourceChatId] ?: byGroupKey[groupKey]
            BookkeepingWhatsappGroup(
                id = existing?.id,
                groupKey = existing?.groupKey ?: groupKey,
                sourceType = MESSAGE_SOURCE_WHATSAPP,
                sourceChatId = sourceChatId.take(128),
                displayName = existing?.displayName ?: chatName.take(128),
                chatName = chatName.take(255),
                role = existing?.takeIf { it.configured }?.role ?: "pending",
                currency = existing?.currency ?: "USDT",
                exchangeRate = existing?.exchangeRate ?: BigDecimal.ONE,
                rebatePoints = existing?.rebatePoints ?: BigDecimal.ZERO,
                rebateRate = existing?.rebateRate ?: BigDecimal.ZERO,
                rebateRule = existing?.rebateRule ?: "none",
                lastScannedMessageId = existing?.lastScannedMessageId,
                configured = existing?.configured ?: false,
                enabled = existing?.enabled ?: true,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        }
        if (syncedGroups.isNotEmpty()) {
            whatsappGroupRepository.saveAll(syncedGroups)
        }

        return BookkeepingWhatsappChatSyncResultDto(
            connected = true,
            status = bridgeResponse.status.ifEmpty { "ready" },
            message = "已同步 ${syncedGroups.size} 个 WhatsApp 群聊。",
            groups = listWhatsappGroups()
        )
    }

    fun whatsappStatus(): BookkeepingWhatsappStatusDto {
        val status = runCatching { fetchWhatsappBridgeStatus() }.getOrNull()
            ?: return BookkeepingWhatsappStatusDto(
                connected = false,
                status = "bridge_unavailable",
                message = "未检测到本机 WhatsApp 读取服务。",
                qr = null
            )
        return BookkeepingWhatsappStatusDto(
            connected = status.connected,
            status = status.status,
            message = status.message,
            qr = status.qr
        )
    }

    @Transactional
    fun syncTelegramChats(): BookkeepingTelegramChatSyncResultDto {
        val bridgeResponse = runCatching { fetchTelegramBridgeGroups() }.getOrNull()
            ?: return BookkeepingTelegramChatSyncResultDto(
                connected = false,
                status = "bridge_unavailable",
                message = "未检测到本机 Telegram 读取服务，当前显示已保存的群聊配置。",
                groups = listTelegramGroups()
            )

        if (!bridgeResponse.connected) {
            return BookkeepingTelegramChatSyncResultDto(
                connected = false,
                status = bridgeResponse.status.ifEmpty { "not_ready" },
                message = bridgeResponse.message.ifEmpty { "Telegram 尚未登录或尚未读取到群聊。" },
                groups = listTelegramGroups()
            )
        }

        val now = System.currentTimeMillis()
        val existingGroups = whatsappGroupRepository.findAllBySourceTypeOrderByDisplayNameAsc(MESSAGE_SOURCE_TELEGRAM)
        val bySourceChatId = existingGroups
            .mapNotNull { group -> group.sourceChatId?.takeIf { it.isNotBlank() }?.let { it to group } }
            .toMap()
        val byGroupKey = existingGroups.associateBy { it.groupKey }

        val syncedGroups = bridgeResponse.groups.mapNotNull { chat ->
            val sourceChatId = chat.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val chatName = chat.name?.trim()?.takeIf { it.isNotEmpty() } ?: sourceChatId
            val groupKey = buildTelegramGroupKey(sourceChatId)
            val existing = bySourceChatId[sourceChatId] ?: byGroupKey[groupKey]
            BookkeepingWhatsappGroup(
                id = existing?.id,
                groupKey = existing?.groupKey ?: groupKey,
                sourceType = MESSAGE_SOURCE_TELEGRAM,
                sourceChatId = sourceChatId.take(128),
                displayName = existing?.displayName ?: chatName.take(128),
                chatName = chatName.take(255),
                role = existing?.takeIf { it.configured }?.role ?: "pending",
                currency = existing?.currency ?: "USDT",
                exchangeRate = existing?.exchangeRate ?: BigDecimal.ONE,
                rebatePoints = existing?.rebatePoints ?: BigDecimal.ZERO,
                rebateRate = existing?.rebateRate ?: BigDecimal.ZERO,
                rebateRule = existing?.rebateRule ?: "none",
                lastScannedMessageId = existing?.lastScannedMessageId,
                configured = existing?.configured ?: false,
                enabled = existing?.enabled ?: true,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        }
        if (syncedGroups.isNotEmpty()) {
            whatsappGroupRepository.saveAll(syncedGroups)
        }

        return BookkeepingTelegramChatSyncResultDto(
            connected = true,
            status = bridgeResponse.status.ifEmpty { "ready" },
            message = "已同步 ${syncedGroups.size} 个 Telegram 群聊。",
            groups = listTelegramGroups()
        )
    }

    fun telegramApiConfig(): BookkeepingTelegramApiConfigDto {
        return telegramApiConfigStore.read()
    }

    fun saveTelegramApiConfig(request: SaveBookkeepingTelegramApiConfigRequest): BookkeepingTelegramApiConfigDto {
        val saved = telegramApiConfigStore.save(request)
        val restartMessage = runCatching {
            restartTelegramBridge()
            "Telegram 读取服务已重启。"
        }.getOrElse { error ->
            "Telegram API 已保存，但读取服务重启失败：${error.message ?: "未知错误"}"
        }
        return saved.copy(message = restartMessage)
    }

    fun telegramStatus(): BookkeepingTelegramStatusDto {
        val status = runCatching { fetchTelegramBridgeStatus() }.getOrNull()
            ?: return BookkeepingTelegramStatusDto(
                connected = false,
                status = "bridge_unavailable",
                message = "未检测到本机 Telegram 读取服务。",
                qr = null
            )
        return BookkeepingTelegramStatusDto(
            connected = status.connected,
            status = status.status,
            message = status.message,
            qr = status.qr
        )
    }

    private fun restartTelegramBridge() {
        val rootDir = telegramApiConfigStore.projectRootDir()
        val scriptPath = rootDir.resolve("start-telegram-bridge.ps1")
        require(Files.exists(scriptPath)) { "start-telegram-bridge.ps1 not found" }

        val command = listOf(
            "\$port = if (\$env:TELEGRAM_BRIDGE_PORT) { [int]\$env:TELEGRAM_BRIDGE_PORT } else { 18884 }",
            "Get-NetTCPConnection -State Listen -LocalPort \$port -ErrorAction SilentlyContinue | " +
                "Select-Object -ExpandProperty OwningProcess -Unique | " +
                "ForEach-Object { Stop-Process -Id \$_ -Force -ErrorAction SilentlyContinue }",
            "Start-Process -FilePath (Join-Path \$PSHOME 'powershell.exe') " +
                "-ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File','${powerShellSingleQuoted(scriptPath.toString())}') " +
                "-WorkingDirectory '${powerShellSingleQuoted(rootDir.toString())}' -WindowStyle Hidden"
        ).joinToString("; ")

        val process = ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command)
            .directory(rootDir.toFile())
            .start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("restart command timed out")
        }
        require(process.exitValue() == 0) { "restart command exited ${process.exitValue()}" }
    }

    private fun powerShellSingleQuoted(value: String): String = value.replace("'", "''")

    @Transactional
    fun scanWhatsappMessages(request: ScanBookkeepingWhatsappMessagesRequest): BookkeepingWhatsappMessageScanResultDto {
        validateBusinessDate(request.businessDate)
        val workspaceType = normaliseWorkspaceType(request.workspaceType)
        val roles = rolesForWhatsappScan(workspaceType)
        val groups = whatsappGroupRepository.findAll()
            .filter {
                it.sourceType == MESSAGE_SOURCE_WHATSAPP &&
                    it.enabled && it.configured && it.role in roles && !it.sourceChatId.isNullOrBlank()
            }
        if (groups.isEmpty()) {
            return BookkeepingWhatsappMessageScanResultDto(
                connected = false,
                status = "no_groups",
                message = if (workspaceType == "rolling") {
                    "没有配置已启用的滚球群。"
                } else {
                    "没有配置已启用的上游群、下游群或公司跟单群。"
                },
                workspaceType = workspaceType,
                businessDate = request.businessDate,
                scannedGroupCount = 0,
                scannedMessageCount = 0,
                importedCount = 0,
                updatedCount = 0
            )
        }

        val (startTime, endTime) = scanWindowMillis(request.businessDate, request.scanStart, request.scanEnd)
        val bridgeResponse = runCatching {
            fetchWhatsappBridgeMessages(
                chatIds = groups.mapNotNull { it.sourceChatId },
                startTime = startTime,
                endTime = endTime,
                limit = request.limit.coerceIn(1, 1000)
            )
        }.getOrElse { error ->
            return BookkeepingWhatsappMessageScanResultDto(
                connected = false,
                status = "bridge_unavailable",
                message = error.message ?: "未检测到本机 WhatsApp 读取服务。",
                workspaceType = workspaceType,
                businessDate = request.businessDate,
                scannedGroupCount = groups.size,
                scannedMessageCount = 0,
                importedCount = 0,
                updatedCount = 0
            )
        }

        if (!bridgeResponse.connected) {
            return BookkeepingWhatsappMessageScanResultDto(
                connected = false,
                status = bridgeResponse.status.ifEmpty { "not_ready" },
                message = bridgeResponse.message.ifEmpty { "WhatsApp 尚未登录或尚未读取到消息。" },
                workspaceType = workspaceType,
                businessDate = request.businessDate,
                scannedGroupCount = groups.size,
                scannedMessageCount = 0,
                importedCount = 0,
                updatedCount = 0
            )
        }

        val groupsBySourceChatId = groups
            .mapNotNull { group -> group.sourceChatId?.takeIf { it.isNotBlank() }?.let { it to group } }
            .toMap()
        val messagesForImport = messagesAfterLastScanned(groups, bridgeResponse.messages, request.force)
        val imports = BookkeepingWhatsappOrderPrecision.deduplicateImports(messagesForImport.flatMap { message ->
            val sourceChatId = message.chatId?.trim()?.takeIf { it.isNotEmpty() } ?: return@flatMap emptyList()
            val group = groupsBySourceChatId[sourceChatId] ?: return@flatMap emptyList()
            parseWhatsappMessageBlocks(message.body.orEmpty()).mapIndexed { index, parsed ->
                BookkeepingWhatsappOrderImportDto(
                    groupKey = group.groupKey,
                    orderKey = buildWhatsappOrderKey(workspaceType, message, index),
                    sourceType = MESSAGE_SOURCE_WHATSAPP,
                    direction = group.role,
                    messageTime = message.timestamp,
                    senderName = message.author ?: message.from ?: message.chatName,
                    rawMessage = parsed.rawMessage,
                    leagueName = parsed.leagueName,
                    matchName = parsed.matchName,
                    marketText = parsed.marketText,
                    oddsValue = parsed.oddsValue,
                    amount = parsed.amount,
                    currency = group.currency,
                    parseStatus = parsed.parseStatus
                )
            }
        })

        val importResult = if (imports.isEmpty()) {
            ImportBookkeepingWhatsappOrdersResultDto(request.businessDate, 0, 0)
        } else {
            importWhatsappOrders(ImportBookkeepingWhatsappOrdersRequest(request.businessDate, imports))
        }

        updateLastScannedMessageIds(groups, bridgeResponse.messages)

        return BookkeepingWhatsappMessageScanResultDto(
            connected = true,
            status = bridgeResponse.status.ifEmpty { "ready" },
            message = "扫描完成：读取 ${bridgeResponse.messages.size} 条消息，处理 ${messagesForImport.size} 条新消息，导入 ${importResult.importedCount} 条订单，更新 ${importResult.updatedCount} 条订单。",
            workspaceType = workspaceType,
            businessDate = request.businessDate,
            scannedGroupCount = groups.size,
            scannedMessageCount = messagesForImport.size,
            importedCount = importResult.importedCount,
            updatedCount = importResult.updatedCount
        )
    }

    @Transactional
    fun scanTelegramMessages(request: ScanBookkeepingTelegramMessagesRequest): BookkeepingTelegramMessageScanResultDto {
        validateBusinessDate(request.businessDate)
        val workspaceType = normaliseWorkspaceType(request.workspaceType)
        val roles = rolesForWhatsappScan(workspaceType)
        val groups = whatsappGroupRepository.findAll()
            .filter {
                it.sourceType == MESSAGE_SOURCE_TELEGRAM &&
                    it.enabled && it.configured && it.role in roles && !it.sourceChatId.isNullOrBlank()
            }
        if (groups.isEmpty()) {
            return BookkeepingTelegramMessageScanResultDto(
                connected = false,
                status = "no_groups",
                message = if (workspaceType == "rolling") {
                    "没有配置已启用的 Telegram 滚球群。"
                } else {
                    "没有配置已启用的 Telegram 上游群、下游群或公司跟单群。"
                },
                workspaceType = workspaceType,
                businessDate = request.businessDate,
                scannedGroupCount = 0,
                scannedMessageCount = 0,
                importedCount = 0,
                updatedCount = 0
            )
        }

        val (startTime, endTime) = scanWindowMillis(request.businessDate, request.scanStart, request.scanEnd)
        val bridgeResponse = runCatching {
            fetchTelegramBridgeMessages(
                chatIds = groups.mapNotNull { it.sourceChatId },
                startTime = startTime,
                endTime = endTime,
                limit = request.limit.coerceIn(1, 1000)
            )
        }.getOrElse { error ->
            return BookkeepingTelegramMessageScanResultDto(
                connected = false,
                status = "bridge_unavailable",
                message = error.message ?: "未检测到本机 Telegram 读取服务。",
                workspaceType = workspaceType,
                businessDate = request.businessDate,
                scannedGroupCount = groups.size,
                scannedMessageCount = 0,
                importedCount = 0,
                updatedCount = 0
            )
        }

        if (!bridgeResponse.connected) {
            return BookkeepingTelegramMessageScanResultDto(
                connected = false,
                status = bridgeResponse.status.ifEmpty { "not_ready" },
                message = bridgeResponse.message.ifEmpty { "Telegram 尚未登录或尚未读取到消息。" },
                workspaceType = workspaceType,
                businessDate = request.businessDate,
                scannedGroupCount = groups.size,
                scannedMessageCount = 0,
                importedCount = 0,
                updatedCount = 0
            )
        }

        val groupsBySourceChatId = groups
            .mapNotNull { group -> group.sourceChatId?.takeIf { it.isNotBlank() }?.let { it to group } }
            .toMap()
        val messagesForImport = messagesAfterLastScanned(groups, bridgeResponse.messages, request.force)
        val imports = BookkeepingWhatsappOrderPrecision.deduplicateImports(messagesForImport.flatMap { message ->
            val sourceChatId = message.chatId?.trim()?.takeIf { it.isNotEmpty() } ?: return@flatMap emptyList()
            val group = groupsBySourceChatId[sourceChatId] ?: return@flatMap emptyList()
            parseWhatsappMessageBlocks(message.body.orEmpty()).mapIndexed { index, parsed ->
                BookkeepingWhatsappOrderImportDto(
                    groupKey = group.groupKey,
                    orderKey = buildTelegramOrderKey(workspaceType, message, index),
                    sourceType = MESSAGE_SOURCE_TELEGRAM,
                    direction = group.role,
                    messageTime = message.timestamp,
                    senderName = message.author ?: message.from ?: message.chatName,
                    rawMessage = parsed.rawMessage,
                    leagueName = parsed.leagueName,
                    matchName = parsed.matchName,
                    marketText = parsed.marketText,
                    oddsValue = parsed.oddsValue,
                    amount = parsed.amount,
                    currency = group.currency,
                    parseStatus = parsed.parseStatus
                )
            }
        })

        val importResult = if (imports.isEmpty()) {
            ImportBookkeepingWhatsappOrdersResultDto(request.businessDate, 0, 0)
        } else {
            importWhatsappOrders(ImportBookkeepingWhatsappOrdersRequest(request.businessDate, imports))
        }

        updateLastScannedMessageIds(groups, bridgeResponse.messages)

        return BookkeepingTelegramMessageScanResultDto(
            connected = true,
            status = bridgeResponse.status.ifEmpty { "ready" },
            message = "扫描完成：读取 ${bridgeResponse.messages.size} 条 Telegram 消息，处理 ${messagesForImport.size} 条新消息，导入 ${importResult.importedCount} 条订单，更新 ${importResult.updatedCount} 条订单。",
            workspaceType = workspaceType,
            businessDate = request.businessDate,
            scannedGroupCount = groups.size,
            scannedMessageCount = messagesForImport.size,
            importedCount = importResult.importedCount,
            updatedCount = importResult.updatedCount
        )
    }

    @Transactional
    fun importWhatsappOrders(request: ImportBookkeepingWhatsappOrdersRequest): ImportBookkeepingWhatsappOrdersResultDto {
        validateBusinessDate(request.businessDate)
        val groups = whatsappGroupRepository.findAll().associateBy { it.sourceType to it.groupKey }
        val existingOrders = whatsappOrderRepository.findByBusinessDateOrderByCreatedAtDesc(request.businessDate)
        val existingByOrderKey = existingOrders.associateBy { it.orderKey }
        val existingIdentityKeys = existingOrders.mapNotNull { BookkeepingWhatsappOrderPrecision.existingIdentityKey(it) }.toMutableSet()
        val currentImportIdentityKeys = mutableSetOf<String>()
        val now = System.currentTimeMillis()
        var imported = 0
        var updated = 0
        val orders = BookkeepingWhatsappOrderPrecision.deduplicateImports(request.orders).mapNotNull { item ->
            val orderKey = cleanRequired(item.orderKey, "orderKey")
            val sourceType = normaliseMessageSourceType(item.sourceType)
            val group = groups[sourceType to item.groupKey]
            val existing = existingByOrderKey[orderKey]
            val identityKey = BookkeepingWhatsappOrderPrecision.importIdentityKey(item, group?.id, sourceType)
            if (existing == null && identityKey != null) {
                if (identityKey in existingIdentityKeys || !currentImportIdentityKeys.add(identityKey)) {
                    return@mapNotNull null
                }
                existingIdentityKeys.add(identityKey)
            }
            if (existing == null) imported += 1 else updated += 1
            BookkeepingWhatsappOrder(
                id = existing?.id,
                taskId = existing?.taskId,
                groupId = group?.id,
                sourceType = sourceType,
                businessDate = request.businessDate,
                orderKey = orderKey,
                direction = cleanRequired(item.direction, "direction"),
                messageTime = item.messageTime,
                senderName = item.senderName?.trim()?.takeIf { it.isNotEmpty() },
                rawMessage = cleanRequired(item.rawMessage, "rawMessage"),
                leagueName = item.leagueName?.trim()?.takeIf { it.isNotEmpty() },
                matchName = item.matchName?.trim()?.takeIf { it.isNotEmpty() },
                marketText = item.marketText?.trim()?.takeIf { it.isNotEmpty() },
                oddsValue = item.oddsValue,
                amount = item.amount,
                currency = item.currency?.trim()?.takeIf { it.isNotEmpty() },
                parseStatus = item.parseStatus.trim().ifEmpty { "parsed" },
                settlementResult = item.settlementResult?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        }
        whatsappOrderRepository.saveAll(orders)
        return ImportBookkeepingWhatsappOrdersResultDto(request.businessDate, imported, updated)
    }

    fun fetchTitan007ScoreResults(request: FetchBookkeepingTitan007ScoresRequest): BookkeepingTitan007ScoreFetchResultDto {
        validateBusinessDate(request.businessDate)
        val start = parseScanTime(request.startTime, LocalTime.MIN)
        val end = parseScanTime(request.endTime, LocalTime.of(23, 59, 59))
        require(!end.isBefore(start)) { "endTime must be greater than or equal to startTime" }
        return titan007ScoreCrawler.fetchScoreResults(request)
    }

    @Transactional
    fun runTask(request: RunBookkeepingTaskRequest): BookkeepingTaskDto {
        validateBusinessDate(request.businessDate)
        val workspaceType = normaliseWorkspaceType(request.workspaceType)
        val reportType = normaliseReportType(request.reportType)
        validateReportTypeForWorkspace(workspaceType, reportType)
        val now = System.currentTimeMillis()
        val taskKey = "bk-${workspaceType.take(1)}-${reportType.take(24)}-${request.businessDate.replace("-", "")}-$now"
        val task = taskRepository.save(
            BookkeepingTask(
                taskKey = taskKey,
                businessDate = request.businessDate,
                workspaceType = workspaceType,
                status = "running",
                startedAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
        val taskId = task.id ?: throw IllegalStateException("bookkeeping task id is empty")
        return runCatching {
            val accounts = listCrownAccounts()
            val groups = whatsappGroupRepository.findAllByOrderByDisplayNameAsc().map { it.toDto() }
            val wagers = crownWagerRepository.findByBusinessDateOrderByCreatedAtDesc(request.businessDate)
            val allWhatsappOrders = BookkeepingWhatsappOrderPrecision.deduplicateExistingOrders(
                whatsappOrderRepository.findByBusinessDateOrderByCreatedAtDesc(request.businessDate)
            )
            val workspaceWhatsappOrders = allWhatsappOrders.filter { orderMatchesWorkspace(workspaceType, it.direction) }
            val reconciliationItems = if (workspaceType == "prematch") {
                calculator.reconcile(
                    wagers = wagers.map { it.toCalculator() },
                    whatsappOrders = workspaceWhatsappOrders.map { it.toCalculator() }
                )
            } else {
                emptyList()
            }
            reconciliationRepository.deleteByTaskId(taskId)
            val reconciliationResults = reconciliationRepository.saveAll(
                reconciliationItems.map { item ->
                    BookkeepingReconciliationResult(
                        taskId = taskId,
                        crownWagerId = item.crownWagerId,
                        whatsappOrderId = item.whatsappOrderId,
                        matchStatus = item.matchStatus,
                        issueType = item.issueType,
                        amountDiff = item.amountDiff,
                        oddsDiff = item.oddsDiff,
                        profitAmount = item.profitAmount,
                        notes = item.notes,
                        createdAt = System.currentTimeMillis()
                    )
                }
            ).map { it.toDto() }
            val wagerDtos = wagers.map { it.toDto() }
            val rawWhatsappOrderDtos = ordersForWorkspace(workspaceType, allWhatsappOrders.map { it.toDto() })
            val whatsappOrderDtos = excelReportWriter.withAutoSettlementResults(request.businessDate, rawWhatsappOrderDtos)
            val summary = if (workspaceType == "rolling") {
                buildRollingSummaryDto(accounts, wagerDtos, whatsappOrderDtos)
            } else {
                buildPrematchSummaryDto(
                    accounts = accounts,
                    wagers = wagerDtos,
                    whatsappOrders = whatsappOrderDtos,
                    differenceCount = reconciliationItems.count { it.issueType != "matched" },
                    whatsappGroups = groups
                )
            }
            val excelPath = excelReportWriter.writeDailyReport(
                businessDate = request.businessDate,
                taskId = taskId,
                workspaceType = workspaceType,
                reportType = reportType,
                summary = summary,
                accounts = accounts,
                wagers = wagerDtos,
                whatsappOrders = whatsappOrderDtos,
                whatsappGroups = groups,
                reconciliationResults = reconciliationResults
            )
            val finishedAt = System.currentTimeMillis()
            taskRepository.save(
                task.copy(
                    status = "completed",
                    finishedAt = finishedAt,
                    resultSummaryJson = summary.toJson(),
                    excelPath = excelPath,
                    updatedAt = finishedAt
                )
            ).toDto()
        }.getOrElse { error ->
            val finishedAt = System.currentTimeMillis()
            taskRepository.save(
                task.copy(
                    status = "failed",
                    finishedAt = finishedAt,
                    resultSummaryJson = error.message,
                    updatedAt = finishedAt
                )
            )
            throw error
        }
    }

    fun downloadTaskFile(taskId: Long): FileSystemResource {
        val task = taskRepository.findById(taskId).orElseThrow {
            IllegalArgumentException("bookkeeping task not found")
        }
        val excelPath = task.excelPath?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("bookkeeping task file is empty")
        val outputRoot = Paths.get("output", "bookkeeping").toAbsolutePath().normalize()
        val filePath = Paths.get(excelPath).toAbsolutePath().normalize()
        require(filePath.startsWith(outputRoot)) { "bookkeeping task file path is not allowed" }
        require(Files.exists(filePath) && Files.isRegularFile(filePath)) { "bookkeeping task file not found" }
        return FileSystemResource(filePath)
    }

    @Transactional
    fun clearGeneratedFiles(): ClearBookkeepingGeneratedFilesResultDto {
        val outputRoot = Paths.get("output", "bookkeeping").toAbsolutePath().normalize()
        val tasks = taskRepository.findByExcelPathIsNotNull()
        val taskFilePaths = tasks.mapNotNull { task ->
            task.excelPath?.trim()?.takeIf { it.isNotEmpty() }?.let { Paths.get(it).toAbsolutePath().normalize() }
        }
        val strayFilePaths = if (Files.exists(outputRoot)) {
            Files.list(outputRoot).use { stream ->
                stream.filter { Files.isRegularFile(it) }.map { it.toAbsolutePath().normalize() }.toList()
            }
        } else {
            emptyList()
        }
        var deletedFileCount = 0
        (taskFilePaths + strayFilePaths)
            .distinct()
            .filter { it.startsWith(outputRoot) }
            .forEach { path ->
                if (Files.deleteIfExists(path)) {
                    deletedFileCount += 1
                }
            }

        val taskIds = tasks.mapNotNull { it.id }
        if (taskIds.isNotEmpty()) {
            reconciliationRepository.deleteByTaskIdIn(taskIds)
        }
        val deletedTaskCount = taskRepository.deleteByExcelPathIsNotNull().toInt()

        return ClearBookkeepingGeneratedFilesResultDto(
            deletedFileCount = deletedFileCount,
            deletedTaskCount = deletedTaskCount
        )
    }

    private fun cleanRequired(value: String?, field: String): String {
        val cleaned = value?.trim().orEmpty()
        require(cleaned.isNotEmpty()) { "$field is required" }
        return cleaned
    }

    private fun normaliseBaseUrl(value: String): String {
        val cleaned = cleanRequired(value, "baseUrl").trimEnd('/')
        require(cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            "baseUrl must start with http:// or https://"
        }
        return cleaned
    }

    private fun normaliseCurrency(value: String): String {
        val currency = value.trim().uppercase().ifEmpty { "USDT" }
        val normalized = if (currency == "U") "USDT" else currency
        require(normalized in setOf("USDT", "RMB")) { "currency must be USDT or RMB" }
        return normalized
    }

    private fun normaliseRebateRule(value: String): String {
        val rule = value.trim().lowercase().ifEmpty { "none" }
        require(rule in setOf("none", "turnover", "loss_amount", "lose_only", "win_loss", "draw_included", "half_result")) {
            "rebateRule must be none, turnover, loss_amount, lose_only, win_loss, draw_included or half_result"
        }
        return rule
    }

    private fun normaliseGroupRole(value: String): String {
        val role = cleanRequired(value, "role").lowercase()
        require(role in BOOKKEEPING_GROUP_ROLES) {
            "role must be pending, upstream, downstream, company_follow, rolling_upstream, rolling_downstream, rolling_company, rolling or ignored"
        }
        return role
    }

    private fun normaliseReportType(value: String): String {
        val reportType = value.trim().lowercase().ifEmpty { "daily" }
        require(
            reportType in setOf(
                "daily",
                "crown_wagers",
                "downstream_orders",
                "upstream_orders",
                "company_orders",
                "prematch_profit",
                "prematch_excel",
                "rolling_upstream_orders",
                "rolling_downstream_orders",
                "rolling_group_orders",
                "rolling_reconcile",
                "rolling_profit",
                "rolling_excel"
            )
        ) {
            "unsupported reportType $reportType"
        }
        return reportType
    }

    private fun normaliseWorkspaceType(value: String): String {
        val workspaceType = value.trim().lowercase().ifEmpty { "prematch" }
        require(workspaceType in setOf("prematch", "rolling")) {
            "workspaceType must be prematch or rolling"
        }
        return workspaceType
    }

    private fun validateReportTypeForWorkspace(workspaceType: String, reportType: String) {
        val allowed = when (workspaceType) {
            "rolling" -> setOf(
                "crown_wagers",
                "rolling_upstream_orders",
                "rolling_downstream_orders",
                "rolling_group_orders",
                "rolling_reconcile",
                "rolling_profit",
                "rolling_excel"
            )
            else -> setOf(
                "daily",
                "downstream_orders",
                "upstream_orders",
                "company_orders",
                "prematch_profit",
                "prematch_excel"
            )
        }
        require(reportType in allowed) {
            "reportType $reportType is not allowed for $workspaceType workspace"
        }
    }

    private fun fetchWhatsappBridgeGroups(): WhatsappBridgeGroupsResponse {
        val bridgeBaseUrl = (System.getenv("BOOKKEEPING_WHATSAPP_BRIDGE_URL") ?: "http://127.0.0.1:18883")
            .trim()
            .trimEnd('/')
        val request = HttpRequest.newBuilder(URI.create("$bridgeBaseUrl/groups"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()
        val response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) { "WhatsApp bridge returned ${response.statusCode()}" }
        return objectMapper.readValue(response.body())
    }

    private fun fetchWhatsappBridgeStatus(): WhatsappBridgeStatusResponse {
        val bridgeBaseUrl = (System.getenv("BOOKKEEPING_WHATSAPP_BRIDGE_URL") ?: "http://127.0.0.1:18883")
            .trim()
            .trimEnd('/')
        val request = HttpRequest.newBuilder(URI.create("$bridgeBaseUrl/status"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()
        val response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) { "WhatsApp bridge returned ${response.statusCode()}" }
        return objectMapper.readValue(response.body())
    }

    private fun fetchWhatsappBridgeMessages(
        chatIds: List<String>,
        startTime: Long,
        endTime: Long,
        limit: Int
    ): WhatsappBridgeMessagesResponse {
        val bridgeBaseUrl = (System.getenv("BOOKKEEPING_WHATSAPP_BRIDGE_URL") ?: "http://127.0.0.1:18883")
            .trim()
            .trimEnd('/')
        val body = objectMapper.writeValueAsString(
            WhatsappBridgeMessagesRequest(
                chatIds = chatIds,
                startTime = startTime,
                endTime = endTime,
                limit = limit
            )
        )
        val request = HttpRequest.newBuilder(URI.create("$bridgeBaseUrl/messages"))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) { "WhatsApp bridge returned ${response.statusCode()}" }
        return objectMapper.readValue(response.body())
    }

    private fun fetchTelegramBridgeGroups(): WhatsappBridgeGroupsResponse {
        val bridgeBaseUrl = telegramBridgeBaseUrl()
        val request = HttpRequest.newBuilder(URI.create("$bridgeBaseUrl/groups"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()
        val response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) { "Telegram bridge returned ${response.statusCode()}" }
        return objectMapper.readValue(response.body())
    }

    private fun fetchTelegramBridgeStatus(): WhatsappBridgeStatusResponse {
        val bridgeBaseUrl = telegramBridgeBaseUrl()
        val request = HttpRequest.newBuilder(URI.create("$bridgeBaseUrl/status"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()
        val response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) { "Telegram bridge returned ${response.statusCode()}" }
        return objectMapper.readValue(response.body())
    }

    private fun fetchTelegramBridgeMessages(
        chatIds: List<String>,
        startTime: Long,
        endTime: Long,
        limit: Int
    ): WhatsappBridgeMessagesResponse {
        val bridgeBaseUrl = telegramBridgeBaseUrl()
        val body = objectMapper.writeValueAsString(
            WhatsappBridgeMessagesRequest(
                chatIds = chatIds,
                startTime = startTime,
                endTime = endTime,
                limit = limit
            )
        )
        val request = HttpRequest.newBuilder(URI.create("$bridgeBaseUrl/messages"))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) { "Telegram bridge returned ${response.statusCode()}" }
        return objectMapper.readValue(response.body())
    }

    private fun telegramBridgeBaseUrl(): String =
        (System.getenv("BOOKKEEPING_TELEGRAM_BRIDGE_URL") ?: "http://127.0.0.1:18884")
            .trim()
            .trimEnd('/')

    private fun rolesForWhatsappScan(workspaceType: String): Set<String> =
        if (workspaceType == "rolling") ROLLING_GROUP_ROLES else PREMATCH_GROUP_ROLES

    private fun ordersForWorkspace(
        workspaceType: String,
        orders: List<BookkeepingWhatsappOrderDto>
    ): List<BookkeepingWhatsappOrderDto> =
        orders.filter { orderMatchesWorkspace(workspaceType, it.direction) }

    private fun orderMatchesWorkspace(workspaceType: String, direction: String): Boolean {
        return if (workspaceType == "rolling") {
            direction.lowercase() in ROLLING_GROUP_ROLES
        } else {
            direction.lowercase() in PREMATCH_GROUP_ROLES
        }
    }

    private fun scanWindowMillis(
        businessDate: String,
        scanStart: String?,
        scanEnd: String?
    ): Pair<Long, Long> {
        val date = LocalDate.parse(businessDate)
        val start = parseScanTime(scanStart, LocalTime.MIN)
        val end = parseScanTime(scanEnd, LocalTime.of(23, 59, 59))
        require(!end.isBefore(start)) { "scanEnd must be greater than or equal to scanStart" }
        val zone = ZoneId.systemDefault()
        val startMs = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
        val endMs = date.atTime(end).atZone(zone).toInstant().toEpochMilli()
        return startMs to endMs
    }

    private fun parseScanTime(value: String?, fallback: LocalTime): LocalTime {
        val cleaned = value?.trim()?.takeIf { it.isNotEmpty() } ?: return fallback
        return try {
            LocalTime.parse(cleaned)
        } catch (error: DateTimeParseException) {
            LocalTime.parse(cleaned, DateTimeFormatter.ofPattern("H:mm"))
        }
    }

    private fun parseWhatsappMessageBlocks(rawMessage: String): List<ParsedWhatsappOrderBlock> {
        val cleaned = rawMessage.trim()
        if (cleaned.isEmpty()) return emptyList()
        return splitWhatsappOrderBlocks(cleaned).map { block ->
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val marketIndex = lines.indexOfFirst { it.contains("@") }
            val amount = extractOrderAmount(block)
            val odds = extractOrderOdds(block)
            ParsedWhatsappOrderBlock(
                rawMessage = block,
                leagueName = lines.getOrNull((marketIndex - 2).coerceAtLeast(0))?.takeIf { marketIndex >= 2 },
                matchName = lines.getOrNull((marketIndex - 1).coerceAtLeast(0))?.takeIf { marketIndex >= 1 },
                marketText = lines.getOrNull(marketIndex).takeIf { marketIndex >= 0 },
                oddsValue = odds,
                amount = amount,
                parseStatus = if (amount != null && odds != null) "parsed" else "suspicious"
            )
        }
    }

    private fun splitWhatsappOrderBlocks(rawMessage: String): List<String> {
        val blocks = rawMessage
            .split(Regex("\\n\\s*\\n+"))
            .map { it.trim() }
            .filter {
                it.isNotEmpty() &&
                    !it.matches(Regex("^(今日|今天).*(共|条).*$")) &&
                    BookkeepingWhatsappOrderPrecision.isLikelyOrderBlock(it)
            }
        if (blocks.size > 1) return blocks
        return if (BookkeepingWhatsappOrderPrecision.isLikelyOrderBlock(rawMessage)) listOf(rawMessage) else emptyList()
    }

    private fun extractOrderOdds(value: String): BigDecimal? {
        val match = Regex("@\\s*([+-]?\\d+(?:\\.\\d+)?)").find(value) ?: return null
        return match.groupValues.getOrNull(1)?.toBigDecimalOrNull()
    }

    private fun extractOrderAmount(value: String): BigDecimal? {
        return BookkeepingWhatsappAmountParser.parseAmount(value)
    }

    private fun buildWhatsappOrderKey(
        workspaceType: String,
        message: WhatsappBridgeMessage,
        blockIndex: Int
    ): String {
        val source = message.messageId
            ?: "${message.chatId.orEmpty()}-${message.timestamp ?: 0}-${message.body.orEmpty().hashCode()}"
        return "$workspaceType-$blockIndex-$source"
            .replace(Regex("[^A-Za-z0-9_-]"), "-")
            .take(128)
    }

    private fun buildTelegramOrderKey(
        workspaceType: String,
        message: WhatsappBridgeMessage,
        blockIndex: Int
    ): String {
        val source = message.messageId
            ?: "${message.chatId.orEmpty()}-${message.timestamp ?: 0}-${message.body.orEmpty().hashCode()}"
        return "telegram_$workspaceType-$blockIndex-$source"
            .replace(Regex("[^A-Za-z0-9_-]"), "-")
            .take(128)
    }

    private fun updateLastScannedMessageIds(
        groups: List<BookkeepingWhatsappGroup>,
        messages: List<WhatsappBridgeMessage>
    ) {
        if (messages.isEmpty()) return
        val latestByChatId = messages
            .filter { !it.chatId.isNullOrBlank() && !it.messageId.isNullOrBlank() }
            .groupBy { it.chatId }
            .mapValues { (_, chatMessages) -> chatMessages.maxByOrNull { it.timestamp ?: 0 }?.messageId }
        val updatedGroups = groups.mapNotNull { group ->
            val lastMessageId = latestByChatId[group.sourceChatId]?.takeIf { !it.isNullOrBlank() } ?: return@mapNotNull null
            group.copy(lastScannedMessageId = lastMessageId, updatedAt = System.currentTimeMillis())
        }
        if (updatedGroups.isNotEmpty()) {
            whatsappGroupRepository.saveAll(updatedGroups)
        }
    }

    private fun messagesAfterLastScanned(
        groups: List<BookkeepingWhatsappGroup>,
        messages: List<WhatsappBridgeMessage>,
        force: Boolean
    ): List<WhatsappBridgeMessage> {
        if (force) return messages
        val lastScannedByChatId = groups
            .mapNotNull { group ->
                val chatId = group.sourceChatId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val messageId = group.lastScannedMessageId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                chatId to messageId
            }
            .toMap()
        if (lastScannedByChatId.isEmpty()) return messages

        return messages
            .groupBy { it.chatId }
            .flatMap { (chatId, chatMessages) ->
                val lastScannedMessageId = lastScannedByChatId[chatId] ?: return@flatMap chatMessages
                val lastSeenIndex = chatMessages.indexOfFirst { it.messageId == lastScannedMessageId }
                if (lastSeenIndex >= 0) chatMessages.drop(lastSeenIndex + 1) else chatMessages
            }
            .sortedBy { it.timestamp ?: 0 }
    }

    private fun buildWhatsappGroupKey(sourceChatId: String): String {
        val normalized = sourceChatId
            .replace(Regex("[^A-Za-z0-9_-]"), "-")
            .trim('-')
            .ifEmpty { "group" }
        return "wa-${normalized.take(61)}"
    }

    private fun buildTelegramGroupKey(sourceChatId: String): String {
        val normalized = sourceChatId
            .replace(Regex("[^A-Za-z0-9_-]"), "-")
            .trim('-')
            .ifEmpty { "group" }
        return "telegram_${normalized.take(55)}"
    }

    private fun normaliseMessageSourceType(value: String?): String =
        when (value?.trim()?.lowercase()) {
            MESSAGE_SOURCE_TELEGRAM -> MESSAGE_SOURCE_TELEGRAM
            else -> MESSAGE_SOURCE_WHATSAPP
        }

    private fun encryptCrownPassword(value: String): String {
        return "enc:${cryptoUtils.encrypt(value)}"
    }

    private fun decryptCrownPassword(value: String): String {
        return if (value.startsWith("enc:")) {
            cryptoUtils.decrypt(value.removePrefix("enc:"))
        } else if (cryptoUtils.isEncrypted(value)) {
            cryptoUtils.decrypt(value)
        } else {
            value
        }
    }

    private fun validateBusinessDate(value: String) {
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("businessDate must be YYYY-MM-DD")
        }
    }

    private fun todayText(): String = LocalDate.now().toString()

    private fun buildPrematchSummaryDto(
        accounts: List<BookkeepingCrownAccountDto>,
        wagers: List<BookkeepingCrownWagerDto>,
        whatsappOrders: List<BookkeepingWhatsappOrderDto>,
        differenceCount: Int,
        whatsappGroups: List<BookkeepingWhatsappGroupDto> = emptyList()
    ): BookkeepingDailySummaryDto {
        val summary = calculator.buildDailySummary(
            accounts = accounts.map { BookkeepingCalculatorAccount(it.lastLoginStatus) },
            wagers = wagers.map {
                BookkeepingCalculatorCrownWager(
                    id = it.id,
                    ticketId = it.ticketId,
                    leagueName = it.leagueName,
                    homeTeam = it.homeTeam,
                    awayTeam = it.awayTeam,
                    marketType = it.marketType,
                    selectionName = it.selectionName,
                    oddsValue = it.oddsValue,
                    stakeAmount = it.stakeAmount,
                    winLossAmount = it.winLossAmount,
                    status = it.status
                )
            },
            whatsappOrders = whatsappOrders.map {
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
            },
            differences = differenceCount,
            whatsappGroups = whatsappGroups.map {
                BookkeepingCalculatorWhatsappGroup(
                    id = it.id,
                    rebatePoints = it.rebatePoints
                )
            }
        )
        return BookkeepingDailySummaryDto(
            crownAccountTotal = summary.crownAccountTotal,
            crownSuccessCount = summary.crownSuccessCount,
            crownFailedCount = summary.crownFailedCount,
            crownManualCount = summary.crownManualCount,
            crownUntestedCount = summary.crownUntestedCount,
            crownTurnover = summary.crownTurnover,
            settledWinLoss = summary.settledWinLoss,
            unsettledAmount = summary.unsettledAmount,
            whatsappOrderCount = summary.whatsappOrderCount,
            upstreamValidCount = summary.upstreamValidCount,
            downstreamValidCount = summary.downstreamValidCount,
            companyFollowCount = summary.companyFollowCount,
            companyFollowAmount = summary.companyFollowAmount,
            suspiciousCount = summary.suspiciousCount,
            cancelledCount = summary.cancelledCount,
            differenceCount = summary.differenceCount,
            todayProfit = summary.todayProfit,
            upstreamTotalStake = summary.upstreamTotalStake,
            downstreamTotalStake = summary.downstreamTotalStake,
            upstreamCashflow = summary.upstreamCashflow,
            downstreamCashflow = summary.downstreamCashflow,
            waterLossAmount = summary.waterLossAmount,
            grossProfit = summary.grossProfit,
            companyNetProfit = summary.companyNetProfit,
            rollingGroupStake = summary.rollingGroupStake,
            rollingGroupSettlement = summary.rollingGroupSettlement,
            rollingProfitDiff = summary.rollingProfitDiff
        )
    }

    private fun buildRollingSummaryDto(
        accounts: List<BookkeepingCrownAccountDto>,
        wagers: List<BookkeepingCrownWagerDto>,
        whatsappOrders: List<BookkeepingWhatsappOrderDto>
    ): BookkeepingDailySummaryDto {
        val summary = calculator.buildRollingSummary(
            accounts = accounts.map { BookkeepingCalculatorAccount(it.lastLoginStatus) },
            wagers = wagers.map {
                BookkeepingCalculatorCrownWager(
                    id = it.id,
                    ticketId = it.ticketId,
                    leagueName = it.leagueName,
                    homeTeam = it.homeTeam,
                    awayTeam = it.awayTeam,
                    marketType = it.marketType,
                    selectionName = it.selectionName,
                    oddsValue = it.oddsValue,
                    stakeAmount = it.stakeAmount,
                    winLossAmount = it.winLossAmount,
                    status = it.status
                )
            },
            whatsappOrders = whatsappOrders.map {
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
        return BookkeepingDailySummaryDto(
            crownAccountTotal = summary.crownAccountTotal,
            crownSuccessCount = summary.crownSuccessCount,
            crownFailedCount = summary.crownFailedCount,
            crownManualCount = summary.crownManualCount,
            crownUntestedCount = summary.crownUntestedCount,
            crownTurnover = summary.crownTurnover,
            settledWinLoss = summary.settledWinLoss,
            unsettledAmount = summary.unsettledAmount,
            whatsappOrderCount = summary.whatsappOrderCount,
            upstreamValidCount = summary.upstreamValidCount,
            downstreamValidCount = summary.downstreamValidCount,
            companyFollowCount = summary.companyFollowCount,
            companyFollowAmount = summary.companyFollowAmount,
            suspiciousCount = summary.suspiciousCount,
            cancelledCount = summary.cancelledCount,
            differenceCount = summary.differenceCount,
            todayProfit = summary.todayProfit,
            upstreamTotalStake = summary.upstreamTotalStake,
            downstreamTotalStake = summary.downstreamTotalStake,
            upstreamCashflow = summary.upstreamCashflow,
            downstreamCashflow = summary.downstreamCashflow,
            waterLossAmount = summary.waterLossAmount,
            grossProfit = summary.grossProfit,
            companyNetProfit = summary.companyNetProfit,
            rollingGroupStake = summary.rollingGroupStake,
            rollingGroupSettlement = summary.rollingGroupSettlement,
            rollingProfitDiff = summary.rollingProfitDiff
        )
    }

    private fun BookkeepingCrownAccount.toDto() = BookkeepingCrownAccountDto(
        id, accountKey, displayName, baseUrl, username, !password.isNullOrBlank(), enabled, timezone,
        lastLoginStatus, lastLoginMessage, lastLoginAt, updatedAt
    )

    private fun BookkeepingWhatsappGroup.toDto() = BookkeepingWhatsappGroupDto(
        id = id,
        groupKey = groupKey,
        sourceType = sourceType,
        sourceChatId = sourceChatId,
        displayName = displayName,
        chatName = chatName,
        role = if (configured) role else "pending",
        currency = currency,
        exchangeRate = exchangeRate,
        rebatePoints = rebatePoints,
        rebateRate = rebateRate,
        rebateRule = rebateRule,
        lastScannedMessageId = lastScannedMessageId,
        configured = configured,
        enabled = enabled,
        updatedAt = updatedAt
    )

    private fun BookkeepingTask.toDto() = BookkeepingTaskDto(
        id, taskKey, workspaceType, businessDate, status, startedAt, finishedAt, resultSummaryJson, excelPath, createdAt, updatedAt
    )

    private fun BookkeepingCrownWager.toDto() = BookkeepingCrownWagerDto(
        id = id,
        taskId = taskId,
        accountId = accountId,
        businessDate = businessDate,
        ticketId = ticketId,
        wagerTime = wagerTime,
        leagueName = leagueName,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        marketType = marketType,
        selectionName = selectionName,
        oddsValue = oddsValue,
        stakeAmount = stakeAmount,
        winLossAmount = winLossAmount,
        currency = currency,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun BookkeepingCrownWager.toCalculator() = BookkeepingCalculatorCrownWager(
        id = id,
        ticketId = ticketId,
        leagueName = leagueName,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        marketType = marketType,
        selectionName = selectionName,
        oddsValue = oddsValue,
        stakeAmount = stakeAmount,
        winLossAmount = winLossAmount,
        status = status
    )

    private fun BookkeepingWhatsappOrder.toDto() = BookkeepingWhatsappOrderDto(
        id = id,
        taskId = taskId,
        groupId = groupId,
        sourceType = sourceType,
        businessDate = businessDate,
        orderKey = orderKey,
        direction = direction,
        messageTime = messageTime,
        senderName = senderName,
        rawMessage = rawMessage,
        leagueName = leagueName,
        matchName = matchName,
        marketText = marketText,
        oddsValue = oddsValue,
        amount = amount,
        currency = currency,
        parseStatus = parseStatus,
        settlementResult = settlementResult,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun BookkeepingWhatsappOrder.toCalculator() = BookkeepingCalculatorWhatsappOrder(
        id = id,
        groupId = groupId,
        orderKey = orderKey,
        direction = direction,
        parseStatus = parseStatus,
        leagueName = leagueName,
        matchName = matchName,
        marketText = marketText,
        oddsValue = oddsValue,
        amount = amount,
        settlementResult = settlementResult
    )

    private fun BookkeepingReconciliationResult.toDto() = BookkeepingReconciliationResultDto(
        id = id,
        taskId = taskId,
        crownWagerId = crownWagerId,
        whatsappOrderId = whatsappOrderId,
        matchStatus = matchStatus,
        issueType = issueType,
        amountDiff = amountDiff,
        oddsDiff = oddsDiff,
        profitAmount = profitAmount,
        notes = notes,
        createdAt = createdAt
    )
}
