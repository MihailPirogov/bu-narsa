package ru.pirogovlab.tatarkamera

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")
    fun write(ctx: Context, title: String, t: Throwable) = runCatching {
        val sw = StringWriter(); t.printStackTrace(PrintWriter(sw))
        file(ctx).writeText("Бу нәрсә? ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "$title\n\n$sw")
    }
    fun install(ctx: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, e -> write(ctx.applicationContext, "Сбой в потоке ${th.name}", e); prev?.uncaughtException(th, e) }
    }
    fun take(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.let { val s = it.readText(); it.delete(); s }
}
