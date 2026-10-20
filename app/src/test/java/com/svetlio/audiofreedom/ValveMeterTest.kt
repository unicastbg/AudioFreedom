package com.svetlio.audiofreedom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValveMeterTest {
    @Test fun scaleExtendsDownToMinusThirty() {
        assertEquals(0F, vuPosition(-30F), 0.0001F)
        assertEquals(0F, vuPosition(-60F), 0.0001F)
        assertTrue(vuPosition(-25F) > 0F)
        assertEquals(0.16F, vuPosition(-20F), 0.0001F)
    }

    @Test fun referenceAndOverloadRegionStayUnchanged() {
        assertEquals(0.86F, vuPosition(0F), 0.0001F)
        assertEquals(0.93F, vuPosition(1F), 0.0001F)
        assertEquals(1F, vuPosition(3F), 0.0001F)
        assertEquals(1F, vuPosition(20F), 0.0001F)
    }

    @Test fun needleAdvancesContinuouslyAcrossTheScale() {
        val positions = (-300..30).map { vuPosition(it / 10F) }
        assertTrue(positions.all { it in 0F..1F })
        assertTrue(positions.zipWithNext().all { (lower, higher) -> higher > lower })
    }
}
