package com.wrbug.polymarketbot.repository

import com.wrbug.polymarketbot.entity.BookkeepingCrownAccount
import com.wrbug.polymarketbot.entity.BookkeepingCrownWager
import com.wrbug.polymarketbot.entity.BookkeepingReconciliationResult
import com.wrbug.polymarketbot.entity.BookkeepingTask
import com.wrbug.polymarketbot.entity.BookkeepingWhatsappGroup
import com.wrbug.polymarketbot.entity.BookkeepingWhatsappOrder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BookkeepingCrownAccountRepository : JpaRepository<BookkeepingCrownAccount, Long> {
    fun findByAccountKey(accountKey: String): BookkeepingCrownAccount?
    fun findByEnabledOrderByDisplayNameAsc(enabled: Boolean): List<BookkeepingCrownAccount>
    fun findAllByOrderByDisplayNameAsc(): List<BookkeepingCrownAccount>
}

@Repository
interface BookkeepingCrownWagerRepository : JpaRepository<BookkeepingCrownWager, Long> {
    fun countByBusinessDate(businessDate: String): Long
    fun findByAccountIdAndBusinessDateAndTicketId(
        accountId: Long,
        businessDate: String,
        ticketId: String
    ): BookkeepingCrownWager?
    fun findByBusinessDateOrderByCreatedAtDesc(businessDate: String): List<BookkeepingCrownWager>
}

@Repository
interface BookkeepingWhatsappGroupRepository : JpaRepository<BookkeepingWhatsappGroup, Long> {
    fun findByGroupKey(groupKey: String): BookkeepingWhatsappGroup?
    fun findByGroupKeyAndSourceType(groupKey: String, sourceType: String): BookkeepingWhatsappGroup?
    fun findByEnabledOrderByDisplayNameAsc(enabled: Boolean): List<BookkeepingWhatsappGroup>
    fun findByEnabledAndSourceTypeOrderByDisplayNameAsc(
        enabled: Boolean,
        sourceType: String
    ): List<BookkeepingWhatsappGroup>
    fun findAllByOrderByDisplayNameAsc(): List<BookkeepingWhatsappGroup>
    fun findAllBySourceTypeOrderByDisplayNameAsc(sourceType: String): List<BookkeepingWhatsappGroup>
}

@Repository
interface BookkeepingWhatsappOrderRepository : JpaRepository<BookkeepingWhatsappOrder, Long> {
    fun countByBusinessDate(businessDate: String): Long
    fun findByBusinessDateAndOrderKey(businessDate: String, orderKey: String): BookkeepingWhatsappOrder?
    fun findByBusinessDateOrderByCreatedAtDesc(businessDate: String): List<BookkeepingWhatsappOrder>
}

@Repository
interface BookkeepingTaskRepository : JpaRepository<BookkeepingTask, Long> {
    fun findTop50ByOrderByCreatedAtDesc(): List<BookkeepingTask>
    fun findTop50ByWorkspaceTypeOrderByCreatedAtDesc(workspaceType: String): List<BookkeepingTask>
    fun findTopByBusinessDateOrderByCreatedAtDesc(businessDate: String): BookkeepingTask?
    fun findTopByBusinessDateAndWorkspaceTypeOrderByCreatedAtDesc(businessDate: String, workspaceType: String): BookkeepingTask?
    fun findByExcelPathIsNotNull(): List<BookkeepingTask>
    fun deleteByExcelPathIsNotNull(): Long
}

@Repository
interface BookkeepingReconciliationResultRepository : JpaRepository<BookkeepingReconciliationResult, Long> {
    fun countByTaskId(taskId: Long): Long
    fun findByTaskIdOrderByCreatedAtAsc(taskId: Long): List<BookkeepingReconciliationResult>
    fun deleteByTaskId(taskId: Long)
    fun deleteByTaskIdIn(taskIds: Collection<Long>)
}
