package com.wrbug.polymarketbot.util

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

class SourceEncodingGuardTest {
    @Test
    fun `main kotlin source does not contain common mojibake markers`() {
        val root = Path.of("src/main/kotlin")
        val offenders = Files.walk(root).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.extension == "kt" }
                .flatMap { path ->
                    val text = Files.readString(path)
                    mojibakeMarkers()
                        .filter { marker -> text.contains(marker) }
                        .map { marker -> "${root.relativize(path)} contains U+${marker[0].code.toString(16).uppercase()}" }
                        .stream()
                }
                .toList()
        }

        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }

    private fun mojibakeMarkers(): List<String> =
        listOf(
            0x9A9E,
            0x9428,
            0x947B,
            0x59AF,
            0x7481,
            0x6FB6,
            0x7459,
            0x9427,
            0x7035,
            0x95C2,
            0x93B6,
            0x7039,
            0x9368,
            0x68BD,
            0x9288,
            0x95B9,
            0x59D8,
            0x928F,
            0x790B
        ).map { it.toChar().toString() }
}
