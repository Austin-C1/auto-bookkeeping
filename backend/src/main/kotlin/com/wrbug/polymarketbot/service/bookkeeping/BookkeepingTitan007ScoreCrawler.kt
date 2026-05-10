package com.wrbug.polymarketbot.service.bookkeeping

import com.wrbug.polymarketbot.dto.BookkeepingTitan007ScoreFetchResultDto
import com.wrbug.polymarketbot.dto.FetchBookkeepingTitan007ScoresRequest
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Component
class BookkeepingTitan007ScoreCrawler(
    @Value("\${bookkeeping.titan007.score-data-dir:data/titan007}")
    private val configuredOutputDir: String = "data/titan007"
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()
    private var pageFetcher: ((URI) -> ByteArray)? = null

    constructor(outputDir: Path, pageFetcher: (URI) -> ByteArray) : this(outputDir.toString()) {
        this.pageFetcher = pageFetcher
    }

    fun fetchScoreResults(request: FetchBookkeepingTitan007ScoresRequest): BookkeepingTitan007ScoreFetchResultDto {
        val businessDate = LocalDate.parse(request.businessDate)
        val sourceUrl = scoreUrl(businessDate)
        val html = decodeHtml(fetchPage(sourceUrl))
        val rows = parseScoreRows(
            html = html,
            businessDate = businessDate,
            leagueFilter = request.leagueFilter,
            startTime = request.startTime?.takeIf { it.isNotBlank() }?.let(LocalTime::parse),
            endTime = request.endTime?.takeIf { it.isNotBlank() }?.let(LocalTime::parse)
        )
        val outputPath = writeScoreWorkbook(businessDate, rows)
        return BookkeepingTitan007ScoreFetchResultDto(
            businessDate = businessDate.toString(),
            fetchedCount = rows.size,
            sourceUrl = sourceUrl.toString(),
            savedPath = outputPath.toString()
        )
    }

    private fun scoreUrl(businessDate: LocalDate): URI {
        val prefix = if (businessDate.isBefore(LocalDate.now())) "Over_" else "Next_"
        return URI.create("https://bf.titan007.com/football/$prefix${businessDate.format(DateTimeFormatter.BASIC_ISO_DATE)}.htm")
    }

    private fun fetchPage(uri: URI): ByteArray {
        pageFetcher?.let { return it(uri) }
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122 Safari/537.36")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        require(response.statusCode() in 200..299) { "titan007 fetch failed: HTTP ${response.statusCode()}" }
        return response.body()
    }

    private fun decodeHtml(bytes: ByteArray): String {
        val probe = String(bytes.take(4096).toByteArray(), StandardCharsets.ISO_8859_1)
        val charsetName = Regex("charset\\s*=\\s*['\"]?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
            .find(probe)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
        val charset = when (charsetName) {
            "gb2312", "gbk", "gb18030" -> Charset.forName("GB18030")
            "utf-8", "utf8" -> StandardCharsets.UTF_8
            else -> StandardCharsets.UTF_8
        }
        return String(bytes, charset)
    }

    private fun parseScoreRows(
        html: String,
        businessDate: LocalDate,
        leagueFilter: String?,
        startTime: LocalTime?,
        endTime: LocalTime?
    ): List<Titan007ParsedScoreRow> {
        val leagueFilters = leagueFilter
            ?.split(',', '，', ';', '；', '|')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        return Jsoup.parse(html).select("tr[id^=tr1_]").mapNotNull { row ->
            val columns = row.select("td")
            if (columns.size < 7) return@mapNotNull null
            val statusText = cleanText(columns[2])
            if (!isFinishedStatus(statusText)) return@mapNotNull null
            val matchTime = cleanText(columns[1])
            val parsedTime = parseTitanMatchTime(matchTime) ?: return@mapNotNull null
            if (!timeInRange(parsedTime, startTime, endTime)) return@mapNotNull null

            val league = columns[0].selectFirst("span")?.text()?.trim().orEmpty().ifBlank { cleanText(columns[0]) }
            if (leagueFilters.isNotEmpty() && leagueFilters.none { league.contains(it) }) return@mapNotNull null

            val score = normalizeScore(cleanText(columns[4]))
            if (score.isBlank()) return@mapNotNull null
            val halfScore = normalizeScore(cleanText(columns[6]))
            val matchResults = calculateMatchResults(score, halfScore)
            Titan007ParsedScoreRow(
                businessDate = businessDate.toString(),
                leagueName = league,
                matchTime = matchTime,
                homeTeam = cleanTeamName(columns[3]),
                fullTimeScore = score,
                awayTeam = cleanTeamName(columns[5]),
                halfTimeScore = halfScore,
                halfGoals = matchResults.halfGoals,
                fullGoals = matchResults.fullGoals,
                halfResult = matchResults.halfResult,
                fullResult = matchResults.fullResult
            )
        }
    }

    private fun writeScoreWorkbook(businessDate: LocalDate, rows: List<Titan007ParsedScoreRow>): Path {
        val outputDir = Paths.get(configuredOutputDir).toAbsolutePath().normalize()
        Files.createDirectories(outputDir)
        val outputPath = outputDir.resolve("${businessDate.format(DateTimeFormatter.BASIC_ISO_DATE)}_比赛数据.xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("比赛数据")
            val headers = listOf("日期", "联赛", "详细时间", "主队", "比分", "客队", "半场比分", "半场进球数", "全场进球数", "半场赛果", "全场赛果")
            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { index, header -> headerRow.createCell(index).setCellValue(header) }
            rows.forEachIndexed { rowIndex, item ->
                val row = sheet.createRow(rowIndex + 1)
                listOf(
                    item.businessDate,
                    item.leagueName,
                    item.matchTime,
                    item.homeTeam,
                    item.fullTimeScore,
                    item.awayTeam,
                    item.halfTimeScore,
                    item.halfGoals,
                    item.fullGoals,
                    item.halfResult,
                    item.fullResult
                ).forEachIndexed { cellIndex, value -> row.createCell(cellIndex).setCellValue(value?.toString().orEmpty()) }
            }
            headers.indices.forEach { sheet.autoSizeColumn(it) }
            Files.newOutputStream(outputPath).use { workbook.write(it) }
        }
        return outputPath
    }

    private fun cleanText(element: Element): String =
        element.text().replace(Regex("\\s+"), " ").trim()

    private fun cleanTeamName(element: Element): String {
        val clone = element.clone()
        clone.select("span[name=order]").remove()
        return cleanText(clone)
            .replace(Regex("^\\[[^]]+\\]"), "")
            .replace(Regex("\\[[^]]+\\]$"), "")
            .trim()
    }

    private fun parseTitanMatchTime(value: String): LocalTime? {
        val match = Regex("(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})").find(value)
            ?: Regex("(\\d{1,2}):(\\d{2})").find(value)
            ?: return null
        val hour = match.groupValues[match.groupValues.size - 2].toIntOrNull() ?: return null
        val minute = match.groupValues[match.groupValues.size - 1].toIntOrNull() ?: return null
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }

    private fun timeInRange(value: LocalTime, startTime: LocalTime?, endTime: LocalTime?): Boolean {
        if (startTime != null && value.isBefore(startTime)) return false
        if (endTime != null && value.isAfter(endTime)) return false
        return true
    }

    private fun isFinishedStatus(value: String): Boolean {
        val status = value.trim().lowercase()
        return status == "完" || status.contains("完") || status in setOf("ft", "aet", "pen")
    }

    private fun normalizeScore(value: String): String {
        val match = Regex("(\\d+)\\s*[-:：]\\s*(\\d+)").find(value) ?: return ""
        return "${match.groupValues[1]}-${match.groupValues[2]}"
    }

    private fun calculateMatchResults(score: String, halfScore: String): Titan007MatchResults {
        val half = splitScore(halfScore)
        val full = splitScore(score)
        return Titan007MatchResults(
            halfGoals = half?.let { it.first + it.second },
            fullGoals = full?.let { it.first + it.second },
            halfResult = half?.let(::matchResult),
            fullResult = full?.let(::matchResult)
        )
    }

    private fun splitScore(score: String): Pair<Int, Int>? {
        val parts = score.split("-")
        if (parts.size != 2) return null
        return (parts[0].toIntOrNull() ?: return null) to (parts[1].toIntOrNull() ?: return null)
    }

    private fun matchResult(score: Pair<Int, Int>): String = when {
        score.first > score.second -> "主胜"
        score.first < score.second -> "主负"
        else -> "主平"
    }
}

private data class Titan007ParsedScoreRow(
    val businessDate: String,
    val leagueName: String,
    val matchTime: String,
    val homeTeam: String,
    val fullTimeScore: String,
    val awayTeam: String,
    val halfTimeScore: String,
    val halfGoals: Int?,
    val fullGoals: Int?,
    val halfResult: String?,
    val fullResult: String?
)

private data class Titan007MatchResults(
    val halfGoals: Int?,
    val fullGoals: Int?,
    val halfResult: String?,
    val fullResult: String?
)
