package org.qownnotes.mobile

/**
 * Presentation choices for one home-screen widget.
 *
 * A `null` color means "follow the system": the widget keeps its light and dark resource colors.
 * Custom colors are fixed and do not change with the system theme.
 */
data class WidgetAppearance(
    val background: Int? = null,
    val backgroundOpacityPercent: Int = AppearanceColors.MAX_OPACITY_PERCENT,
    /** Header band behind the title; `null` draws no separate header band. */
    val header: Int? = null,
    /** Note-list rows; ignored by the single-note widget. */
    val row: Int? = null,
    val frame: Boolean = false,
    val frameColor: Int? = null
) {
    companion object {
        val DEFAULT = WidgetAppearance()
    }
}
