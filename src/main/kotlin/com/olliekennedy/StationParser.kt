package com.olliekennedy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class StationParser {

    fun parse(filename: String): List<Station> {
        val corpus = javaClass.getResourceAsStream(filename)
            ?: error("CORPUSExtract.json not found in resources!")
        val json = corpus.bufferedReader().use { it.readText() }
        val stationDataset = Json.decodeFromString<StationDataset>(json)
        val justBigStations = stationDataset.CORPUS.filter { it.`3ALPHA`.isNotBlank() }
        return justBigStations.map { Station(code = it.`3ALPHA`, name = it.NLCDESC) }
    }

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