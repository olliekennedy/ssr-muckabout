package com.olliekennedy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream

class StationParser {

    fun parse(filename: String): List<Station> =
        getInputStreamFrom(filename)
            .bufferedReader()
            .use { it.readText() }
            .let { Json.decodeFromString<StationDataset>(it) }
            .CORPUS
            .filter { it.`3ALPHA`.isNotBlank() }
            .filter { !it.`3ALPHA`.startsWith("X") }
            .filter { it.NLCDESC.isNotBlank() }
            .filter { it.NLCDESC != "." }
            .map { Station(code = it.`3ALPHA`, name = it.NLCDESC) }

    private fun getInputStreamFrom(filename: String): InputStream = (javaClass.getResourceAsStream(filename)
        ?: error("$filename not found in resources!"))

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