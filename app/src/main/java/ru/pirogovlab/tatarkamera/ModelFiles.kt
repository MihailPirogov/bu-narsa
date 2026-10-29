package ru.pirogovlab.tatarkamera

import android.content.Context
import java.io.File

/** Модели лежат в APK сжатыми (экономия ~2,8 МБ); при первом запуске версии распаковываются в filesDir для mmap. */
object ModelFiles {
    fun get(ctx: Context, asset: String): File {
        val ver = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime }.getOrDefault(0L)
        val dir = File(ctx.filesDir, "models").apply { mkdirs() }
        val f = File(dir, asset); val stamp = File(dir, "$asset.v")
        if (!f.exists() || stamp.takeIf { it.exists() }?.readText() != ver.toString()) {
            val tmp = File(dir, "$asset.tmp")
            ctx.assets.open("$asset.z").use { i -> tmp.outputStream().use { i.copyTo(it) } }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            stamp.writeText(ver.toString())
        }
        return f
    }
}
