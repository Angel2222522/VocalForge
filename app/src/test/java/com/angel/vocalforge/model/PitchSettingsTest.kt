package com.angel.vocalforge.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchSettingsTest {
    @Test
    fun chromaticAllowsAllPitchClasses() { assertEquals(0xFFF, PitchSettings(scaleKind = ScaleKind.CHROMATIC).mask()) }

    @Test
    fun majorScaleUsesRootRelativeIntervals() {
        val cMajor = PitchSettings(rootMidi = 60, scaleKind = ScaleKind.MAJOR).mask()
        assertTrue(cMajor and (1 shl 0) != 0)
        assertTrue(cMajor and (1 shl 1) == 0)
        assertTrue(cMajor and (1 shl 4) != 0)
    }
}
