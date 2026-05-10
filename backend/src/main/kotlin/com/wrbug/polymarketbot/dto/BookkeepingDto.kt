package com.wrbug.polymarketbot.dto

import java.math.BigDecimal

data class BookkeepingDashboardRequest(
    val businessDate: String? = null,
    val workspaceType: String? = null
)

data class BookkeepingCrownAccountDto(
    val id: Long?,
    val accountKey: String,
    val displayName: String,
    val baseUrl: String,
    val username: String,
    val passwordConfigured: Boolean,
    val enabled: Boolean,
    val timezone: String,
    val lastLoginStatus: String?,
    val lastLoginMessage: String?,
    val lastLoginAt: Long?,
    val updatedAt: Long
)

data class SaveBookkeepingCrownAccountRequest(
    val id: Long? = null,
    val accountKey: String,
    val displayName: String,
    val baseUrl: String,
    val username: String,
    val password: String? = null,
    val enabled: Boolean = true,
    val timezone: String = "GMT-4"
)

data class BookkeepingCrownAccountIdRequest(val accountId: Long)

data class BookkeepingLoginTestResultDto(
    val accountId: Long,
    val ok: Boolean,
    val message: String,
    val checkedAt: Long
)

data class BookkeepingWhatsappGroupDto(
    val id: Long?,
    val groupKey: String,
    val sourceType: String = "whatsapp",
    val sourceChatId: String?,
    val displayName: String,
    val chatName: String,
    val role: String,
    val currency: String,
    val exchangeRate: BigDecimal,
    val rebatePoints: BigDecimal,
    val rebateRate: BigDecimal,
    val rebateRule: String,
    val lastScannedMessageId: String?,
    val configured: Boolean,
    val enabled: Boolean,
    val updatedAt: Long
)

data class SaveBookkeepingWhatsappGroupRequest(
    val id: Long? = null,
    val groupKey: String,
    val sourceChatId: String? = null,
    val displayName: String,
    val chatName: String,
    val role: String,
    val currency: String = "USDT",
    val exchangeRate: BigDecimal = BigDecimal.ONE,
    val rebatePoints: BigDecimal = BigDecimal.ZERO,
    val rebateRate: BigDecimal = BigDecimal.ZERO,
    val rebateRule: String = "none",
    val lastScannedMessageId: String? = null,
    val enabled: Boolean = true
)

data class BookkeepingWhatsappChatSyncResultDto(
    val connected: Boolean,
    val status: String,
    val message: String,
    val groups: List<BookkeepingWhatsappGroupDto>
)

data class BookkeepingWhatsappStatusDto(
    val connected: Boolean,
    val status: String,
    val message: String,
    val qr: String? = null
)

data class ScanBookkeepingWhatsappMessagesRequest(
    val businessDate: String,
    val workspaceType: String = "prematch",
    val scanStart: String? = null,
    val scanEnd: String? = null,
    val force: Boolean = false,
    val limit: Int = 300
)

data class BookkeepingWhatsappMessageScanResultDto(
    val connected: Boolean,
    val status: String,
    val message: String,
    val workspaceType: String,
    val businessDate: String,
    val scannedGroupCount: Int,
    val scannedMessageCount: Int,
    val importedCount: Int,
    val updatedCount: Int
)

data class FetchBookkeepingTitan007ScoresRequest(
    val businessDate: String,
    val leagueFilter: String? = null,
    val startTime: String? = null,
    val endTime: String? = null
)

data class BookkeepingTitan007ScoreFetchResultDto(
    val businessDate: String,
    val fetchedCount: Int,
    val sourceUrl: String,
    val savedPath: String
)

data class BookkeepingTelegramApiConfigDto(
    val apiId: String = "",
    val apiHashConfigured: Boolean = false,
    val sessionConfigured: Boolean = false,
    val bridgeConfigured: Boolean = false,
    val message: String = ""
)

data class SaveBookkeepingTelegramApiConfigRequest(
    val apiId: String,
    val apiHash: String? = null
)

typealias BookkeepingTelegramGroupDto = BookkeepingWhatsappGroupDto
typealias SaveBookkeepingTelegramGroupRequest = SaveBookkeepingWhatsappGroupRequest
typealias BookkeepingTelegramChatSyncResultDto = BookkeepingWhatsappChatSyncResultDto
typealias BookkeepingTelegramStatusDto = BookkeepingWhatsappStatusDto
typealias ScanBookkeepingTelegramMessagesRequest = ScanBookkeepingWhatsappMessagesRequest
typealias BookkeepingTelegramMessageScanResultDto = BookkeepingWhatsappMessageScanResultDto

data class BookkeepingWhatsappBootstrapDto(
    val crownAccounts: List<BookkeepingCrownAccountDto>,
    val whatsappGroups: List<BookkeepingWhatsappGroupDto>,
    val endpoints: Map<String, String>
)

data class ImportBookkeepingWhatsappOrdersRequest(
    val businessDate: String,
    val orders: List<BookkeepingWhatsappOrderImportDto>
)

data class BookkeepingWhatsappOrderImportDto(
    val groupKey: String,
    val orderKey: String,
    val sourceType: String = "whatsapp",
    val direction: String,
    val messageTime: Long? = null,
    val senderName: String? = null,
    val rawMessage: String,
    val leagueName: String? = null,
    val matchName: String? = null,
    val marketText: String? = null,
    val oddsValue: BigDecimal? = null,
    val amount: BigDecimal? = null,
    val currency: String? = null,
    val parseStatus: String = "parsed",
    val settlementResult: String? = null
)

data class ImportBookkeepingWhatsappOrdersResultDto(
    val businessDate: String,
    val importedCount: Int,
    val updatedCount: Int = 0
)

data class RunBookkeepingTaskRequest(
    val businessDate: String,
    val workspaceType: String = "prematch",
    val reportType: String = "daily"
)

data class ImportBookkeepingCrownWagersRequest(
    val businessDate: String,
    val accountKey: String,
    val wagers: List<BookkeepingCrownWagerImportDto>
)

data class BookkeepingCrownWagerImportDto(
    val ticketId: String,
    val wagerTime: Long? = null,
    val leagueName: String? = null,
    val homeTeam: String? = null,
    val awayTeam: String? = null,
    val marketType: String? = null,
    val selectionName: String? = null,
    val oddsValue: BigDecimal? = null,
    val stakeAmount: BigDecimal = BigDecimal.ZERO,
    val winLossAmount: BigDecimal = BigDecimal.ZERO,
    val currency: String? = null,
    val status: String = "unknown",
    val rawPayloadJson: String? = null
)

data class ImportBookkeepingCrownWagersResultDto(
    val businessDate: String,
    val accountKey: String,
    val importedCount: Int,
    val updatedCount: Int
)

data class BookkeepingTaskDto(
    val id: Long?,
    val taskKey: String,
    val workspaceType: String,
    val businessDate: String,
    val status: String,
    val startedAt: Long?,
    val finishedAt: Long?,
    val resultSummaryJson: String?,
    val excelPath: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class ClearBookkeepingGeneratedFilesResultDto(
    val deletedFileCount: Int,
    val deletedTaskCount: Int
)

data class BookkeepingDailySummaryDto(
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
    val upstreamCashflow: BigDecimal = BigDecimal.ZERO,
    val downstreamCashflow: BigDecimal = BigDecimal.ZERO,
    val waterLossAmount: BigDecimal = BigDecimal.ZERO,
    val grossProfit: BigDecimal = BigDecimal.ZERO,
    val companyNetProfit: BigDecimal = BigDecimal.ZERO,
    val rollingGroupStake: BigDecimal = BigDecimal.ZERO,
    val rollingGroupSettlement: BigDecimal = BigDecimal.ZERO,
    val rollingProfitDiff: BigDecimal = BigDecimal.ZERO
)

data class BookkeepingCrownWagerDto(
    val id: Long?,
    val taskId: Long?,
    val accountId: Long,
    val businessDate: String,
    val ticketId: String,
    val wagerTime: Long?,
    val leagueName: String?,
    val homeTeam: String?,
    val awayTeam: String?,
    val marketType: String?,
    val selectionName: String?,
    val oddsValue: BigDecimal?,
    val stakeAmount: BigDecimal,
    val winLossAmount: BigDecimal,
    val currency: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class BookkeepingWhatsappOrderDto(
    val id: Long?,
    val taskId: Long?,
    val groupId: Long?,
    val sourceType: String = "whatsapp",
    val businessDate: String,
    val orderKey: String,
    val direction: String,
    val messageTime: Long?,
    val senderName: String?,
    val rawMessage: String,
    val leagueName: String?,
    val matchName: String?,
    val marketText: String?,
    val oddsValue: BigDecimal?,
    val amount: BigDecimal?,
    val currency: String?,
    val parseStatus: String,
    val settlementResult: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class BookkeepingReconciliationResultDto(
    val id: Long?,
    val taskId: Long,
    val crownWagerId: Long?,
    val whatsappOrderId: Long?,
    val matchStatus: String,
    val issueType: String?,
    val amountDiff: BigDecimal?,
    val oddsDiff: BigDecimal?,
    val profitAmount: BigDecimal,
    val notes: String?,
    val createdAt: Long
)

data class BookkeepingDashboardDto(
    val businessDate: String,
    val summary: BookkeepingDailySummaryDto,
    val crownAccounts: List<BookkeepingCrownAccountDto>,
    val whatsappGroups: List<BookkeepingWhatsappGroupDto>,
    val telegramGroups: List<BookkeepingTelegramGroupDto> = emptyList(),
    val crownWagers: List<BookkeepingCrownWagerDto>,
    val whatsappOrders: List<BookkeepingWhatsappOrderDto>,
    val reconciliationResults: List<BookkeepingReconciliationResultDto>,
    val tasks: List<BookkeepingTaskDto>
)
