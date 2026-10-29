package ru.pirogovlab.tatarkamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import org.tensorflow.lite.Interpreter
import java.io.File

/** Минимальная доля кадра для области сегментации. */
const val SEG_MIN_AREA = 0.04f

/** ADE20K-класс (первое имя до ';') -> ключ словаря. Только фон и крупные «вещества». */
val SEG_CLASSES = mapOf(
    "sky" to "sky", "tree" to "tree", "grass" to "grass", "water" to "water", "sea" to "sea", "river" to "river",
    "lake" to "lake", "field" to "field", "mountain" to "mountain", "hill" to "hill", "road" to "road", "sand" to "sand",
    "rock" to "rock", "flower" to "flower", "plant" to "plant", "palm" to "palm", "earth" to "earth", "land" to "earth",
    "floor" to "floor", "ceiling" to "ceiling", "wall" to "wall", "windowpane" to "windowpane", "door" to "door",
    "house" to "house", "building" to "building", "path" to "path", "sidewalk" to "sidewalk", "fence" to "fence",
    "bridge" to "bridge", "waterfall" to "waterfall", "rug" to "rug", "curtain" to "curtain", "mirror" to "mirror",
    "painting" to "painting", "lamp" to "lamp", "snow" to "snow", "cloud" to "cloud")

/** outline: отрезки контура (x1,y1,x2,y2,…) в координатах кадра, marching squares. */
data class SegRegion(val key: String, val point: PointF, val area: Float, val outline: FloatArray)

/** DeepLabV3-MobileNetV2 ADE20K (Apache-2.0), 257x257, 150 классов. Загрузка с маркером отключения при нативном сбое. */
class Segmenter private constructor(private val seg: Interpreter, private val names: List<String>) {
    private val S = 513
    private val inp = TfUtil.direct(4 * S * S * 3); private val px = IntArray(S * S)
    private val outB = TfUtil.direct(4 * S * S)  // модель с ARG_MAX: int32 карта классов 513x513 (~1 МБ)
    companion object {
        @Volatile var status = "не загружен"
        fun load(ctx: Context): Segmenter? {
            val dis = File(ctx.filesDir, "seg_disabled"); val mk = File(ctx.filesDir, "seg_loading.marker")
            if (dis.exists()) { status = "отключён после сбоя"; return null }
            if (mk.exists()) { dis.writeText("native crash while loading ade20k.tflite"); mk.delete(); status = "отключён после сбоя"; return null }
            return try {
                mk.writeText("1")
                Segmenter(Interpreter(ModelFiles.get(ctx, "ade20k.tflite"), Interpreter.Options().setNumThreads(2)), TfUtil.labels(ctx, "ade20k_labels.txt")).also { status = "ok" }
            } catch (t: Throwable) { status = "ошибка: $t"; CrashLog.write(ctx, "Segmenter init failed (не фатально)", t); null }
            finally { mk.delete() }
        }
    }
    fun close() = runCatching { seg.close() }

    /** Для каждого класса из SEG_CLASSES — крупнейшая связная область, точка в ней ближе всего к её центру масс. */
    @Synchronized fun regions(bmp: Bitmap): List<SegRegion> {
        TfUtil.rgbF(bmp, S, S, inp, px, 127.5f, 127.5f); outB.rewind(); seg.run(inp, outB)
        outB.rewind(); val ib = outB.asIntBuffer()
        val w = S; val h = S
        val cls = IntArray(w * h) { ib.get(it) }
        val labels = names.map { it.substringBefore(';').trim().lowercase() }
        val seen = BooleanArray(w * h); val out = ArrayList<SegRegion>()
        val best = HashMap<Int, IntArray>()  // class -> pixels of largest component
        val stack = IntArray(w * h)
        for (start in 0 until w * h) {
            if (seen[start]) continue
            val c = cls[start]; var sp = 0; var n = 0; stack[sp++] = start; seen[start] = true
            val comp = ArrayList<Int>()
            while (sp > 0) { val p = stack[--sp]; comp += p; n++
                val x = p % w; val y = p / w
                if (x > 0 && !seen[p - 1] && cls[p - 1] == c) { seen[p - 1] = true; stack[sp++] = p - 1 }
                if (x < w - 1 && !seen[p + 1] && cls[p + 1] == c) { seen[p + 1] = true; stack[sp++] = p + 1 }
                if (y > 0 && !seen[p - w] && cls[p - w] == c) { seen[p - w] = true; stack[sp++] = p - w }
                if (y < h - 1 && !seen[p + w] && cls[p + w] == c) { seen[p + w] = true; stack[sp++] = p + w }
            }
            if ((best[c]?.size ?: 0) < comp.size) best[c] = comp.toIntArray()
        }
        for ((c, px) in best) {
            val key = SEG_CLASSES[labels.getOrNull(c) ?: continue] ?: continue
            val area = px.size.toFloat() / (w * h); if (area < SEG_MIN_AREA) continue
            var sx = 0.0; var sy = 0.0; px.forEach { sx += it % w; sy += it / w }
            val cx = sx / px.size; val cy = sy / px.size
            val near = px.minBy { val dx = it % w - cx; val dy = it / w - cy; dx * dx + dy * dy }
            val inside = BooleanArray(w * h); px.forEach { inside[it] = true }
            out += SegRegion(key, PointF((near % w + 0.5f) * bmp.width / w, (near / w + 0.5f) * bmp.height / h), area,
                outline(inside, w, h, bmp.width.toFloat() / w, bmp.height.toFloat() / h))
        }
        return out.sortedByDescending { it.area }
    }

    /** Marching squares по бинарной маске: контур с диагоналями (сглаженнее лесенки). */
    private fun outline(m: BooleanArray, w: Int, h: Int, kx: Float, ky: Float): FloatArray {
        fun v(x: Int, y: Int) = x in 0 until w && y in 0 until h && m[y * w + x]
        val seg = ArrayList<Float>()
        fun add(ax: Float, ay: Float, bx: Float, by: Float) { seg += (ax + 0.5f) * kx; seg += (ay + 0.5f) * ky; seg += (bx + 0.5f) * kx; seg += (by + 0.5f) * ky }
        for (y in -1 until h) for (x in -1 until w) {
            val c = (if (v(x, y)) 8 else 0) or (if (v(x + 1, y)) 4 else 0) or (if (v(x + 1, y + 1)) 2 else 0) or (if (v(x, y + 1)) 1 else 0)
            val t = x + 0.5f to y.toFloat(); val r = x + 1f to y + 0.5f; val b = x + 0.5f to y + 1f; val l = x.toFloat() to y + 0.5f
            fun e(p: Pair<Float, Float>, q: Pair<Float, Float>) = add(p.first, p.second, q.first, q.second)
            when (c) { 1, 14 -> e(l, b); 2, 13 -> e(b, r); 3, 12 -> e(l, r); 4, 11 -> e(t, r); 6, 9 -> e(t, b); 7, 8 -> e(l, t)
                5 -> { e(l, t); e(b, r) }; 10 -> { e(t, r); e(l, b) } }
        }
        return seg.toFloatArray()
    }
}
