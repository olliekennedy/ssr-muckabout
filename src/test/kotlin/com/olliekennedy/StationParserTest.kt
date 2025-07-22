package com.olliekennedy

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasElement
import com.natpryce.hamkrest.hasSize
import org.junit.jupiter.api.Test

class StationParserTest {

    val underTest = StationParser()

    @Test
    fun `gets data into a shape we can work with`() {
        val corpusFileName = "/datasets/TestCorpus.json"

        val result = underTest.parse(corpusFileName)

        assertThat(result, hasSize(equalTo(3)))
        assertThat(result, hasElement(Station(code = "FMA", name = "FENTON MANOR")))
        assertThat(result.all { it.code.isNotBlank() }, equalTo(true))
    }
}