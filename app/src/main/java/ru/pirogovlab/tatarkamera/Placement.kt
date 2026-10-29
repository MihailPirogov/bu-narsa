package ru.pirogovlab.tatarkamera

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Объект для расстановки номера (координаты исходного изображения). box = l,t,r,b; outline = отрезки x1,y1,x2,y2… */
class PItem(val box: FloatArray?, val px: Float, val py: Float, val outline: FloatArray?)

/**
 * Один номер на объект. Точка ищется внутри своей рамки/маски, вне чужих рамок и масок,
 * подальше от края маски и не ближе 2r к уже поставленным номерам. Всё в координатах изображения,
 * поэтому экран (fit/zoom) и сохранённая картинка дают одинаковое положение.
 */
object Placement {
    private fun inside(o: FloatArray, x: Float, y: Float): Boolean {
        var c = false; var i = 0
        while (i + 3 < o.size) {
            val x1 = o[i]; val y1 = o[i + 1]; val x2 = o[i + 2]; val y2 = o[i + 3]
            if ((y1 > y) != (y2 > y)) { val xi = x1 + (y - y1) * (x2 - x1) / (y2 - y1); if (xi > x) c = !c }
            i += 4
        }
        return c
    }
    private fun edgeDist(o: FloatArray, x: Float, y: Float): Float {
        var d = Float.MAX_VALUE; var i = 0; val step = maxOf(4, (o.size / 4 / 300) * 4)
        while (i + 3 < o.size) { d = min(d, hypot(o[i] - x, o[i + 1] - y)); i += step }
        return d
    }
    private fun simplify(o: FloatArray?): FloatArray? = o
    private fun contains(it: PItem, x: Float, y: Float): Boolean {
        val b = it.box
        if (b != null) return x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]
        val o = it.outline ?: return false
        return inside(o, x, y)
    }
    private fun area(it: PItem): Float = it.box?.let { (it[2] - it[0]) * (it[3] - it[1]) } ?: Float.MAX_VALUE

    fun place(items: List<PItem>, w: Float, h: Float, r: Float, margin: Float = r): List<Triple<Int, Float, Float>> {
        val out = ArrayList<Triple<Int, Float, Float>>()
        // мелкие рамки первыми — у них меньше выбора
        val order = items.indices.sortedBy { if (items[it].box != null) area(items[it]) else Float.MAX_VALUE }
        val minX = margin; val maxX = w - margin; val minY = margin; val maxY = h - margin
        for (idx in order) {
            val it = items[idx]
            val b = it.box
            val (l, t, rr, bb) = if (b != null) listOf(b[0], b[1], b[2], b[3]) else {
                val o = it.outline
                if (o != null && o.size >= 4) { var a = Float.MAX_VALUE; var c = Float.MAX_VALUE; var d = -Float.MAX_VALUE; var e = -Float.MAX_VALUE
                    var i = 0; while (i + 1 < o.size) { a = min(a, o[i]); c = min(c, o[i + 1]); d = maxOf(d, o[i]); e = maxOf(e, o[i + 1]); i += 2 }
                    listOf(a, c, d, e) } else listOf(it.px - r, it.py - r, it.px + r, it.py + r)
            }
            val n = 11
            var best: Pair<Float, Float>? = null; var bestS = -Float.MAX_VALUE
            val cands = ArrayList<Pair<Float, Float>>(); cands += it.px to it.py
            for (j in 0 until n) for (i in 0 until n) cands += (l + (rr - l) * (i + 0.5f) / n) to (t + (bb - t) * (j + 0.5f) / n)
            for ((x0, y0) in cands) {
                val x = x0.coerceIn(minX, maxOf(minX, maxX)); val y = y0.coerceIn(minY, maxOf(minY, maxY))
                var s = 0f
                if (!contains(it, x, y)) s -= 1000f
                // чужие объекты: мельче (передний план) — сильный штраф, крупнее (фон) — слабый
                items.forEachIndexed { k, o -> if (k != idx && contains(o, x, y)) s -= if (area(o) <= area(it)) 400f else 60f }
                // соседние номера
                for ((_, ox, oy) in out) { val d = hypot(ox - x, oy - y); if (d < 2.3f * r) s -= 300f + (2.3f * r - d) * 10f }
                // удалённость от края своей маски / рамки (не больше 3r), близость к исходной точке
                val ed = if (b != null) min(min(x - b[0], b[2] - x), min(y - b[1], b[3] - y)) else it.outline?.let { o -> edgeDist(o, x, y) } ?: r
                s += min(ed, 3 * r) * 2f
                s -= hypot(x - it.px, y - it.py) / maxOf(1f, maxOf(rr - l, bb - t)) * 40f
                if (s > bestS) { bestS = s; best = x to y }
            }
            val p = best ?: (it.px to it.py)
            out += Triple(idx, p.first, p.second)
        }
        return out.sortedBy { it.first }
    }
}
