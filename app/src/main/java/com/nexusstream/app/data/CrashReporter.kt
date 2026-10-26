package com.nexusstream.app.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the stack trace of the last crash on the device so it can be shared from Settings
 * (there is no analytics or crash-reporting service in this app).
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"
                File(app.filesDir, FILE).writeText(
                    format(version, "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})", thread.name, error, System.currentTimeMillis())
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun lastReport(context: Context): String? =
        File(context.applicationContext.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.applicationContext.filesDir, FILE).delete()
    }

    /** The text of a crash report. Pure, for tests. */
    internal fun format(version: String, device: String, thread: String, error: Throwable, timeMs: Long): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timeMs))
        return "NexusStream crash report\nTime: $time\nVersion: $version\nDevice: $device\nThread: $thread\n\n$trace"
    }
}
