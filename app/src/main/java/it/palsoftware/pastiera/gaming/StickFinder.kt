package it.palsoftware.pastiera.gaming

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A game's on-screen stick found on a picture of its screen: the ring of its base near where a
 * stick was put down, so the stick is centred on it (off centre, a straight push reads as a
 * diagonal). Games draw their controls themselves, so a picture is all there is to go on.
 */
object StickFinder {
    private const val SAMPLES = 72

    /**
     * How a game's screen of [screenW] by [screenH] sits in a picture of [pictureW] by [pictureH]:
     * fitted in the middle, black bars either side (a sideways screen on the upright panel).
     */
    class Fit(val scale: Float, val left: Float, val top: Float, private val screenW: Float, private val screenH: Float) {
        fun toPicture(x: Float, y: Float): Pair<Float, Float> = left + x * screenW * scale to top + y * screenH * scale
        fun toShare(px: Float, py: Float): Pair<Float, Float> =
            ((px - left) / scale / screenW).coerceIn(0f, 1f) to ((py - top) / scale / screenH).coerceIn(0f, 1f)

        companion object {
            fun of(pictureW: Int, pictureH: Int, screenW: Float, screenH: Float): Fit {
                val scale = minOf(pictureW / screenW, pictureH / screenH)
                return Fit(scale, (pictureW - screenW * scale) / 2, (pictureH - screenH * scale) / 2, screenW, screenH)
            }
        }
    }

    /**
     * The ring nearest ([cx], [cy]) about [r] across (all in pixels of the [w] by [h] brightness
     * picture [lum]): its centre and radius, or null when nothing stands out as a ring.
     */
    fun find(lum: IntArray, w: Int, h: Int, cx: Float, cy: Float, r: Float): Triple<Float, Float, Float>? {
        if (r < 6f || lum.size < w * h) return null
        fun at(x: Float, y: Float): Int {
            val xi = x.roundToInt().coerceIn(0, w - 1); val yi = y.roundToInt().coerceIn(0, h - 1)
            return lum[yi * w + xi]
        }
        val cosines = FloatArray(SAMPLES) { cos(2 * PI * it / SAMPLES).toFloat() }
        val sines = FloatArray(SAMPLES) { sin(2 * PI * it / SAMPLES).toFloat() }
        // How sharply, and how evenly all round, the brightness changes across a circle
        fun score(x: Float, y: Float, radius: Float): Float {
            val step = (radius * 0.08f).coerceAtLeast(1.5f)
            var total = 0f; var strong = 0
            for (i in 0 until SAMPLES) {
                val c = cosines[i]; val s = sines[i]
                val outside = at(x + c * (radius + step), y + s * (radius + step))
                val inside = at(x + c * (radius - step), y + s * (radius - step))
                val on = at(x + c * radius, y + s * radius)
                // A filled base's edge (dark one side, light the other), or a thin ring's line
                val edge = maxOf(abs(outside - inside), abs(2 * on - inside - outside) / 2)
                total += edge
                if (edge > 18) strong++
            }
            // A ring is edged most of the way round, unlike a line or a corner crossing it
            return if (strong < SAMPLES * 0.6f) 0f else total / SAMPLES
        }
        var best: Triple<Float, Float, Float>? = null
        var bestScore = 0f
        val move = r * 0.6f
        val centreStep = (r / 10f).coerceAtLeast(1f)
        // About the size put down: the stick's own knob, far smaller, isn't taken for its base
        var radius = r * 0.7f
        while (radius <= r * 1.4f) {
            var y = cy - move
            while (y <= cy + move) {
                var x = cx - move
                while (x <= cx + move) {
                    // A ring nearer where the stick was put down counts for more than one further off
                    val off = kotlin.math.hypot(x - cx, y - cy) / move
                    val value = score(x, y, radius) * (1f - 0.3f * off)
                    if (value > bestScore) { bestScore = value; best = Triple(x, y, radius) }
                    x += centreStep
                }
                y += centreStep
            }
            radius += (r / 12f).coerceAtLeast(1f)
        }
        // Faint everywhere: no ring to go by
        return best.takeIf { bestScore >= 25f }
    }

    /** A picture's brightness (0 to 255) per pixel, from ARGB pixels. */
    fun brightness(pixels: IntArray): IntArray = IntArray(pixels.size) { i ->
        val p = pixels[i]
        (((p shr 16) and 0xFF) * 3 + ((p shr 8) and 0xFF) * 6 + (p and 0xFF)) / 10
    }
}
