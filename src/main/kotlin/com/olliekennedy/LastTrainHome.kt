package com.olliekennedy

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.Options
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.http4k.core.Body
import org.http4k.core.ContentType.Companion.TEXT_HTML
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.DebuggingFilters.PrintRequest
import org.http4k.format.KotlinxSerialization
import org.http4k.lens.FormField
import org.http4k.lens.Validator
import org.http4k.lens.webForm
import org.http4k.routing.ResourceLoader.Companion.Classpath
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.template.viewModel
import java.io.InputStream
import java.util.UUID

@Serializable
data class Station(
    val code: String,
    val name: String,
)

fun SessionFilter(): Filter = Filter { next ->
    { request ->
        val sessionCookie = request.cookie("SESSION_ID")
        val sessionId = sessionCookie?.value ?: UUID.randomUUID().toString()
        val response = next(request)
        if (sessionCookie == null) {
            response.cookie(Cookie("SESSION_ID", sessionId))
        } else {
            response
        }
    }
}

data class CalculationPageViewModel(
    val stations: List<Station>,
    val from: String,
    val to: String,
    val time: String,
    val link: String,
    val showResult: Boolean, // Flag to conditionally show the result
) : ViewModel

object InMemorySessionStore {
    private val sessions = mutableMapOf<String, MutableMap<String, Any>>()

    fun get(sessionId: String): MutableMap<String, Any> =
        sessions.getOrPut(sessionId) { mutableMapOf() }
}

val sessionStorage = InMemorySessionStore

val firstFormField = FormField.required("from")
val secondFormField = FormField.required("to")
val calculationForm = Body.webForm(Validator.Strict, firstFormField, secondFormField).toLens()

val customHandlebars = Handlebars().apply {
    registerHelper("ifEquals", Helper<Any?> { a: Any?, options: Options ->
        val b: Any? = options.param(0)
        if (a == b) options.fn(null) else options.inverse(null)
    })
}
val renderer = HandlebarsTemplates(configure = { customHandlebars }).CachingClasspath()
val views = Body.viewModel(renderer, TEXT_HTML).toLens()

private val json = KotlinxSerialization
val lens = json.autoBody<List<Station>>().toLens()

const val CORPUS_FILENAME = "/datasets/CORPUSExtract.json"
val stations: List<Station> = StationParser().parse(CORPUS_FILENAME).sortedBy { it.name.lowercase() }

const val LAST_CALC_SESSION_KEY = "lastCalculation"

val app: HttpHandler = routes(
    "/" bind GET to { request ->
        val sessionCookie = request.cookie("SESSION_ID")
        val sessionId = sessionCookie?.value ?: UUID.randomUUID().toString()
        val session = sessionStorage.get(sessionId)

        val lastCalculationViewModel = session[LAST_CALC_SESSION_KEY]?.let {
            // Deserialize the string back to CalculationPageViewModel
            // In a real app, you might use a JSON serializer for more complex objects
            val parts = (it as String).split(",")
            if (parts.size == 4) {
                CalculationPageViewModel(
                    stations = stations,
                    from = parts[0],
                    to = parts[1],
                    time = parts[2],
                    link = parts[3],
                    showResult = true,
                )
            } else null
        } ?: CalculationPageViewModel(stations, "", "", "", "", showResult = false) // Default if no calculation yet

        session.remove(LAST_CALC_SESSION_KEY)

        Response(OK).with(views of lastCalculationViewModel)
    },
    "/calculate" bind POST to { request ->
        val webForm = calculationForm(request)
        val sessionCookie = request.cookie("SESSION_ID")
        val sessionId = sessionCookie?.value ?: UUID.randomUUID().toString()
        val session = sessionStorage.get(sessionId)

        when (webForm.errors.isEmpty()) {
            true -> {
                val from = firstFormField(webForm)
                val to = secondFormField(webForm)
                val time = "23:44"
                val link =
                    "https://www.nationalrail.co.uk/live-trains/details/?sid=202507218936451&type=departures&targetCrs=ZFD&filterCrs=SAC"

                val resultViewModel = CalculationPageViewModel(stations, from, to, time, link, showResult = true)

                session[LAST_CALC_SESSION_KEY] =
                    "${resultViewModel.from},${resultViewModel.to},${resultViewModel.time},${resultViewModel.link}"

                Response(SEE_OTHER).header("Location", "/")
            }

            false -> Response(SEE_OTHER).header("Location", "/")
        }
    },

    "/stations" bind GET to { request ->
        Response(OK)
            .with(lens of stations)
            .header("Cache-Control", "public, max-age=3600")
            .header("Content-Type", "application/json")
    },

    "/static" bind static(Classpath("/public")),
)

class StationParser {

    fun parse(filename: String): List<Station> =
        getInputStreamFrom(filename)
            .bufferedReader()
            .use { it.readText() }
            .let { Json.Default.decodeFromString<StationDataset>(it) }
            .CORPUS
            .filterOutUndesirables()
            .map { Station(code = it.`3ALPHA`, name = it.NLCDESC) }

    private fun getInputStreamFrom(filename: String): InputStream =
        (javaClass.getResourceAsStream(filename)
            ?: error("$filename not found in resources!"))

    private fun List<StationData>.filterOutUndesirables(): List<StationData> =
        this
            .filter { it.`3ALPHA`.isNotBlank() }
            .filter { !it.`3ALPHA`.startsWith("X") }
            .filter { it.NLCDESC.isNotBlank() }
            .filter { it.NLCDESC != "." }
            .filter { !it.NLCDESC.contains("DEPOT") }
            .filter { !it.NLCDESC.contains("STORE") }
            .filter { !it.NLCDESC.contains("SIDING") }
            .filter { !it.NLCDESC.contains("YARD") }
            .filter { !it.NLCDESC.contains("WORKS") }
            .filter { !it.NLCDESC.contains("MAINT") }
            .filter { !it.NLCDESC.contains("LOCOMOTIVE") }
            .filter { !it.NLCDESC.contains("REPAIR") }
            .filter { !it.NLCDESC.contains("BUS S") }
            .filter { !it.NLCDESC.contains("SLIPWAY") }
            .filter { !it.NLCDESC.contains("Metrolink") }
            .filter { !it.NLCDESC.contains("MTLK") }

    companion object {
        @Suppress("PropertyName")
        @Serializable
        data class StationDataset(
            val CORPUS: List<StationData>,
        )

        @Suppress("PropertyName")
        @Serializable
        data class StationData(
            val NLC: Int,
            val STANOX: String,
            val TIPLOC: String,
            val `3ALPHA`: String,
            val UIC: String,
            val NLCDESC: String,
            val NLCDESC16: String,
        )
    }
}

fun main() {
    val printingApp: HttpHandler = SessionFilter().then(PrintRequest().then(app))

    val server = printingApp.asServer(Netty(9000)).start()

    println("Server started on " + server.port())
    stations.forEach { println(it) }
}
