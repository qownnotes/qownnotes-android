package org.qownnotes.mobile.markdown

import kotlin.math.abs

/**
 * Discrete note body text sizes in scale-independent pixels.
 *
 * The values are stepped rather than continuous so the control is predictable and so a persisted
 * size can always be mapped back onto a known step. Sizes are expressed in `sp`, so they continue
 * to compose with the system font size instead of replacing it.
 */
object NoteTextSize {
    val steps = listOf(12, 14, 16, 18, 20, 24, 28, 34)

    /** Matches the Material body text size and is close to the previous widget default. */
    const val DEFAULT_SP = 16

    val smallest = steps.first()
    val largest = steps.last()

    /**
     * Snaps an arbitrary stored or restored value onto the nearest supported step. `steps` is
     * ascending and `minBy` keeps the first minimum, so a tie resolves to the smaller step.
     */
    fun coerce(sizeSp: Int): Int = steps.minBy { abs(it - sizeSp) }

    fun canIncrease(sizeSp: Int): Boolean = coerce(sizeSp) < largest

    fun canDecrease(sizeSp: Int): Boolean = coerce(sizeSp) > smallest

    fun increase(sizeSp: Int): Int {
        val current = coerce(sizeSp)
        return steps.firstOrNull { it > current } ?: largest
    }

    fun decrease(sizeSp: Int): Int {
        val current = coerce(sizeSp)
        return steps.lastOrNull { it < current } ?: smallest
    }
}
