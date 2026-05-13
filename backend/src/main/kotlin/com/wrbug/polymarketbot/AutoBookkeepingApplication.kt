package com.wrbug.polymarketbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AutoBookkeepingApplication

fun main(args: Array<String>) {
    runApplication<AutoBookkeepingApplication>(*args)
}

