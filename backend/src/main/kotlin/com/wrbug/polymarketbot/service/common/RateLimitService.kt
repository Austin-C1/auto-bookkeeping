package com.wrbug.polymarketbot.service.common

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class RateLimitService {

    private val logger = LoggerFactory.getLogger(RateLimitService::class.java)
    @Value("\${rate-limit.login.max-attempts:5}")
    private var loginMaxAttempts: Int = 5

    @Value("\${rate-limit.login.window-seconds:300}")
    private var loginWindowSeconds: Long = 300

    @Value("\${rate-limit.login.lockout-seconds:900}")
    private var loginLockoutSeconds: Long = 900
    private val loginFailedAttempts = ConcurrentHashMap<String, MutableList<Long>>()
    private val loginLockouts = ConcurrentHashMap<String, Long>()

    fun checkLoginRateLimit(ipAddress: String): Result<Unit> {
        val now = System.currentTimeMillis()
        val lockoutEndTime = loginLockouts[ipAddress]
        if (lockoutEndTime != null) {
            if (now < lockoutEndTime) {
                val remainingSeconds = (lockoutEndTime - now) / 1000
                logger.warn("登录锁定中: ip=$ipAddress, remainingSeconds=$remainingSeconds")
                return Result.failure(IllegalStateException("账户已被锁定，请${remainingSeconds}秒后再试"))
            } else {
                loginLockouts.remove(ipAddress)
                loginFailedAttempts.remove(ipAddress)
            }
        }

        return Result.success(Unit)
    }

    fun recordLoginFailure(ipAddress: String): String? {
        val now = System.currentTimeMillis()
        val windowStart = now - (loginWindowSeconds * 1000)
        val attempts = loginFailedAttempts.computeIfAbsent(ipAddress) { mutableListOf() }
        synchronized(attempts) {
            attempts.removeIf { it < windowStart }
            attempts.add(now)
            if (attempts.size >= loginMaxAttempts) {
                val lockoutEndTime = now + (loginLockoutSeconds * 1000)
                loginLockouts[ipAddress] = lockoutEndTime
                logger.warn("登录锁定触发: ip=$ipAddress, attempts=${attempts.size}, lockoutSeconds=$loginLockoutSeconds")
                return "登录失败次数过多，账户已被锁定${loginLockoutSeconds / 60}分钟"
            }
        }

        val remainingAttempts = loginMaxAttempts - attempts.size
        logger.warn("登录失败: ip=$ipAddress, attempts=${attempts.size}/$loginMaxAttempts, remainingAttempts=$remainingAttempts")
        return null
    }

    fun clearLoginFailures(ipAddress: String) {
        loginFailedAttempts.remove(ipAddress)
        loginLockouts.remove(ipAddress)
    }
}

