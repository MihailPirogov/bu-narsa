package ru.pirogovlab.tatarkamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Порог детектора Open Images V7 (YOLOv8s, 601 класс). */
const val OID_THRESHOLD = 0.35f
/** Слишком общие классы OID: уступают более конкретным рамкам. */
val OID_GENERIC = setOf("animal","clothing","furniture","land vehicle","mammal","tool","food","vehicle","plant","person",
    "fashion accessory","home appliance","kitchen appliance","kitchenware","kitchen utensil","sports equipment","office supplies",
    "personal care","bathroom accessory","container","invertebrate","marine invertebrates","marine mammal","carnivore","reptile",
    "auto part","weapon","snack","baked goods","dairy product","seafood","fast food","drink","luggage and bags","medical equipment",
    "plumbing fixture","hiking equipment","bowling equipment","musical instrument","human body","footwear","tree","building")

data class OidHit(val box: Rect, val label: String, val score: Float)

/** YOLOv8s-oiv7, вход NCHW 1x3x640x640 float, выход 1x605x8400 (cx,cy,w,h нормированы + 601 класс). */
class OidDetector private constructor(private val tfl: Interpreter, private val names: List<String>) {
    private val S = 640
    private val input = ByteBuffer.allocateDirect(4 * 3 * S * S).order(ByteOrder.nativeOrder())
    private val out = Array(1) { Array(605) { FloatArray(8400) } }
    private val px = IntArray(S * S)

    companion object {
        @Volatile var status = "не загружен"
        fun load(ctx: Context): OidDetector? {
            val dis = java.io.File(ctx.filesDir, "oid_disabled"); val mk = java.io.File(ctx.filesDir, "oid_loading.marker")
            if (dis.exists()) { status = "отключён после сбоя"; return null }
            if (mk.exists()) { dis.writeText("native crash while loading oid.tflite"); mk.delete(); status = "отключён после сбоя"; return null }
            mk.writeText("1")
            return try {
            val names = ctx.assets.open("oid_labels.txt").bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
            OidDetector(Interpreter(ModelFiles.get(ctx, "oid.tflite"), Interpreter.Options().setNumThreads(4)), names).also { status = "ok" }
        } catch (t: Throwable) { status = "ошибка: ${t.message}"; CrashLog.write(ctx, "OID init failed (не фатально)", t); null } finally { mk.delete() }
        }
    }

    fun close() { runCatching { tfl.close() } }

    @Synchronized fun detect(bmp: Bitmap): List<OidHit> {
        val sc = Bitmap.createScaledBitmap(bmp, S, S, true)
        sc.getPixels(px, 0, S, 0, 0, S, S)
        input.rewind()
        val fb = input.asFloatBuffer()
        for (c in 0 until 3) { val sh = 16 - 8 * c; for (p in px) fb.put(((p shr sh) and 0xFF) / 255f) }
        tfl.run(input, out)
        val o = out[0]; val hits = ArrayList<OidHit>()
        for (k in 0 until 8400) {
            var best = 0f; var bi = -1
            for (c in 4 until 605) { val v = o[c][k]; if (v > best) { best = v; bi = c - 4 } }
            if (best < OID_THRESHOLD || bi < 0 || bi >= names.size) continue
            val cx = o[0][k] * bmp.width; val cy = o[1][k] * bmp.height; val w = o[2][k] * bmp.width; val h = o[3][k] * bmp.height
            hits += OidHit(Rect((cx - w / 2).toInt().coerceAtLeast(0), (cy - h / 2).toInt().coerceAtLeast(0),
                (cx + w / 2).toInt().coerceAtMost(bmp.width), (cy + h / 2).toInt().coerceAtMost(bmp.height)), names[bi], best)
        }
        // NMS по классу
        val kept = ArrayList<OidHit>()
        hits.sortedByDescending { it.score }.forEach { h -> if (kept.none { it.label == h.label && iou(it.box, h.box) > 0.5f }) kept += h }
        return kept.take(30)
    }
    private fun iou(a: Rect, b: Rect): Float {
        val i = Rect(); if (!i.setIntersect(a, b)) return 0f
        val ia = i.width().toFloat() * i.height(); return ia / (a.width().toFloat() * a.height() + b.width().toFloat() * b.height() - ia)
    }
}
