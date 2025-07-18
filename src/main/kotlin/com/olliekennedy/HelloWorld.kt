package com.olliekennedy

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
import org.http4k.lens.FormField
import org.http4k.lens.Validator
import org.http4k.lens.int
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
import java.util.UUID

data class Calculations(val firstNumber: Int, val secondNumber: Int) : ViewModel {
    val sum: Int = firstNumber.addedTo(secondNumber)
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
    val firstNumber: Int,
    val secondNumber: Int,
    val sum: Int,
    val showResult: Boolean // Flag to conditionally show the result
) : ViewModel

object InMemorySessionStore {
    private val sessions = mutableMapOf<String, MutableMap<String, Any>>()

    fun get(sessionId: String): MutableMap<String, Any> =
        sessions.getOrPut(sessionId) { mutableMapOf() }
}

val sessionStorage = InMemorySessionStore

val firstFormField = FormField.int().required("firstNumber")
val secondFormField = FormField.int().required("secondNumber")
val calculationForm = Body.webForm(Validator.Strict, firstFormField, secondFormField).toLens()

val renderer = HandlebarsTemplates().CachingClasspath()
val views = Body.viewModel(renderer, TEXT_HTML).toLens()

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
            if (parts.size == 3) {
                CalculationPageViewModel(
                    firstNumber = parts[0].toInt(),
                    secondNumber = parts[1].toInt(),
                    sum = parts[2].toInt(),
                    showResult = true
                )
            } else null
        } ?: CalculationPageViewModel(0, 0, 0, showResult = false) // Default if no calculation yet

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
                val firstNumber = firstFormField(webForm)
                val secondNumber = secondFormField(webForm)
                val sum = firstNumber.addedTo(secondNumber)

                val resultViewModel = CalculationPageViewModel(firstNumber, secondNumber, sum, showResult = true)

                session[LAST_CALC_SESSION_KEY] = "${resultViewModel.firstNumber},${resultViewModel.secondNumber},${resultViewModel.sum}"

                Response(SEE_OTHER).header("Location", "/")
            }
            false -> Response(SEE_OTHER).header("Location", "/")
        }
    },

    "/static" bind static(Classpath("/public")),
)

private fun Int.addedTo(hardcodedSecondNumber: Int) = this + hardcodedSecondNumber

fun main() {
    val printingApp: HttpHandler = SessionFilter().then(PrintRequest().then(app))

    val server = printingApp.asServer(Netty(9000)).start()

    println("Server started on " + server.port())
}
