package io.github.lucaskfp.learnharness.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayMathTest {

    @Test
    fun `dollar-delimited display math becomes a math fence`() {
        val markdown = "Exponents commute:\n\n$$(g^a)^b = g^{ab}$$\n\nSo both agree."

        assertEquals(
            "Exponents commute:\n\n```math\n(g^a)^b = g^{ab}\n```\n\nSo both agree.",
            fenceDisplayMath(markdown),
        )
    }
}
