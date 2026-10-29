package ru.pirogovlab.tatarkamera

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.File

/** Порог ImageNet-классификатора. */
const val IN_THRESHOLD = 0.6f

/** EfficientNet-Lite0 int8, ImageNet-1k (Apache-2.0, MediaPipe). Маркер отключения при нативном сбое загрузки. */
class Classifier private constructor(private val c: Interpreter, private val names: List<String>) {
    private val inp = TfUtil.direct(224 * 224 * 3); private val px = IntArray(224 * 224)
    private val out = Array(1) { ByteArray(1000) }
    companion object {
        @Volatile var status = "не загружен"
        fun load(ctx: Context): Classifier? {
            val dis = File(ctx.filesDir, "in_disabled"); val mk = File(ctx.filesDir, "in_loading.marker")
            if (dis.exists()) { status = "отключён после сбоя"; return null }
            if (mk.exists()) { dis.writeText("native crash while loading imagenet.tflite"); mk.delete(); status = "отключён после сбоя"; return null }
            return try {
                mk.writeText("1")
                Classifier(Interpreter(ModelFiles.get(ctx, "imagenet.tflite"), Interpreter.Options().setNumThreads(2)), TfUtil.labels(ctx, "imagenet_labels.txt")).also { status = "ok" }
            } catch (t: Throwable) { status = "ошибка: $t"; CrashLog.write(ctx, "Classifier init failed (не фатально)", t); null }
            finally { mk.delete() }
        }
    }
    /** Лучший класс с вероятностью ≥ порога: (метка, вероятность). */
    @Synchronized fun top(bmp: Bitmap): Pair<String, Float>? = runCatching {
        TfUtil.rgbU8(bmp, 224, 224, inp, px); c.run(inp, out)
        var bi = 0; for (k in 1 until 1000) if ((out[0][k].toInt() and 0xFF) > (out[0][bi].toInt() and 0xFF)) bi = k
        val sc = (out[0][bi].toInt() and 0xFF) / 256f  // квантование 1/256
        if (sc >= IN_THRESHOLD) names.getOrNull(bi)?.let { it to sc } else null
    }.getOrNull()
    fun close() = runCatching { c.close() }
}
