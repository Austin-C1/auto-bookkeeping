package com.wrbug.polymarketbot.service.bookkeeping

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.springframework.stereotype.Component
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

data class CrownLoginConfig(
    val displayName: String,
    val username: String,
    val password: String,
    val baseUrl: String
)

data class CrownSession(
    val uid: String,
    val cookies: Map<String, String>
)

class CrownLoginException(
    override val message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

@Component
class CrownLoginClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun login(config: CrownLoginConfig): CrownSession {
        val username = config.username.takeIf { it.isNotBlank() }
            ?: throw CrownLoginException("crown username is empty")
        val password = config.password.takeIf { it.isNotBlank() }
            ?: throw CrownLoginException("crown password is empty")

        val cookieJar = linkedMapOf<String, String>()
        val response = postForm(
            baseUrl = config.baseUrl.ifBlank { DEFAULT_BASE_URL },
            path = "/transform_nl.php",
            form = mapOf(
                "p" to "chk_login",
                "langx" to "zh-cn",
                "username" to username,
                "password" to password,
                "app" to "N",
                "auto" to "CDBHGD",
                "blackbox" to ""
            ),
            cookies = cookieJar
        )
        val login = parseLogin(response)
        if (login.status != "200" || login.uid.isNullOrBlank()) {
            val details = listOfNotNull(login.messageCode, login.message).joinToString(": ")
            val suffix = details.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            throw CrownLoginException("crown login failed with status ${login.status}$suffix")
        }
        return CrownSession(uid = login.uid, cookies = cookieJar.toMap())
    }

    private fun postForm(
        baseUrl: String,
        path: String,
        form: Map<String, String>,
        cookies: MutableMap<String, String>
    ): String {
        val bodyBuilder = FormBody.Builder(Charsets.UTF_8)
        form.forEach { (key, value) -> bodyBuilder.add(key, value) }

        val requestBuilder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .post(bodyBuilder.build())
            .header("Accept", "application/xml,text/xml,*/*")
            .header("User-Agent", "Mozilla/5.0")

        if (cookies.isNotEmpty()) {
            requestBuilder.header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            updateCookies(response, cookies)
            if (!response.isSuccessful) {
                throw CrownLoginException("crown http ${response.code}")
            }
            return decodeResponseBody(response)
        }
    }

    private fun parseLogin(xmlText: String): CrownLoginResponse {
        val root = parseRoot(xmlText)
        return CrownLoginResponse(
            status = root.childText("status").orEmpty(),
            uid = root.childText("uid")?.takeIf { it.isNotBlank() },
            messageCode = root.childText("msg")?.takeIf { it.isNotBlank() },
            message = root.childText("code_message")?.takeIf { it.isNotBlank() }
        )
    }

    private fun parseRoot(xmlText: String): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val builder = factory.newDocumentBuilder()
        return builder.parse(InputSource(StringReader(xmlText))).documentElement
    }

    private fun Element.childText(name: String): String? =
        getElementsByTagName(name).item(0)?.textContent?.trim()

    private fun decodeResponseBody(response: Response): String {
        val body = response.body ?: return ""
        val bytes = body.bytes()
        if (bytes.isEmpty()) return ""
        val declaredCharset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val declaredText = String(bytes, declaredCharset)
        val gbText = runCatching { String(bytes, Charset.forName("GB18030")) }.getOrNull()
            ?: return declaredText
        return if (isBetterDecodedText(gbText, declaredText)) gbText else declaredText
    }

    private fun isBetterDecodedText(candidate: String, current: String): Boolean {
        val candidateCjk = candidate.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        val currentCjk = current.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        val candidateBroken = candidate.count { it == '\uFFFD' || it == '?' }
        val currentBroken = current.count { it == '\uFFFD' || it == '?' }
        return candidateCjk > currentCjk && candidateBroken <= currentBroken
    }

    private fun updateCookies(response: Response, cookies: MutableMap<String, String>) {
        response.headers("Set-Cookie").forEach { header ->
            val pair = header.substringBefore(';')
            val name = pair.substringBefore('=', "")
            val value = pair.substringAfter('=', "")
            if (name.isNotBlank()) {
                cookies[name] = value
            }
        }
    }

    private data class CrownLoginResponse(
        val status: String,
        val uid: String?,
        val messageCode: String?,
        val message: String?
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://m407.mos077.com"
    }
}
