package com.olliekennedy

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.contains
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.junit.jupiter.api.Test

class HelloWorldTest {

    @Test
    fun `shows the title`() {
        assertThat(app.invoke(Request(GET, "/")).bodyString(), contains(Regex("<h1>Simple SSR Calculator</h1>")))
    }
}
