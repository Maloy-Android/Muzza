package com.maloy.muzza

import com.maloy.innertube.YouTube
import com.maloy.innertube.utils.parseCookieString
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.response.AccountMenuResponse
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

internal fun normalizedAccountCookie(cookie: String): String {
    require(cookie.length in 1..32_768 && cookie.none { it == '\r' || it == '\n' }) {
        "Invalid YouTube session cookie."
    }
    val parts = cookie.split(';').map(String::trim).filter(String::isNotEmpty)
    val separators = "()<>@,;:\\\"/[]?={} \t"
    require(parts.all { part ->
        val index = part.indexOf('=')
        index > 0 && part.take(index).all { it.code in 33..126 && it !in separators } &&
            part.substring(index + 1).none { it.code < 32 || it.code == 127 }
    }) { "Invalid YouTube session cookie." }
    val normalized = parts.joinToString("; ")
    require(!parseCookieString(normalized)["SAPISID"].isNullOrBlank()) {
        "The YouTube session does not contain SAPISID."
    }
    return normalized
}

internal fun validatedVisitorData(visitorData: String?): String? =
    visitorData?.trim()?.takeIf(String::isNotEmpty)?.also {
        require(it.length <= 1_024 && it.none { char -> char.code < 32 || char.code == 127 }) {
            "Invalid YouTube visitor data."
        }
    }

fun validateIosAccount(cookie: String, visitorData: String?, completion: (String) -> Unit) =
    iosRequest(completion) {
        val normalized = normalizedAccountCookie(cookie)
        val visitor = validatedVisitorData(visitorData)
        val session = YouTube.extractionTransport().innerTube.createIsolatedSession(includeAccount = false)
        try {
            session.replaceSession(normalized, visitor, null, "0", true)
            val response = session.accountMenu(YouTubeClient.WEB_REMIX)
            check(response.status.isSuccess()) { "YouTube account verification failed (HTTP ${response.status.value})." }
            val body = response.body<AccountMenuResponse>()
            val header = body.actions.firstNotNullOfOrNull {
                it.openPopupAction?.popup?.multiPageMenuRenderer?.header?.activeAccountHeaderRenderer
            } ?: throw IllegalStateException("YouTube did not confirm the account.")
            val account = header.toAccountInfo()
            check(account.name.isNotBlank() && account.name != "Unknown") { "YouTube did not confirm the account." }
            IosBridgeResponse(account = IosAccountProfile(
                name = account.name,
                email = account.email,
                channelHandle = account.channelHandle,
                thumbnailUrl = account.thumbnailUrl,
            ))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException("Could not verify the YouTube account. Check the session and try again.", error)
        } finally {
            session.close()
        }
    }

fun activateIosAccount(cookie: String, visitorData: String?, completion: (String) -> Unit) =
    iosRequest(completion) {
        val normalized = normalizedAccountCookie(cookie)
        val visitor = validatedVisitorData(visitorData)
        val transport = YouTube.extractionTransport().innerTube
        val previous = transport.sessionSnapshot()
        val previousCookie = YouTube.cookie
        val previousVisitor = YouTube.visitorData
        val previousDataSyncId = YouTube.dataSyncId
        val previousBrowse = YouTube.useLoginForBrowse
        try {
            transport.replaceSession(normalized, visitor, null, "0", true)
            YouTube.cookie = normalized
            YouTube.visitorData = visitor
            YouTube.dataSyncId = null
            YouTube.useLoginForBrowse = true
        } catch (error: Exception) {
            transport.replaceSession(
                previous.cookie, previous.visitorData, previous.dataSyncId, previous.authUser, previous.useLoginForBrowse,
            )
            YouTube.cookie = previousCookie
            YouTube.visitorData = previousVisitor
            YouTube.dataSyncId = previousDataSyncId
            YouTube.useLoginForBrowse = previousBrowse
            throw IllegalStateException("Could not activate the YouTube account.", error)
        }
        IosBridgeResponse()
    }

fun clearIosAccount(completion: (String) -> Unit) = iosRequest(completion) {
    YouTube.extractionTransport().innerTube.replaceSession(null, null, null, "0", false)
    YouTube.cookie = null
    YouTube.visitorData = null
    YouTube.dataSyncId = null
    YouTube.useLoginForBrowse = false
    IosBridgeResponse()
}
