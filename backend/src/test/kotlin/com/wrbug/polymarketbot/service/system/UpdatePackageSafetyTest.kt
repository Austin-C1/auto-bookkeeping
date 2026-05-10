package com.wrbug.polymarketbot.service.system

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class UpdatePackageSafetyTest {

    @Test
    fun `program files are allowed to be updated`() {
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("backend/build/libs/auto-bookkeeping-backend-1.0.1.jar"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("frontend/dist/index.html"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("frontend/dist/assets/index.js"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("scripts/serve-blackcat-frontend.ps1"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("scripts/serve-odds-frontend.ps1"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("launch-blackcat.ps1"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("launch-blackcat.cmd"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("open-blackcat-frontend.ps1"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("open-blackcat-frontend.cmd"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("start-blackcat-backend.ps1"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("start-telegram-bridge.ps1"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("start-telegram-bridge.cmd"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("telegram-bridge/package.json"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("telegram-bridge/package-lock.json"))
        assertTrue(UpdatePackageSafety.isAllowedProgramPath("telegram-bridge/server.mjs"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath(".tools/jdk-17.0.18+8/bin/java.exe"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("telegram-bridge/node_modules/telegram/index.js"))
    }

    @Test
    fun `user data and config paths are never overwritten`() {
        assertFalse(UpdatePackageSafety.isAllowedProgramPath(".env"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("config/local.json"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("config/local.env.ps1"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("config/update.json"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("data/mysql"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("data/telegram-session.txt"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("data/titan007/scores.json"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("telegram-bridge/data/telegram-session.txt"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("telegram-bridge/node_modules/telegram/index.js"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("whatsapp-bridge/.wwebjs_auth/session-blackcat-bookkeeping/Default/Cookies"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("whatsapp-bridge/.wwebjs_cache/2.3000.0/index.html"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("whatsapp-bridge/node_modules/whatsapp-web.js/index.js"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("logs/backend.log"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("backups/update.zip"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("backend-live.out.log"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("../outside.txt"))
        assertFalse(UpdatePackageSafety.isAllowedProgramPath("/absolute/path.txt"))
    }

    @Test
    fun `semantic versions compare correctly`() {
        assertTrue(UpdateVersionComparator.isNewer("3.0.2", "3.0.1"))
        assertTrue(UpdateVersionComparator.isNewer("v3.1.0", "3.0.9"))
        assertFalse(UpdateVersionComparator.isNewer("3.0.1", "3.0.1"))
        assertFalse(UpdateVersionComparator.isNewer("3.0.0", "3.0.1"))
    }

    @Test
    fun `default update repository points to auto bookkeeping releases`() {
        assertTrue(
            GitHubReleaseApiUrlBuilder.latestReleaseApiUrl(OddsMonitorUpdateDefaults.GITHUB_REPO)
                .endsWith("/Austin-C1/auto-bookkeeping/releases/latest")
        )
    }

    @Test
    fun `update service supports auto bookkeeping github environment names`() {
        val source = Files.readString(Path.of("src/main/kotlin/com/wrbug/polymarketbot/service/system/GitHubUpdateService.kt"))

        assertTrue(source.contains("AUTO_BOOKKEEPING_UPDATE_RELEASE_API_URL"))
        assertTrue(source.contains("AUTO_BOOKKEEPING_GITHUB_REPO"))
        assertTrue(source.contains("AUTO_BOOKKEEPING_GITHUB_TOKEN"))
    }

    @Test
    fun `github release api url encodes unicode repository names`() {
        assertTrue(
            GitHubReleaseApiUrlBuilder.latestReleaseApiUrl("Austin-C1/自动做账")
                .endsWith("/Austin-C1/%E8%87%AA%E5%8A%A8%E5%81%9A%E8%B4%A6/releases/latest")
        )
    }

    @Test
    fun `apply script uses encoded paths and real file list newlines`() {
        val script = UpdateApplyScriptBuilder.render(
            appRoot = Path.of("C:/Users/kesul/Desktop/新建文件夹/_tmp_auto_bookkeeping"),
            packageRoot = Path.of("C:/Users/kesul/Desktop/新建文件夹/_tmp_auto_bookkeeping/updates/work-v1.0.1"),
            backupRoot = Path.of("C:/Users/kesul/Desktop/新建文件夹/_tmp_auto_bookkeeping/backups/update-v1.0.1"),
            files = listOf(
                "backend/build/libs/auto-bookkeeping-backend-1.0.1.jar",
                "frontend/dist/index.html"
            ),
            backendPid = 12345
        )

        assertTrue(script.contains("[Convert]::FromBase64String"))
        assertFalse(script.contains("新建文件夹"))
        assertFalse(script.contains(",`n"))
        assertTrue(script.contains("'backend/build/libs/auto-bookkeeping-backend-1.0.1.jar',"))
        assertTrue(script.contains("'frontend/dist/index.html'"))
        assertTrue(script.contains("Stop-Process -Id 12345"))
        assertTrue(script.contains("*serve-blackcat-frontend.ps1*"))
        assertTrue(script.contains("*start-telegram-bridge.ps1*"))
        assertTrue(script.contains("*telegram-bridge*server.mjs*"))
        assertTrue(script.contains("launch-blackcat.ps1"))
        assertTrue(script.contains("update-apply.log"))
        assertTrue(script.contains("更新失败"))
        assertTrue(script.contains("finally"))
    }
}
