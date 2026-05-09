package com.wrbug.polymarketbot.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextEncodingUtilsTest {
    @Test
    fun `repairs gbk-decoded utf8 mojibake`() {
        assertEquals("平博", TextEncodingUtils.repairMojibake(mojibake(0x9A9E, 0x51B2, 0x5D25)))
        assertEquals("皇冠", TextEncodingUtils.repairMojibake(mojibake(0x9428, 0x56E7, 0x555D)))
        assertEquals("英超", TextEncodingUtils.repairMojibake(mojibake(0x947B, 0x8FAB, 0x79F4)))
    }

    @Test
    fun `keeps valid readable text unchanged`() {
        assertEquals("加拿大超级联赛", TextEncodingUtils.repairMojibake("加拿大超级联赛"))
        assertEquals("Polymarket", TextEncodingUtils.repairMojibake("Polymarket"))
    }

    private fun mojibake(vararg chars: Int): String =
        chars.map { it.toChar() }.joinToString("")
}
