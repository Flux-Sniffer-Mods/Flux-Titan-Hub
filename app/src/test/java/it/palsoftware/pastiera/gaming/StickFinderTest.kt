package it.palsoftware.pastiera.gaming

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickFinderTest {
    /** A dark screen with a light ring of [radius] round ([x], [y]), [thickness] wide. */
    private fun ring(w: Int, h: Int, x: Float, y: Float, radius: Float, thickness: Float = 4f): IntArray =
        IntArray(w * h) { i ->
            val d = hypot(i % w - x, (i / w).toFloat() - y)
            if (abs(d - radius) <= thickness / 2) 220 else 30
        }

    @Test
    fun aStickPutDownOffCentreIsCentredOnTheRing() {
        val lum = ring(400, 300, 150f, 160f, 60f)
        val found = StickFinder.find(lum, 400, 300, 172f, 145f, 55f)
        assertNotNull(found)
        found!!
        assertTrue("x ${found.first}", abs(found.first - 150f) <= 4f)
        assertTrue("y ${found.second}", abs(found.second - 160f) <= 4f)
        assertTrue("radius ${found.third}", abs(found.third - 60f) <= 6f)
    }

    @Test
    fun aPlainScreenOrAStraightEdgeIsNoStick() {
        assertNull(StickFinder.find(IntArray(400 * 300) { 90 }, 400, 300, 150f, 150f, 50f))
        // Half light, half dark: an edge, not a ring
        val split = IntArray(400 * 300) { i -> if (i % 400 < 150) 220 else 30 }
        assertNull(StickFinder.find(split, 400, 300, 150f, 150f, 50f))
    }

    @Test
    fun aSidewaysScreenIsFoundBetweenThePicturesBars() {
        // A 1200x1050 game screen on a 1080x1200 panel: scaled 0.9, bars above and below
        val fit = StickFinder.Fit.of(1080, 1200, 1200f, 1050f)
        assertTrue(abs(fit.scale - 0.9f) < 0.001f)
        val (px, py) = fit.toPicture(0.5f, 0f)
        assertTrue("x $px", abs(px - 540f) < 0.5f)
        assertTrue("y $py", abs(py - 127.5f) < 0.5f)
        val (sx, sy) = fit.toShare(540f, 600f)
        assertTrue(abs(sx - 0.5f) < 0.001f && abs(sy - 0.5f) < 0.001f)
    }

    @Test
    fun theKnobInsideTheBaseIsNotTakenForIt() {
        // A base ring of 60 with a knob ring of 22 inside it: the base is found
        val base = ring(400, 300, 150f, 160f, 60f)
        val knob = ring(400, 300, 150f, 160f, 22f)
        val lum = IntArray(base.size) { maxOf(base[it], knob[it]) }
        val found = StickFinder.find(lum, 400, 300, 160f, 150f, 55f)!!
        assertTrue("radius ${found.third}", abs(found.third - 60f) <= 6f)
    }
}
