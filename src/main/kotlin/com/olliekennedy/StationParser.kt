package com.olliekennedy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import kotlin.collections.map

class StationParser {

    fun parse(filename: String): List<Station> =
        getInputStreamFrom(filename)
            .bufferedReader()
            .use { it.readText() }
            .let { Json.decodeFromString<StationDataset>(it) }
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
