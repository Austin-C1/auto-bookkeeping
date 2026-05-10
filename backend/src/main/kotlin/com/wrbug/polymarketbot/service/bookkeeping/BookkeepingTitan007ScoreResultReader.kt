package com.wrbug.polymarketbot.service.bookkeeping

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Component
class BookkeepingTitan007ScoreResultReader(
    @Value("\${bookkeeping.titan007.score-data-dirs:}")
    private val configuredScoreDataDirs: String = ""
) {
    private var includeDefaultScoreDataDirs: Boolean = true

    constructor(scoreDataDirs: List<Path>) : this(scoreDataDirs.joinToString(";") { it.toString() }) {
        includeDefaultScoreDataDirs = false
    }

    fun loadScoreLookup(businessDate: String): BookkeepingTitan007ScoreLookup {
        val rows = scoreResultFiles(businessDate).flatMap { file -> readScoreRows(file, businessDate) }
        return BookkeepingTitan007ScoreLookup(rows)
    }

    private fun scoreResultFiles(businessDate: String): List<Path> {
        val compactDate = businessDate.filter(Char::isDigit)
        return scoreDataDirs().flatMap { dir ->
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                emptyList()
            } else {
                Files.walk(dir, 3).use { stream ->
                    val excelFiles = stream
                        .filter { Files.isRegularFile(it) }
                        .filter { path -> path.fileName.toString().startsWith("~$").not() }
                        .filter { path -> path.fileName.toString().lowercase().let { it.endsWith(".xlsx") || it.endsWith(".xls") } }
                        .toList()
                    excelFiles.filter { path ->
                        val name = path.fileName.toString()
                        name.contains(compactDate) || name.contains(businessDate)
                    }.ifEmpty { excelFiles }
                }
            }
        }.distinct()
    }

    private fun scoreDataDirs(): List<Path> {
        val configured = configuredScoreDataDirs
            .split(';', ',', '\n', '|')
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }
            .map { Paths.get(it).toAbsolutePath().normalize() }

        val defaults = listOf(
            Paths.get("data", "titan007"),
            Paths.get("data"),
            Paths.get("爬虫工具", "data"),
            Paths.get("..", "爬虫工具", "data"),
            Paths.get("..", "..", "爬虫工具", "data")
        ).map { it.toAbsolutePath().normalize() }

        return (configured + if (includeDefaultScoreDataDirs) defaults else emptyList()).distinct()
    }

    private fun readScoreRows(file: Path, businessDate: String): List<BookkeepingTitan007ScoreRow> {
        return runCatching {
            WorkbookFactory.create(file.toFile()).use { workbook ->
                val formatter = DataFormatter()
                (0 until workbook.numberOfSheets).flatMap { sheetIndex ->
                    val sheet = workbook.getSheetAt(sheetIndex)
                    val headerRow = (0..10)
                        .mapNotNull { rowIndex -> sheet.getRow(rowIndex) }
                        .firstOrNull { row -> headerIndex(row, formatter, "主队") != null && headerIndex(row, formatter, "客队") != null }
                        ?: return@flatMap emptyList()

                    val headerRowIndex = headerRow.rowNum
                    val dateIndex = headerIndex(headerRow, formatter, "日期", "比赛日期")
                    val leagueIndex = headerIndex(headerRow, formatter, "联赛", "联赛类型")
                    val homeIndex = headerIndex(headerRow, formatter, "主队")
                        ?: return@flatMap emptyList()
                    val awayIndex = headerIndex(headerRow, formatter, "客队")
                        ?: return@flatMap emptyList()
                    val scoreIndex = headerIndex(headerRow, formatter, "比分", "全场比分", "完场比分")
                        ?: return@flatMap emptyList()

                    ((headerRowIndex + 1)..sheet.lastRowNum).mapNotNull { rowIndex ->
                        val row = sheet.getRow(rowIndex) ?: return@mapNotNull null
                        val homeTeam = cellText(row, homeIndex, formatter)
                        val awayTeam = cellText(row, awayIndex, formatter)
                        val fullTimeScore = normalizeScore(cellText(row, scoreIndex, formatter))
                        if (homeTeam.isBlank() || awayTeam.isBlank() || fullTimeScore.isBlank()) {
                            return@mapNotNull null
                        }
                        val rowDate = dateIndex
                            ?.let { cellText(row, it, formatter) }
                            ?.let(::normalizeDateText)
                            ?.ifBlank { businessDate }
                            ?: businessDate
                        if (rowDate != businessDate) {
                            return@mapNotNull null
                        }
                        BookkeepingTitan007ScoreRow(
                            businessDate = rowDate,
                            leagueName = leagueIndex?.let { cellText(row, it, formatter) },
                            homeTeam = homeTeam,
                            awayTeam = awayTeam,
                            fullTimeScore = fullTimeScore
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun headerIndex(row: Row, formatter: DataFormatter, vararg aliases: String): Int? {
        return (row.firstCellNum.toInt() until row.lastCellNum.toInt()).firstOrNull { index ->
            val header = formatter.formatCellValue(row.getCell(index)).trim()
            aliases.any { alias -> header == alias || header.contains(alias) }
        }
    }

    private fun cellText(row: Row, index: Int, formatter: DataFormatter): String =
        formatter.formatCellValue(row.getCell(index)).trim()

    private fun normalizeDateText(value: String): String {
        val digits = value.filter(Char::isDigit)
        return if (digits.length >= 8) {
            "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
        } else {
            ""
        }
    }

    private fun normalizeScore(value: String): String {
        val match = Regex("(\\d+)\\s*[-:：]\\s*(\\d+)").find(value) ?: return ""
        return "${match.groupValues[1]}-${match.groupValues[2]}"
    }
}

data class BookkeepingTitan007ScoreRow(
    val businessDate: String,
    val leagueName: String?,
    val homeTeam: String,
    val awayTeam: String,
    val fullTimeScore: String
)

class BookkeepingTitan007ScoreLookup(rows: List<BookkeepingTitan007ScoreRow>) {
    private val scoresByTeams = rows.associate { row ->
        ScoreKey(normalizeTeam(row.homeTeam), normalizeTeam(row.awayTeam)) to row.fullTimeScore
    }

    fun actualScore(matchName: String?): String {
        val teams = splitMatchName(matchName) ?: return ""
        return actualScore(teams.first, teams.second)
    }

    fun actualScore(homeTeam: String?, awayTeam: String?): String {
        val home = normalizeTeam(homeTeam)
        val away = normalizeTeam(awayTeam)
        if (home.isBlank() || away.isBlank()) return ""
        scoresByTeams[ScoreKey(home, away)]?.let { return it }
        return scoresByTeams[ScoreKey(away, home)]?.let(::reverseScore).orEmpty()
    }

    private data class ScoreKey(val homeTeam: String, val awayTeam: String)

    private companion object {
        fun splitMatchName(value: String?): Pair<String, String>? {
            val cleaned = value?.trim().orEmpty()
            if (cleaned.isBlank()) return null
            val parts = cleaned.split(Regex("\\s+(?i:v(?:s)?\\.?)\\s+|\\s*[对對]\\s*|\\s*[-－]\\s*"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (parts.size >= 2) return parts[0] to parts[1]
            val compactV = Regex("^(.+?)(?i:vs?\\.?)\\s*(.+)$").find(cleaned) ?: return null
            val textWithoutSeparator = cleaned.replace(Regex("(?i:vs?\\.?)"), "")
            return if (textWithoutSeparator.none { it in 'a'..'z' || it in 'A'..'Z' }) {
                compactV.groupValues[1].trim() to compactV.groupValues[2].trim()
            } else {
                null
            }
        }

        fun normalizeTeam(value: String?): String =
            value.orEmpty()
                .lowercase()
                .replace(Regex("\\s+"), "")
                .replace("足球俱乐部", "")
                .replace("俱乐部", "")
                .replace("fc", "")
                .replace(Regex("[()（）\\[\\]【】]"), "")

        fun reverseScore(score: String): String {
            val parts = score.split("-")
            return if (parts.size == 2) "${parts[1]}-${parts[0]}" else score
        }
    }
}
