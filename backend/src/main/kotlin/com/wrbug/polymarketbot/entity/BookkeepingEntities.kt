package com.wrbug.polymarketbot.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "bookkeeping_crown_accounts")
data class BookkeepingCrownAccount(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "account_key", nullable = false, length = 64)
    val accountKey: String = "",
    @Column(name = "display_name", nullable = false, length = 128)
    val displayName: String = "",
    @Column(name = "base_url", nullable = false, length = 255)
    val baseUrl: String = "",
    @Column(name = "username", nullable = false, length = 128)
    val username: String = "",
    @Column(name = "password", length = 512)
    val password: String? = null,
    @Column(name = "enabled", nullable = false)
    val enabled: Boolean = true,
    @Column(name = "timezone", nullable = false, length = 32)
    val timezone: String = "GMT-4",
    @Column(name = "last_login_status", length = 32)
    val lastLoginStatus: String? = null,
    @Column(name = "last_login_message", columnDefinition = "TEXT")
    val lastLoginMessage: String? = null,
    @Column(name = "last_login_at")
    val lastLoginAt: Long? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Long = System.currentTimeMillis(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity
@Table(name = "bookkeeping_crown_wagers")
data class BookkeepingCrownWager(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "task_id")
    val taskId: Long? = null,
    @Column(name = "account_id", nullable = false)
    val accountId: Long = 0,
    @Column(name = "business_date", nullable = false, length = 10)
    val businessDate: String = "",
    @Column(name = "ticket_id", nullable = false, length = 128)
    val ticketId: String = "",
    @Column(name = "wager_time")
    val wagerTime: Long? = null,
    @Column(name = "league_name", length = 128)
    val leagueName: String? = null,
    @Column(name = "home_team", length = 128)
    val homeTeam: String? = null,
    @Column(name = "away_team", length = 128)
    val awayTeam: String? = null,
    @Column(name = "market_type", length = 64)
    val marketType: String? = null,
    @Column(name = "selection_name", length = 128)
    val selectionName: String? = null,
    @Column(name = "odds_value")
    val oddsValue: BigDecimal? = null,
    @Column(name = "stake_amount", nullable = false)
    val stakeAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "win_loss_amount", nullable = false)
    val winLossAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "currency", length = 16)
    val currency: String? = null,
    @Column(name = "status", nullable = false, length = 32)
    val status: String = "unknown",
    @Column(name = "raw_payload_json", columnDefinition = "TEXT")
    val rawPayloadJson: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Long = System.currentTimeMillis(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity
@Table(name = "bookkeeping_whatsapp_groups")
data class BookkeepingWhatsappGroup(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "group_key", nullable = false, length = 64)
    val groupKey: String = "",
    @Column(name = "source_type", nullable = false, length = 32)
    val sourceType: String = "whatsapp",
    @Column(name = "source_chat_id", length = 128)
    val sourceChatId: String? = null,
    @Column(name = "display_name", nullable = false, length = 128)
    val displayName: String = "",
    @Column(name = "chat_name", nullable = false, length = 255)
    val chatName: String = "",
    @Column(name = "role", nullable = false, length = 32)
    val role: String = "",
    @Column(name = "currency", nullable = false, length = 16)
    val currency: String = "USDT",
    @Column(name = "exchange_rate", nullable = false)
    val exchangeRate: BigDecimal = BigDecimal.ONE,
    @Column(name = "rebate_points", nullable = false)
    val rebatePoints: BigDecimal = BigDecimal.ZERO,
    @Column(name = "rebate_rate", nullable = false)
    val rebateRate: BigDecimal = BigDecimal.ZERO,
    @Column(name = "rebate_rule", nullable = false, length = 32)
    val rebateRule: String = "none",
    @Column(name = "last_scanned_message_id", length = 128)
    val lastScannedMessageId: String? = null,
    @Column(name = "configured", nullable = false)
    val configured: Boolean = false,
    @Column(name = "enabled", nullable = false)
    val enabled: Boolean = true,
    @Column(name = "created_at", nullable = false)
    val createdAt: Long = System.currentTimeMillis(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity
@Table(name = "bookkeeping_whatsapp_orders")
data class BookkeepingWhatsappOrder(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "task_id")
    val taskId: Long? = null,
    @Column(name = "group_id")
    val groupId: Long? = null,
    @Column(name = "source_type", nullable = false, length = 32)
    val sourceType: String = "whatsapp",
    @Column(name = "business_date", nullable = false, length = 10)
    val businessDate: String = "",
    @Column(name = "order_key", nullable = false, length = 128)
    val orderKey: String = "",
    @Column(name = "direction", nullable = false, length = 32)
    val direction: String = "",
    @Column(name = "message_time")
    val messageTime: Long? = null,
    @Column(name = "sender_name", length = 128)
    val senderName: String? = null,
    @Column(name = "raw_message", nullable = false, columnDefinition = "TEXT")
    val rawMessage: String = "",
    @Column(name = "league_name", length = 128)
    val leagueName: String? = null,
    @Column(name = "match_name", length = 255)
    val matchName: String? = null,
    @Column(name = "market_text", length = 255)
    val marketText: String? = null,
    @Column(name = "odds_value")
    val oddsValue: BigDecimal? = null,
    @Column(name = "amount")
    val amount: BigDecimal? = null,
    @Column(name = "currency", length = 16)
    val currency: String? = null,
    @Column(name = "parse_status", nullable = false, length = 32)
    val parseStatus: String = "parsed",
    @Column(name = "settlement_result", length = 32)
    val settlementResult: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Long = System.currentTimeMillis(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity
@Table(name = "bookkeeping_tasks")
data class BookkeepingTask(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "task_key", nullable = false, length = 64)
    val taskKey: String = "",
    @Column(name = "business_date", nullable = false, length = 10)
    val businessDate: String = "",
    @Column(name = "workspace_type", nullable = false, length = 32)
    val workspaceType: String = "prematch",
    @Column(name = "status", nullable = false, length = 32)
    val status: String = "",
    @Column(name = "started_at")
    val startedAt: Long? = null,
    @Column(name = "finished_at")
    val finishedAt: Long? = null,
    @Column(name = "result_summary_json", columnDefinition = "TEXT")
    val resultSummaryJson: String? = null,
    @Column(name = "excel_path", length = 512)
    val excelPath: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Long = System.currentTimeMillis(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity
@Table(name = "bookkeeping_reconciliation_results")
data class BookkeepingReconciliationResult(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "task_id", nullable = false)
    val taskId: Long = 0,
    @Column(name = "crown_wager_id")
    val crownWagerId: Long? = null,
    @Column(name = "whatsapp_order_id")
    val whatsappOrderId: Long? = null,
    @Column(name = "match_status", nullable = false, length = 32)
    val matchStatus: String = "",
    @Column(name = "issue_type", length = 64)
    val issueType: String? = null,
    @Column(name = "amount_diff")
    val amountDiff: BigDecimal? = null,
    @Column(name = "odds_diff")
    val oddsDiff: BigDecimal? = null,
    @Column(name = "profit_amount", nullable = false)
    val profitAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "notes", columnDefinition = "TEXT")
    val notes: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Long = System.currentTimeMillis()
)
