package com.olliekennedy

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test

class AppIntegrationTest {

    @Test
    fun `renders home page`() {
        val response = app(Request(GET, "/"))
        assertThat(response.status, equalTo(Status.OK))
        assert(response.bodyString().contains("form"))
    }
}