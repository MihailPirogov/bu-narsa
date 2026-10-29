package ru.pirogovlab.tatarkamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import org.tensorflow.lite.Interpreter

/** EfficientDet-Lite0 COCO: вход 1x320x320x3 uint8; выходы boxes[1,25,4] (ymin,xmin,ymax,xmax), classes, scores, count. */
class CocoDetector private constructor(private val tfl: Interpreter, private val names: List<String>) {
    private val S = 320
    private val inp = TfUtil.direct(S * S * 3); private val px = IntArray(S * S)
    private val boxes = Array(1) { Array(25) { FloatArray(4) } }
    private val classes = Array(1) { FloatArray(25) }
    private val scores = Array(1) { FloatArray(25) }
    private val count = FloatArray(1)
    private val iB: Int; private val iC: Int; private val iS: Int; private val iN: Int
    init {
        var b = -1; var n = -1; val two = ArrayList<Pair<Int, String>>()
        for (i in 0 until tfl.outputTensorCount) { val t = tfl.getOutputTensor(i); val sh = t.shape()
            when { sh.size == 3 -> b = i; sh.size <= 1 -> n = i; else -> two += i to t.name() } }
        // По соглашению экспорта: StatefulPartitionedCall:2 = классы, :1 = оценки
        val sorted = two.sortedByDescending { it.second.substringAfterLast(':').toIntOrNull() ?: 0 }
        iB = b; iN = n; iC = sorted[0].first; iS = sorted[1].first
    }
    companion object {
        fun create(ctx: Context) = CocoDetector(Interpreter(ModelFiles.get(ctx, "coco.tflite"), Interpreter.Options().setNumThreads(2)),
            TfUtil.labels(ctx, "coco_labels.txt"))
    }
    data class Det(val box: Rect, val label: String, val score: Float)
    @Synchronized fun detect(bmp: Bitmap): List<Det> {
        TfUtil.rgbU8(bmp, S, S, inp, px)
        tfl.runForMultipleInputsOutputs(arrayOf(inp), mapOf(iB to boxes, iC to classes, iS to scores, iN to count))
        val out = ArrayList<Det>()
        for (k in 0 until minOf(25, count[0].toInt())) {
            val s = scores[0][k]; if (s < COCO_THRESHOLD) continue
            val lab = names.getOrNull(classes[0][k].toInt()) ?: continue
            if (lab.isBlank() || lab == "???") continue
            val bx = boxes[0][k]
            out += Det(Rect((bx[1] * bmp.width).toInt().coerceAtLeast(0), (bx[0] * bmp.height).toInt().coerceAtLeast(0),
                (bx[3] * bmp.width).toInt().coerceAtMost(bmp.width), (bx[2] * bmp.height).toInt().coerceAtMost(bmp.height)), lab, s)
        }
        return out.sortedByDescending { it.score }.take(8)
    }
    fun close() { runCatching { tfl.close() } }
}
