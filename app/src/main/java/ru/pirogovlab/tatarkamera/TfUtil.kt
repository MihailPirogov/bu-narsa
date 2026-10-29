package ru.pirogovlab.tatarkamera

import android.content.Context
import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Замена TFLite Task Library (её libtask_vision_jni.so не выровнена под 16 КБ). */
object TfUtil {
    fun labels(ctx: Context, asset: String): List<String> =
        ctx.assets.open(asset).bufferedReader().readLines().map { it.trim() }

    /** RGB uint8 NHWC. */
    fun rgbU8(bmp: Bitmap, w: Int, h: Int, buf: ByteBuffer, px: IntArray) {
        val sc = Bitmap.createScaledBitmap(bmp, w, h, true)
        sc.getPixels(px, 0, w, 0, 0, w, h); buf.rewind()
        for (p in px) { buf.put((p shr 16 and 0xFF).toByte()); buf.put((p shr 8 and 0xFF).toByte()); buf.put((p and 0xFF).toByte()) }
        buf.rewind()
    }
    /** RGB float NHWC, (v-mean)/std. */
    fun rgbF(bmp: Bitmap, w: Int, h: Int, buf: ByteBuffer, px: IntArray, mean: Float, std: Float) {
        val sc = Bitmap.createScaledBitmap(bmp, w, h, true)
        sc.getPixels(px, 0, w, 0, 0, w, h); buf.rewind(); val fb = buf.asFloatBuffer()
        for (p in px) { fb.put(((p shr 16 and 0xFF) - mean) / std); fb.put(((p shr 8 and 0xFF) - mean) / std); fb.put(((p and 0xFF) - mean) / std) }
        buf.rewind()
    }
    fun direct(n: Int): ByteBuffer = ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder())
}
