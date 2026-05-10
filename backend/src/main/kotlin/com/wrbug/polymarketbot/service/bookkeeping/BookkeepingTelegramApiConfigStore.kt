package com.wrbug.polymarketbot.service.bookkeeping

import com.wrbug.polymarketbot.dto.BookkeepingTelegramApiConfigDto
import com.wrbug.polymarketbot.dto.SaveBookkeepingTelegramApiConfigRequest
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

@Service
class BookkeepingTelegramApiConfigStore {
    private val projectRootDir: Path = resolveProjectRootDir()
    private val localConfigPath: Path = projectRootDir.resolve("config").resolve("local.env.ps1")
    private val sessionFilePath: Path = projectRootDir.resolve("data").resolve("telegram-session.txt")

    fun projectRootDir(): Path = projectRootDir

    fun read(): BookkeepingTelegramApiConfigDto {
        val content = readLocalConfig()
        val apiId = readEnvValue(content, "TELEGRAM_API_ID").orEmpty()
        val apiHashConfigured = !readEnvValue(content, "TELEGRAM_API_HASH").isNullOrBlank()
        val sessionConfigured = Files.exists(sessionFilePath) && Files.size(sessionFilePath) > 0
        val bridgeConfigured = apiId.isNotBlank() && apiHashConfigured
        return BookkeepingTelegramApiConfigDto(
            apiId = apiId,
            apiHashConfigured = apiHashConfigured,
            sessionConfigured = sessionConfigured,
            bridgeConfigured = bridgeConfigured,
            message = if (bridgeConfigured) {
                "Telegram API 已配置。"
            } else {
                "请先填写 TELEGRAM_API_ID 和 TELEGRAM_API_HASH。"
            }
        )
    }

    fun save(request: SaveBookkeepingTelegramApiConfigRequest): BookkeepingTelegramApiConfigDto {
        val apiId = request.apiId.trim()
        require(apiId.matches(Regex("\\d+"))) { "API_ID 必须是数字" }

        val current = readLocalConfig()
        val existingHash = readEnvValue(current, "TELEGRAM_API_HASH").orEmpty()
        val apiHash = request.apiHash?.trim()?.takeIf { it.isNotEmpty() } ?: existingHash
        require(apiHash.isNotBlank()) { "API_HASH 不能为空" }

        var next = setEnvValue(current, "TELEGRAM_API_ID", apiId)
        next = setEnvValue(next, "TELEGRAM_API_HASH", apiHash)
        Files.createDirectories(localConfigPath.parent)
        Files.writeString(localConfigPath, next)
        return read()
    }

    private fun readLocalConfig(): String =
        if (Files.exists(localConfigPath)) Files.readString(localConfigPath) else ""

    private fun readEnvValue(content: String, name: String): String? {
        val pattern = Regex("""(?m)^\s*\${'$'}env:$name\s*=\s*'((?:''|[^'])*)'\s*$""")
        val value = pattern.find(content)?.groupValues?.getOrNull(1) ?: return null
        return value.replace("''", "'")
    }

    private fun setEnvValue(content: String, name: String, value: String): String {
        val line = "\$env:$name = '${powerShellSingleQuoted(value)}'"
        val pattern = Regex("""(?m)^\s*\${'$'}env:$name\s*=.*$""")
        if (pattern.containsMatchIn(content)) {
            return pattern.replace(content, line)
        }
        val prefix = content.trimEnd()
        return if (prefix.isEmpty()) "$line${System.lineSeparator()}" else "$prefix${System.lineSeparator()}$line${System.lineSeparator()}"
    }

    private fun powerShellSingleQuoted(value: String): String = value.replace("'", "''")

    private fun resolveProjectRootDir(): Path {
        val userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (userDir.fileName?.toString() == "backend") userDir.parent else userDir
    }
}
