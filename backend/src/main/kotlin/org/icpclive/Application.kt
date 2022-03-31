package org.icpclive

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.icpclive.admin.configureAdminRouting
import org.icpclive.adminapi.configureAdminApiRouting
import org.icpclive.cds.launchEventsLoader
import org.icpclive.config.Config
import org.icpclive.data.TickerManager
import org.icpclive.data.WidgetManager
import org.icpclive.overlay.configureOverlayRouting
import org.icpclive.service.EventLoggerService
import org.slf4j.event.Level
import java.io.File
import java.time.Duration

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // application.conf references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {
    install(DefaultHeaders)
    install(CORS) {
<<<<<<< HEAD
        method(HttpMethod.Options)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Post)
        method(HttpMethod.Get)
=======
        header("Access-Control-Expose-Headers: X-Total-Count")
>>>>>>> main
        header(HttpHeaders.ContentType)
        header("*")
        allowSameOrigin = true
        anyHost()
    }
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }
    install(AutoHeadResponse)
    install(IgnoreTrailingSlash)
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            isLenient = true
            allowSpecialFloatingPointValues = true
            allowStructuredMapKeys = true
            prettyPrint = false
            useArrayPolymorphism = false
        })
    }
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        static("/static") { resources("static") }
    }
    configureAdminRouting()
    configureAdminApiRouting()
    configureOverlayRouting()
    environment.log.info("Current working directory is ${File(".").canonicalPath}")
    environment.config.propertyOrNull("live.configDirectory")?.getString()?.run {
        val configPath = File(this).canonicalPath
        environment.log.info("Using config directory $configPath")
        Config.configDirectory = this
    }
    launchEventsLoader()
    launch { EventLoggerService().run() }
    // to trigger init
    TickerManager.let{}
    WidgetManager.let{}
}
