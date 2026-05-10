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
import java.util.Collections

class BookkeepingTitan007ScoreCrawlerTest {
    @Test
    fun `crawler fetches over and next pages from titan007 and writes unique score workbook`(@TempDir outputDir: Path) {
        val requestedUrls = Collections.synchronizedList(mutableListOf<URI>())
        val crawler = BookkeepingTitan007ScoreCrawler(outputDir) { uri ->
            requestedUrls.add(uri)
            titanHtml().toByteArray(Charsets.UTF_8)
        }

        val result = crawler.fetchScoreResults(
            FetchBookkeepingTitan007ScoresRequest(businessDate = "2026-05-08")
        )

        assertEquals("2026-05-08", result.businessDate)
        assertEquals(
            setOf(
                "https://bf.titan007.com/football/Over_20260506.htm",
                "https://bf.titan007.com/football/Next_20260506.htm",
                "https://bf.titan007.com/football/Over_20260507.htm",
                "https://bf.titan007.com/football/Next_20260507.htm",
                "https://bf.titan007.com/football/Over_20260508.htm",
                "https://bf.titan007.com/football/Next_20260508.htm",
                "https://bf.titan007.com/football/Over_20260509.htm",
                "https://bf.titan007.com/football/Next_20260509.htm"
            ),
            requestedUrls.map { it.toString() }.toSet()
        )
        assertEquals(2, result.fetchedCount)
        assertTrue(Files.exists(outputDir.resolve("20260508_比赛数据.xlsx")))

        val lookup = BookkeepingTitan007ScoreResultReader(listOf(outputDir)).loadScoreLookup("2026-05-08")
        assertEquals("2-1", lookup.actualScore("诺丁汉 vs 曼城"))
        assertEquals("0-0", lookup.actualScore("清水心跳 vs 大阪樱花"))
    }

    @Test
    fun `crawler finds cross-day finished matches from the previous titan007 date page`(@TempDir outputDir: Path) {
        val crawler = BookkeepingTitan007ScoreCrawler(outputDir) { uri ->
            if (uri.toString().contains("Over_20260509")) crossDayTitanHtml().toByteArray(Charsets.UTF_8) else emptyTitanHtml().toByteArray(Charsets.UTF_8)
        }

        val result = crawler.fetchScoreResults(
            FetchBookkeepingTitan007ScoresRequest(businessDate = "2026-05-10")
        )

        assertEquals(5, result.fetchedCount)
        val lookup = BookkeepingTitan007ScoreResultReader(listOf(outputDir)).loadScoreLookup("2026-05-10")
        assertEquals("3-0", lookup.actualScore("扑雷索夫 v 斯卡利卡"))
        assertEquals("1-0", lookup.actualScore("布尔格佩罗纳斯 v 瓦朗谢讷"))
        assertEquals("1-0", lookup.actualScore("桑德维肯斯 v 卢恩斯基尔"))
        assertEquals("2-0", lookup.actualScore("马约 v 鲁毕奥"))
        assertEquals("3-2", lookup.actualScore("布拉格斯拉维亚 v 布拉格斯巴达"))
    }

    @Test
    fun `crawler finds company rolling matches from two titan007 source dates before business date`(@TempDir outputDir: Path) {
        val crawler = BookkeepingTitan007ScoreCrawler(outputDir) { uri ->
            if (uri.toString().contains("Over_20260508")) companyRollingTitanHtml().toByteArray(Charsets.UTF_8) else emptyTitanHtml().toByteArray(Charsets.UTF_8)
        }

        val result = crawler.fetchScoreResults(
            FetchBookkeepingTitan007ScoresRequest(businessDate = "2026-05-10")
        )

        assertEquals(5, result.fetchedCount)
        val lookup = BookkeepingTitan007ScoreResultReader(listOf(outputDir)).loadScoreLookup("2026-05-10")
        assertEquals("2-0", lookup.actualScore("琴斯托霍瓦 v 科罗纳"))
        assertEquals("3-2", lookup.actualScore("华保斯 v 渥那模"))
        assertEquals("0-0", lookup.actualScore("克卢日 v CS卡拉奥华大学"))
        assertEquals("2-2", lookup.actualScore("蒙扎 v 恩波利"))
        assertEquals("1-0", lookup.actualScore("德罗赫达联 v 德利城"))
    }

    @Test
    fun `crawler keeps scores from successful source when another source fails`(@TempDir outputDir: Path) {
        val crawler = BookkeepingTitan007ScoreCrawler(outputDir) { uri ->
            if (uri.toString().contains("Over_20260508")) {
                throw RuntimeException("source timeout")
            }
            titanHtml().toByteArray(Charsets.UTF_8)
        }

        val result = crawler.fetchScoreResults(
            FetchBookkeepingTitan007ScoresRequest(businessDate = "2026-05-08")
        )

        assertEquals(2, result.fetchedCount)
        val lookup = BookkeepingTitan007ScoreResultReader(listOf(outputDir)).loadScoreLookup("2026-05-08")
        assertEquals("2-1", lookup.actualScore("诺丁汉 vs 曼城"))
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

    private fun emptyTitanHtml(): String = """
        <html>
          <body>
            <table id="table_live"></table>
          </body>
        </html>
    """.trimIndent()

    private fun crossDayTitanHtml(): String = """
        <html>
          <body>
            <table id="table_live">
              <tr height="18" align="center" id="tr1_10" sId="20010">
                <td><span>斯伐超降</span></td>
                <td>10日02:30</td>
                <td>完</td>
                <td align="right"><span name="order"><font>[11]</font></span>扑雷索夫</td>
                <td><b>3-0</b></td>
                <td align="left">斯卡利卡<span name="order"><font>[12]</font></span></td>
                <td>2-0</td>
              </tr>
              <tr height="18" align="center" id="tr1_11" sId="20011">
                <td><span>法丙</span></td>
                <td>10日01:30</td>
                <td>完</td>
                <td align="right"><span name="order"><font>[15]</font></span>普瑞兰斯</td>
                <td><b>1-0</b></td>
                <td align="left">瓦朗谢纳<span name="order"><font>[9]</font></span></td>
                <td>0-0</td>
              </tr>
              <tr height="18" align="center" id="tr1_12" sId="20012">
                <td><span>瑞典甲</span></td>
                <td>9日23:00</td>
                <td>完</td>
                <td align="right"><span name="order"><font>[15]</font></span>桑德维根斯</td>
                <td><b>1-0</b></td>
                <td align="left">卢恩斯基尔<span name="order"><font>[14]</font></span></td>
                <td>1-0</td>
              </tr>
              <tr height="18" align="center" id="tr1_13" sId="20013">
                <td><span>巴拉甲春</span></td>
                <td>10日04:45</td>
                <td>完</td>
                <td align="right"><span name="order"><font>[春11]</font></span>五月二日体育会</td>
                <td><b>2-0</b></td>
                <td align="left">鲁毕奥<span name="order"><font>[春9]</font></span></td>
                <td>1-0</td>
              </tr>
              <tr height="18" align="center" id="tr1_14" sId="20014">
                <td><span>捷甲冠</span></td>
                <td>10日01:00</td>
                <td>腰斩</td>
                <td align="right"><span name="order"><font>[1]</font></span>布拉格斯拉维亚</td>
                <td><b>3-2</b></td>
                <td align="left">布拉格斯巴达<span name="order"><font>[2]</font></span></td>
                <td>1-1</td>
              </tr>
            </table>
          </body>
        </html>
    """.trimIndent()

    private fun companyRollingTitanHtml(): String = """
        <html>
          <body>
            <table id="table_live">
              <tr height="18" align="center" id="tr1_20" sId="30020">
                <td><span>波兰超</span></td>
                <td>9日00:00</td>
                <td>完</td>
                <td align="right"><span name="order"><font>[4]</font></span>琴斯托霍瓦</td>
                <td><b>2-0</b></td>
                <td align="left">哥罗纳<span name="order"><font>[14]</font></span></td>
                <td>0-0</td>
              </tr>
              <tr height="18" align="center" id="tr1_21" sId="30021">
                <td><span>瑞典甲</span></td>
                <td>9日01:00</td>
                <td>完</td>
                <td align="right"><span name="order"><font>[12]</font></span>瓦尔贝里</td>
                <td><b>3-2</b></td>
                <td align="left">韦纳穆<span name="order"><font>[5]</font></span></td>
                <td>1-0</td>
              </tr>
              <tr height="18" align="center" id="tr1_22" sId="30022">
                <td><span>罗甲冠</span></td>
                <td>9日02:00</td>
                <td>完</td>
                <td align="right"><span name="order"><font>[4]</font></span>克卢日</td>
                <td><b>0-0</b></td>
                <td align="left">克拉约瓦大学<span name="order"><font>[1]</font></span></td>
                <td>0-0</td>
              </tr>
              <tr height="18" align="center" id="tr1_23" sId="30023">
                <td><span>意乙</span></td>
                <td>9日02:30</td>
                <td>完</td>
                <td align="right"><span name="order"><font>[3]</font></span>蒙扎</td>
                <td><b>2-2</b></td>
                <td align="left">恩波利<span name="order"><font>[14]</font></span></td>
                <td>1-1</td>
              </tr>
              <tr height="18" align="center" id="tr1_24" sId="30024">
                <td><span>爱超</span></td>
                <td>9日02:45</td>
                <td>完</td>
                <td align="right"><span name="order"><font>[8]</font></span>德罗赫达联</td>
                <td><b>1-0</b></td>
                <td align="left">德利城<span name="order"><font>[5]</font></span></td>
                <td>0-0</td>
              </tr>
            </table>
          </body>
        </html>
    """.trimIndent()

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
