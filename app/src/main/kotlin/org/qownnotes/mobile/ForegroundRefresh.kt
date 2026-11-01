package org.qownnotes.mobile

/** Failed attempts also cool down; opening an offline app must not cause a retry storm. */
internal fun foregroundRefreshDue(lastAttemptMillis: Long?, nowMillis: Long): Boolean =
    lastAttemptMillis == null || nowMillis < lastAttemptMillis ||
        nowMillis - lastAttemptMillis >= 5 * 60 * 1_000L
