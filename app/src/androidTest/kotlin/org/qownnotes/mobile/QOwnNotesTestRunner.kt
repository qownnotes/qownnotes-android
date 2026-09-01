package org.qownnotes.mobile

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class QOwnNotesTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader,
        className: String,
        context: Context
    ): Application =
        super.newApplication(classLoader, TestQOwnNotesApplication::class.java.name, context)
}
