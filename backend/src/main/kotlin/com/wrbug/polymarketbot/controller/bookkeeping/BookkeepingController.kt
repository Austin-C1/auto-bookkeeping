package com.wrbug.polymarketbot.controller.bookkeeping

import com.wrbug.polymarketbot.dto.*
import com.wrbug.polymarketbot.enums.ErrorCode
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingService
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/bookkeeping")
class BookkeepingController(
    private val bookkeepingService: BookkeepingService
) {
    @PostMapping("/dashboard")
    fun dashboard(@RequestBody(required = false) request: BookkeepingDashboardRequest?):
        ResponseEntity<ApiResponse<BookkeepingDashboardDto>> =
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.dashboard(request?.businessDate, request?.workspaceType)))

    @PostMapping("/crown/accounts/list")
    fun listCrownAccounts(): ResponseEntity<ApiResponse<List<BookkeepingCrownAccountDto>>> =
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.listCrownAccounts()))

    @PostMapping("/crown/accounts/save")
    fun saveCrownAccount(@RequestBody request: SaveBookkeepingCrownAccountRequest):
        ResponseEntity<ApiResponse<BookkeepingCrownAccountDto>> = runCatching {
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.saveCrownAccount(request)))
    }.getOrElse { ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_ERROR, it.message)) }

    @PostMapping("/crown/accounts/test-login")
    fun testCrownLogin(@RequestBody request: BookkeepingCrownAccountIdRequest):
        ResponseEntity<ApiResponse<BookkeepingLoginTestResultDto>> = runCatching {
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.testCrownLogin(request.accountId)))
    }.getOrElse { ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_ERROR, it.message)) }

    @PostMapping("/crown/wagers/import")
    fun importCrownWagers(@RequestBody request: ImportBookkeepingCrownWagersRequest):
        ResponseEntity<ApiResponse<ImportBookkeepingCrownWagersResultDto>> = runCatching {
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.importCrownWagers(request)))
    }.getOrElse { ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_ERROR, it.message)) }

    @PostMapping("/whatsapp/groups/list")
    fun listWhatsappGroups(): ResponseEntity<ApiResponse<List<BookkeepingWhatsappGroupDto>>> =
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.listWhatsappGroups()))

    @PostMapping("/whatsapp/groups/save")
    fun saveWhatsappGroup(@RequestBody request: SaveBookkeepingWhatsappGroupRequest):
        ResponseEntity<ApiResponse<BookkeepingWhatsappGroupDto>> = runCatching {
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.saveWhatsappGroup(request)))
    }.getOrElse { ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_ERROR, it.message)) }

    @PostMapping("/whatsapp/bootstrap")
    fun whatsappBootstrap(): ResponseEntity<ApiResponse<BookkeepingWhatsappBootstrapDto>> =
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.whatsappBootstrap()))

    @PostMapping("/whatsapp/chats/sync")
    fun syncWhatsappChats(): ResponseEntity<ApiResponse<BookkeepingWhatsappChatSyncResultDto>> =
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.syncWhatsappChats()))

    @PostMapping("/whatsapp/status")
    fun whatsappStatus(): ResponseEntity<ApiResponse<BookkeepingWhatsappStatusDto>> =
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.whatsappStatus()))

    @PostMapping("/whatsapp/scan")
    fun scanWhatsappMessages(@RequestBody request: ScanBookkeepingWhatsappMessagesRequest):
        ResponseEntity<ApiResponse<BookkeepingWhatsappMessageScanResultDto>> = runCatching {
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.scanWhatsappMessages(request)))
    }.getOrElse { ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_ERROR, it.message)) }

    @PostMapping("/whatsapp/orders/import")
    fun importWhatsappOrders(@RequestBody request: ImportBookkeepingWhatsappOrdersRequest):
        ResponseEntity<ApiResponse<ImportBookkeepingWhatsappOrdersResultDto>> = runCatching {
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.importWhatsappOrders(request)))
    }.getOrElse { ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_ERROR, it.message)) }

    @PostMapping("/telegram/groups/list")
    fun listTelegramGroups(): ResponseEntity<ApiResponse<List<BookkeepingTelegramGroupDto>>> =
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.listTelegramGroups()))

    @PostMapping("/telegram/groups/save")
    fun saveTelegramGroup(@RequestBody request: SaveBookkeepingTelegramGroupRequest):
        ResponseEntity<ApiResponse<BookkeepingTelegramGroupDto>> = runCatching {
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.saveTelegramGroup(request)))
    }.getOrElse { ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_ERROR, it.message)) }

    @PostMapping("/telegram/api-config")
    fun telegramApiConfig(): ResponseEntity<ApiResponse<BookkeepingTelegramApiConfigDto>> =
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.telegramApiConfig()))

    @PostMapping("/telegram/api-config/save")
    fun saveTelegramApiConfig(@RequestBody request: SaveBookkeepingTelegramApiConfigRequest):
        ResponseEntity<ApiResponse<BookkeepingTelegramApiConfigDto>> = runCatching {
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.saveTelegramApiConfig(request)))
    }.getOrElse { ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_ERROR, it.message)) }

    @PostMapping("/telegram/chats/sync")
    fun syncTelegramChats(): ResponseEntity<ApiResponse<BookkeepingTelegramChatSyncResultDto>> =
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.syncTelegramChats()))

    @PostMapping("/telegram/status")
    fun telegramStatus(): ResponseEntity<ApiResponse<BookkeepingTelegramStatusDto>> =
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.telegramStatus()))

    @PostMapping("/telegram/scan")
    fun scanTelegramMessages(@RequestBody request: ScanBookkeepingTelegramMessagesRequest):
        ResponseEntity<ApiResponse<BookkeepingTelegramMessageScanResultDto>> = runCatching {
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.scanTelegramMessages(request)))
    }.getOrElse { ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_ERROR, it.message)) }

    @PostMapping("/score-results/titan007/fetch")
    fun fetchTitan007ScoreResults(@RequestBody request: FetchBookkeepingTitan007ScoresRequest):
        ResponseEntity<ApiResponse<BookkeepingTitan007ScoreFetchResultDto>> = runCatching {
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.fetchTitan007ScoreResults(request)))
    }.getOrElse { ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_ERROR, it.message)) }

    @PostMapping("/tasks/run")
    fun runTask(@RequestBody request: RunBookkeepingTaskRequest):
        ResponseEntity<ApiResponse<BookkeepingTaskDto>> = runCatching {
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.runTask(request)))
    }.getOrElse { ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_ERROR, it.message)) }

    @PostMapping("/tasks/generated-files/clear")
    fun clearGeneratedFiles(): ResponseEntity<ApiResponse<ClearBookkeepingGeneratedFilesResultDto>> = runCatching {
        ResponseEntity.ok(ApiResponse.success(bookkeepingService.clearGeneratedFiles()))
    }.getOrElse { ResponseEntity.ok(ApiResponse.error(ErrorCode.PARAM_ERROR, it.message)) }

    @GetMapping("/tasks/{taskId}/download")
    fun downloadTaskFile(@PathVariable taskId: Long): ResponseEntity<FileSystemResource> {
        val resource = bookkeepingService.downloadTaskFile(taskId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${resource.filename}\"")
            .body(resource)
    }
}
