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
    private val scoreRows = rows
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
        scoresByTeams[ScoreKey(away, home)]?.let { return reverseScore(it) }
        return fuzzyActualScore(home, away)
    }

    private fun fuzzyActualScore(home: String, away: String): String {
        val best = scoreRows
            .flatMap { row ->
                listOf(
                    FuzzyScoreCandidate(
                        score = row.fullTimeScore,
                        homeScore = teamSimilarity(home, normalizeTeam(row.homeTeam)),
                        awayScore = teamSimilarity(away, normalizeTeam(row.awayTeam)),
                        reversed = false
                    ),
                    FuzzyScoreCandidate(
                        score = row.fullTimeScore,
                        homeScore = teamSimilarity(home, normalizeTeam(row.awayTeam)),
                        awayScore = teamSimilarity(away, normalizeTeam(row.homeTeam)),
                        reversed = true
                    )
                )
            }
            .filter { it.homeScore >= FUZZY_TEAM_MATCH_THRESHOLD && it.awayScore >= FUZZY_TEAM_MATCH_THRESHOLD }
            .maxByOrNull { it.combinedScore }
            ?: return ""

        return if (best.reversed) reverseScore(best.score) else best.score
    }

    private data class ScoreKey(val homeTeam: String, val awayTeam: String)

    private data class FuzzyScoreCandidate(
        val score: String,
        val homeScore: Double,
        val awayScore: Double,
        val reversed: Boolean
    ) {
        val combinedScore: Double = homeScore + awayScore
    }

    private companion object {
        const val FUZZY_TEAM_MATCH_THRESHOLD = 0.78

        val TEAM_ALIASES: Map<String, String> = mapOf(
            "布尔格佩罗纳斯" to "普瑞兰斯",
            "布尔格佩罗纳" to "普瑞兰斯",
            "bourgenbresseperonnas" to "普瑞兰斯",
            "bourgperonnas" to "普瑞兰斯",
            "瓦朗谢讷" to "瓦朗谢纳",
            "valenciennes" to "瓦朗谢纳",
            "桑德维肯斯" to "桑德维根斯",
            "sandvikens" to "桑德维根斯",
            "马约" to "五月二日体育会",
            "2demayo" to "五月二日体育会",
            "club2demayo" to "五月二日体育会",
            "鲁比奥" to "鲁毕奥",
            "rubionu" to "鲁毕奥",
            "rubioñu" to "鲁毕奥",
            "科罗纳" to "哥罗纳",
            "koronakielce" to "哥罗纳",
            "华保斯" to "瓦尔贝里",
            "varbergs" to "瓦尔贝里",
            "渥那模" to "韦纳穆",
            "varnamo" to "韦纳穆",
            "ifkvarnamo" to "韦纳穆",
            "cs卡拉奥华大学" to "克拉约瓦大学",
            "卡拉奥华大学" to "克拉约瓦大学",
            "universitateacraiova" to "克拉约瓦大学",
            "craiova" to "克拉约瓦大学"
        )

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
                .let { TEAM_ALIASES[it] ?: it }

        fun reverseScore(score: String): String {
            val parts = score.split("-")
            return if (parts.size == 2) "${parts[1]}-${parts[0]}" else score
        }

        fun teamSimilarity(left: String, right: String): Double {
            if (left.isBlank() || right.isBlank()) return 0.0
            if (left == right) return 1.0
            if ((left.contains(right) || right.contains(left)) && minOf(left.length, right.length) >= 2) return 0.92
            val maxLength = maxOf(left.length, right.length)
            if (maxLength == 0) return 0.0
            return 1.0 - (levenshteinDistance(left, right).toDouble() / maxLength.toDouble())
        }

        fun levenshteinDistance(left: String, right: String): Int {
            if (left == right) return 0
            if (left.isEmpty()) return right.length
            if (right.isEmpty()) return left.length
            var previous = IntArray(right.length + 1) { it }
            var current = IntArray(right.length + 1)
            for (i in left.indices) {
                current[0] = i + 1
                for (j in right.indices) {
                    val substitutionCost = if (left[i] == right[j]) 0 else 1
                    current[j + 1] = minOf(
                        current[j] + 1,
                        previous[j + 1] + 1,
                        previous[j] + substitutionCost
                    )
                }
                val tmp = previous
                previous = current
                current = tmp
            }
            return previous[right.length]
        }
    }
}
