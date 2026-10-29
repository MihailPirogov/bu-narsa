package ru.pirogovlab.tatarkamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/** Порог уверенности метки ML Kit для кропа объекта. */
const val CROP_THRESHOLD = 0.8f
/** Порог COCO-детектора (EfficientDet-Lite0). */
const val COCO_THRESHOLD = 0.55f
/** Минимальная доля площади кадра для объекта. */
const val MIN_AREA = 0.03f
/** Отступ вокруг кропа, доля размера рамки. */
const val CROP_PAD = 0.08f

/** box = рамка объекта; point = центр метки (для частей лица — сама точка). */
data class Found(val box: Rect?, val point: PointF, val entry: Entry, val enLabel: String, val conf: Float,
                 val points: List<PointF> = listOf(point), val outline: FloatArray? = null)

/** Максимум номеров на кадре. */
const val MAX_ITEMS = 8
/** Порог для фоновых меток всего кадра. */
const val BG_THRESHOLD = 0.85f
val BG_LABELS = setOf("sky","sunset","rainbow","forest","field","garden","beach","mountain","lake","river","road","waterfall","sand","fog","desert","prairie","swamp","ice","glacier","flora","jungle","canyon","cliff","lightning","storm","aurora","asphalt","soil","park")
private val SKY = setOf("sky","sunset","rainbow","fog","lightning","storm","aurora")
private val GREENS = setOf("forest","field","garden","flora","jungle","prairie","park","swamp")
private val WATER = setOf("lake","river","waterfall","beach","glacier","ice")
/** Минимальная доля кадра для лица, чтобы подписывать его части. */
const val FACE_MIN_AREA = 0.08f

class Recognizer(ctx: Context, private val dict: Dictionary, stream: Boolean) {
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(CROP_THRESHOLD).build())
    private val mlDet = ObjectDetection.getClient(ObjectDetectorOptions.Builder()
        .setDetectorMode(if (stream) ObjectDetectorOptions.STREAM_MODE else ObjectDetectorOptions.SINGLE_IMAGE_MODE)
        .enableMultipleObjects().build())
    private val faces = runCatching { FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(if (stream) FaceDetectorOptions.PERFORMANCE_MODE_FAST else FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).setMinFaceSize(0.15f).build()) }.getOrNull()
    private val coco: CocoDetector? = loadCoco(ctx)
    private val oid: OidDetector? = OidDetector.load(ctx)
    private val segm: Segmenter? = Segmenter.load(ctx)
    private val inet: Classifier? = Classifier.load(ctx)
    private var inCache: Pair<String, Float>? = null
    private val segEvery = if (stream) 3 else 1
    private var frameNo = 0
    private var segCache: List<SegRegion> = emptyList()

    companion object {
        /** Маркер «идёт загрузка COCO»: если процесс упал нативно во время загрузки, при следующем запуске COCO отключается. */
        fun cocoMarker(ctx: Context) = java.io.File(ctx.filesDir, "coco_loading.marker")
        fun cocoDisabled(ctx: Context) = java.io.File(ctx.filesDir, "coco_disabled").exists() || cocoMarker(ctx).exists().also {
            if (it) java.io.File(ctx.filesDir, "coco_disabled").writeText("native crash while loading coco.tflite")
        }
        @Volatile var cocoStatus: String = "не загружен"
        private fun loadCoco(ctx: Context): CocoDetector? {
            if (cocoDisabled(ctx)) { cocoStatus = "отключён после сбоя"; return null }
            val m = cocoMarker(ctx)
            return try {
                m.writeText("1")
                CocoDetector.create(ctx).also { cocoStatus = "ok" }
            } catch (t: Throwable) {
                cocoStatus = "ошибка: ${t.javaClass.simpleName}: ${t.message}"
                CrashLog.write(ctx, "COCO init failed (не фатально)", t); null
            } finally { m.delete() }
        }
    }

    fun close() { runCatching { labeler.close() }; runCatching { mlDet.close() }; runCatching { faces?.close() }; runCatching { coco?.close() }; oid?.close(); segm?.close(); inet?.close() }

    private fun area(r: Rect) = r.width().toFloat() * r.height()
    private fun iou(a: Rect, b: Rect): Float {
        val i = Rect(); if (!i.setIntersect(a, b)) return 0f
        val ia = area(i); return ia / (area(a) + area(b) - ia)
    }
    private fun center(r: Rect) = PointF(r.exactCenterX(), r.exactCenterY())

    private fun crop(bmp: Bitmap, b: Rect): Bitmap? {
        val px = (b.width() * CROP_PAD).toInt(); val py = (b.height() * CROP_PAD).toInt()
        val r = Rect(b.left - px, b.top - py, b.right + px, b.bottom + py)
        if (!r.intersect(0, 0, bmp.width, bmp.height)) return null
        return if (r.width() > 32 && r.height() > 32) Bitmap.createBitmap(bmp, r.left, r.top, r.width(), r.height()) else null
    }

    fun detect(bmp: Bitmap): List<Found> {
        val minA = MIN_AREA * bmp.width * bmp.height
        val objs = ArrayList<Found>()
        // 1) COCO-80: люди, животные, транспорт, бытовые предметы
        runCatching {
            coco?.detect(bmp)?.forEach { d ->
                val r = d.box
                val e = dict.lookup(d.label) ?: return@forEach
                if (area(r) >= minA) objs += Found(r, center(r), e, d.label, d.score)
            }
        }
        // 1б) Open Images V7 (601 класс): общие классы — только если нет более конкретной рамки
        runCatching {
            val hs = oid?.detect(bmp) ?: return@runCatching
            val spec = hs.filter { it.label.lowercase() !in OID_GENERIC }
            hs.forEach { h ->
                if (area(h.box) < minA) return@forEach
                val e = dict.lookup(h.label) ?: return@forEach
                val generic = h.label.lowercase() in OID_GENERIC
                if (generic && (spec.any { iou(it.box, h.box) > 0.3f } || objs.any { iou(it.box!!, h.box) > 0.3f })) return@forEach
                objs += Found(h.box, center(h.box), e, "oid:" + h.label, if (generic) h.score * 0.6f else h.score)
            }
        }
        // 2) ML Kit объект + ImageNet-подобный labeler (400+ меток) на кропе, порог 0.8
        runCatching {
            Tasks.await(mlDet.process(InputImage.fromBitmap(bmp, 0))).forEach { o ->
                val r = o.boundingBox
                if (area(r) < minA || objs.any { iou(it.box!!, r) > 0.4f }) return@forEach
                val c = crop(bmp, r) ?: return@forEach
                val l = Tasks.await(labeler.process(InputImage.fromBitmap(c, 0)))
                    .filter { it.confidence >= CROP_THRESHOLD && dict.lookup(it.text) != null }
                    .maxByOrNull { it.confidence }
                if (l != null) { objs += Found(r, center(r), dict.lookup(l.text)!!, l.text, l.confidence); return@forEach }
                // ImageNet-классификатор на кропе, если ML Kit не узнал
                val t = inet?.top(c) ?: return@forEach
                val e = dict.lookupImageNet(t.first) ?: return@forEach
                objs += Found(r, center(r), e, "in:" + t.first, t.second)
            }
        }
        objs.sortByDescending { area(it.box!!) }
        // 3) лицо и его части
        val parts = ArrayList<Found>()
        runCatching {
            Tasks.await(faces!!.process(InputImage.fromBitmap(bmp, 0)))
                .maxByOrNull { area(it.boundingBox) }?.takeIf { area(it.boundingBox) >= FACE_MIN_AREA * bmp.width * bmp.height }
                ?.let { f ->
                dict.lookup("face")?.let { parts += Found(f.boundingBox, PointF(f.boundingBox.exactCenterX(), f.boundingBox.top + f.boundingBox.height() * 0.2f), it, "face", 1f) }
                fun lm(t: Int) = f.getLandmark(t)?.position
                fun add(key: String, p: PointF?) { val e = dict.lookup(key); if (p != null && e != null) parts += Found(null, p, e, key, 1f) }
                add("eye", lm(FaceLandmark.LEFT_EYE) ?: lm(FaceLandmark.RIGHT_EYE))
                add("nose", lm(FaceLandmark.NOSE_BASE))
                val ml = lm(FaceLandmark.MOUTH_LEFT); val mr = lm(FaceLandmark.MOUTH_RIGHT); val mb = lm(FaceLandmark.MOUTH_BOTTOM)
                add("mouth", if (ml != null && mr != null) PointF((ml.x + mr.x) / 2, ((ml.y + mr.y) / 2 + (mb?.y ?: ml.y)) / 2) else mb)
                add("cheek", lm(FaceLandmark.RIGHT_CHEEK) ?: lm(FaceLandmark.LEFT_CHEEK))
                add("ear", lm(FaceLandmark.RIGHT_EAR) ?: lm(FaceLandmark.LEFT_EAR))
            }
        }
        // человек, на котором подписано лицо, не дублируется
        val face = parts.firstOrNull { it.box != null }?.box
        if (face != null) objs.removeAll { (it.enLabel.equals("person", true) || it.enLabel.equals("oid:Person", true) || it.enLabel.equals("oid:Human head", true) || it.enLabel.equals("oid:Human face", true)) && Rect(it.box).contains(face.centerX(), face.centerY()) }
        val partT = parts.map { it.entry.title }.toSet()
        objs.removeAll { it.entry.title in partT }
        // NMS между распознавателями: из пересекающихся рамок остаётся самая уверенная
        val kept = ArrayList<Found>()
        objs.sortedByDescending { it.conf }.forEach { o -> if (kept.none { iou(it.box!!, o.box!!) > 0.45f }) kept += o }
        // одно слово = один номер; кружки на каждом экземпляре
        val groups = kept.groupBy { it.entry.title }.map { (_, g) ->
            val best = g.maxBy { area(it.box!!) }
            best.copy(conf = g.maxOf { it.conf }, points = g.sortedByDescending { area(it.box!!) }.map { it.point })
        }.sortedByDescending { it.conf * 0.5f + area(it.box!!) / (bmp.width.toFloat() * bmp.height) }
        val partsU = parts.distinctBy { it.entry.title }
        val room = (MAX_ITEMS - partsU.size).coerceAtLeast(MAX_ITEMS / 2)
        val main = (groups.take(room) + partsU).take(MAX_ITEMS).toMutableList()
        // сегментация ADE20K: дерево, трава, вода, небо, дорога…
        if (segm != null) runCatching {
            if (frameNo++ % segEvery == 0) segCache = segm!!.regions(bmp)
            segCache.forEach { rg ->
                if (main.size >= MAX_ITEMS) return@forEach
                val e = dict.lookup(rg.key) ?: return@forEach
                if (main.any { it.entry.title == e.title }) return@forEach
                main += Found(null, rg.point, e, rg.key, rg.area.coerceAtMost(1f), outline = rg.outline)
            }
        }
        // ImageNet по всему кадру (без рамки), порог IN_THRESHOLD
        if (inet != null && main.size < MAX_ITEMS) runCatching {
            if ((frameNo - 1) % segEvery == 0 || segm == null) inCache = inet!!.top(bmp)
            val t = inCache ?: return@runCatching
            val e = dict.lookupImageNet(t.first) ?: return@runCatching
            if (main.none { it.entry.title == e.title }) {
                val pt = bgPoint("", colorGrid(bmp), bmp.width, bmp.height, main.flatMap { it.points })
                main += Found(null, pt, e, "in:" + t.first, t.second)
            }
        }
        // фоновые метки всего кадра (без рамки): только белый список, высокий порог
        if (main.size < MAX_ITEMS) runCatching {
            val grid = colorGrid(bmp)
            Tasks.await(labeler.process(InputImage.fromBitmap(bmp, 0)))
                .filter { it.confidence >= BG_THRESHOLD && it.text.lowercase() in BG_LABELS }
                .sortedByDescending { it.confidence }.forEach { l ->
                    if (main.size >= MAX_ITEMS) return@forEach
                    val e = dict.lookup(l.text) ?: return@forEach
                    if (main.any { it.entry.title == e.title }) return@forEach
                    val pt = bgPoint(l.text.lowercase(), grid, bmp.width, bmp.height, main.flatMap { it.points })
                    main += Found(null, pt, e, l.text, l.confidence)
                }
        }
        return main
    }

    /** Средний цвет ячеек сетки 4x4: [row][col] = (r,g,b). */
    private fun colorGrid(bmp: Bitmap): Array<Array<FloatArray>> {
        val small = Bitmap.createScaledBitmap(bmp, 32, 32, true)
        return Array(4) { r -> Array(4) { c ->
            var R = 0f; var G = 0f; var B = 0f
            for (y in r * 8 until r * 8 + 8) for (x in c * 8 until c * 8 + 8) { val p = small.getPixel(x, y)
                R += (p shr 16 and 255); G += (p shr 8 and 255); B += (p and 255) }
            floatArrayOf(R / 64, G / 64, B / 64) } }
    }

    private fun bgPoint(label: String, g: Array<Array<FloatArray>>, w: Int, h: Int, taken: List<PointF>): PointF {
        fun cellCenter(r: Int, c: Int) = PointF((c + 0.5f) * w / 4, (r + 0.5f) * h / 4)
        fun free(r: Int, c: Int): Float { val p = cellCenter(r, c); return taken.minOfOrNull { Math.hypot((it.x - p.x).toDouble(), (it.y - p.y).toDouble()).toFloat() } ?: 1e6f }
        val cells = (0 until 4).flatMap { r -> (0 until 4).map { c -> r to c } }
        val score: (Int, Int) -> Float = { r, c -> val (R, G, B) = g[r][c].let { Triple(it[0], it[1], it[2]) }
            when (label) {
                in SKY -> (R + G + B) / 3 + (B - R) - r * 40f
                in GREENS -> (G - (R + B) / 2) + r * 10f
                in WATER -> (B - R) + r * 15f
                else -> 0f
            } }
        val best = if (label in SKY || label in GREENS || label in WATER)
            cells.sortedByDescending { (r, c) -> score(r, c) }.take(4).maxBy { (r, c) -> free(r, c) }
        else cells.maxBy { (r, c) -> free(r, c) }
        return cellCenter(best.first, best.second)
    }
}
