package com.olliekennedy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.http4k.core.*
import org.http4k.core.ContentType.Companion.TEXT_HTML
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.filter.DebuggingFilters.PrintRequest
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
import java.util.*

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

data class Station(
    val code: String,
    val name: String,
)

fun parseStations(): List<Station> {
    val inputStream = object {}.javaClass.getResourceAsStream("/datasets/CORPUSExtract.json")
        ?: error("CORPUSExtract.json not found in resources!")
    val json = inputStream.bufferedReader().use { it.readText() }
    val stationDataset = Json.decodeFromString<StationDataset>(json)
    val justBigStations = stationDataset.CORPUS.filter { it.`3ALPHA`.isNotBlank() }
    return justBigStations.map { Station(code = it.`3ALPHA`, name = it.NLCDESC) }
}

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

val renderer = HandlebarsTemplates().CachingClasspath()
val views = Body.viewModel(renderer, TEXT_HTML).toLens()

val stations: List<Station> = parseStations()

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
                    from = parts[0],
                    to = parts[1],
                    time = parts[2],
                    link = parts[3],
                    showResult = true,
                )
            } else null
        } ?: CalculationPageViewModel("", "", "", "", showResult = false) // Default if no calculation yet

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
                val link = "https://www.nationalrail.co.uk/live-trains/details/?sid=202507218936451&type=departures&targetCrs=ZFD&filterCrs=SAC"

                val resultViewModel = CalculationPageViewModel(from, to, time, link, showResult = true)

                session[LAST_CALC_SESSION_KEY] = "${resultViewModel.from},${resultViewModel.to},${resultViewModel.time},${resultViewModel.link}"

                Response(SEE_OTHER).header("Location", "/")
            }
            false -> Response(SEE_OTHER).header("Location", "/")
        }
    },

    "/static" bind static(Classpath("/public")),
)

fun main() {
    val printingApp: HttpHandler = SessionFilter().then(PrintRequest().then(app))

    val server = printingApp.asServer(Netty(9000)).start()

    println("Server started on " + server.port())
    stations.forEach { println(it) }
}
