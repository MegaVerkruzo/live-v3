package org.icpclive.cds.atcoder

import org.icpclive.cds.ContestParseResult
import org.icpclive.cds.FullReloadContestDataSource
import org.icpclive.cds.noop.NoopDataSource
import org.icpclive.util.getCredentials
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.net.CookieStore
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Properties
import kotlin.time.Duration.Companion.seconds

class AtCoderDataSource(properties: Properties, creds: Map<String, String>) : FullReloadContestDataSource(5.seconds) {
    private val url = properties.getProperty("url")
    private val login = properties.getCredentials("atcoder.login", creds)
    private val password = properties.getCredentials("atcoder.password", creds)

    private fun getCSRFToken(): String {
        val inputTags = Jsoup.connect("$url/login")
            .get()
            .getElementsByTag("input")
        for (input in inputTags) {
            if (input.hasAttr("name") && input.attr("name") == "csrf_token") {
                return input.attr("value")
            }
        }
        return ""
    }

    override suspend fun loadOnce(): ContestParseResult {

        println(getCSRFToken())

        return NoopDataSource().loadOnce()
    }
}