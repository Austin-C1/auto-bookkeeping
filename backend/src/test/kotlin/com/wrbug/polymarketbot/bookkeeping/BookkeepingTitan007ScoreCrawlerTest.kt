package com.wrbug.polymarketbot.bookkeeping

import com.wrbug.polymarketbot.dto.FetchBookkeepingTitan007ScoresRequest
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingTitan007ScoreCrawler
import com.wrbug.polymarketbot.service.bookkeeping.BookkeepingTitan007ScoreResultReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class BookkeepingTitan007ScoreCrawlerTest {
    @Test
    fun `crawler fetches one date from titan007 and writes score workbook`(@TempDir outputDir: Path) {
        val requestedUrl = AtomicReference<URI>()
        val crawler = BookkeepingTitan007ScoreCrawler(outputDir) { uri ->
            requestedUrl.set(uri)
            titanHtml().toByteArray(Charsets.UTF_8)
        }

        val result = crawler.fetchScoreResults(
            FetchBookkeepingTitan007ScoresRequest(businessDate = "2026-05-08")
        )

        assertEquals("2026-05-08", result.businessDate)
        assertEquals("https://bf.titan007.com/football/Over_20260508.htm", requestedUrl.get().toString())
        assertEquals(2, result.fetchedCount)
        assertTrue(Files.exists(outputDir.resolve("20260508_比赛数据.xlsx")))

        val lookup = BookkeepingTitan007ScoreResultReader(listOf(outputDir)).loadScoreLookup("2026-05-08")
        assertEquals("2-1", lookup.actualScore("诺丁汉 vs 曼城"))
        assertEquals("0-0", lookup.actualScore("清水心跳 vs 大阪樱花"))
    }

    @Test
    fun `crawler applies league and time filters before writing scores`(@TempDir outputDir: Path) {
        val crawler = BookkeepingTitan007ScoreCrawler(outputDir) { titanHtml().toByteArray(Charsets.UTF_8) }

        val result = crawler.fetchScoreResults(
            FetchBookkeepingTitan007ScoresRequest(
                businessDate = "2026-05-08",
                leagueFilter = "英超",
                startTime = "22:00",
                endTime = "23:00"
            )
        )

        assertEquals(1, result.fetchedCount)
        val lookup = BookkeepingTitan007ScoreResultReader(listOf(outputDir)).loadScoreLookup("2026-05-08")
        assertEquals("2-1", lookup.actualScore("诺丁汉 vs 曼城"))
        assertEquals("", lookup.actualScore("清水心跳 vs 大阪樱花"))
    }

    private fun titanHtml(): String = """
        <html>
          <body>
            <table id="table_live">
              <tr height="18" align="center" id="tr1_1" sId="10001">
                <td><span>英超</span></td>
                <td>5-08 22:30</td>
                <td>完</td>
                <td align="right"><span name="order"><font>[1]</font></span>诺丁汉</td>
                <td><b>2-1</b></td>
                <td align="left">曼城<span name="order"><font>[2]</font></span></td>
                <td>1-0</td>
                <td></td>
                <td></td>
                <td><a onclick="AsianOdds(10001)">亚</a></td>
              </tr>
              <tr height="18" align="center" id="tr1_2" sId="10002">
                <td><span>日职联</span></td>
                <td>5-08 19:00</td>
                <td>完</td>
                <td align="right">清水心跳</td>
                <td><b>0-0</b></td>
                <td align="left">大阪樱花</td>
                <td>0-0</td>
                <td></td>
                <td></td>
                <td><a onclick="AsianOdds(10002)">亚</a></td>
              </tr>
              <tr height="18" align="center" id="tr1_3" sId="10003">
                <td><span>英超</span></td>
                <td>5-08 22:45</td>
                <td>中</td>
                <td align="right">利物浦</td>
                <td><b>1-0</b></td>
                <td align="left">切尔西</td>
                <td>1-0</td>
                <td></td>
                <td></td>
                <td><a onclick="AsianOdds(10003)">亚</a></td>
              </tr>
            </table>
          </body>
        </html>
    """.trimIndent()
}
