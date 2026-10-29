package ru.pirogovlab.tatarkamera

import android.content.Context
import org.json.JSONObject

data class Example(val src: String, val ru: String)
data class Sense(val d: String, val lab: String, val ex: List<Example>)
/** Толкование из словаря (только значения без помет). */
data class Definition(val stress: String, val senses: List<Sense>, val source: String)

data class Entry(val label: String, val word: String, val ru: String, val ruStress: String, val needsCheck: Boolean, val def: Definition? = null) {
    /** Нет слова на языке словаря — показываем русское с пометкой «нет перевода». */
    val noMhr get() = word.isBlank()
    val title get() = word.ifBlank { ruStress.ifBlank { ru } }
}

/** Общий движок: язык = JSON-ассет (en_label -> {mhr|kv|cv|..., ru, ru_stress, def}). Сменить язык = сменить файл. */
class Dictionary(val langCode: String, val langName: String, private val map: Map<String, Entry>, private val alias: Map<String, String> = emptyMap()) {
    fun lookup(label: String): Entry? = map[label.lowercase()]?.takeIf { it.word.isNotBlank() || it.ru.isNotBlank() }
    /** Класс ImageNet → запись словаря (через imagenet_map.json). */
    fun lookupImageNet(cls: String): Entry? = alias[cls]?.let { lookup(it) }
    companion object {
        private fun parseDef(o: JSONObject?): Definition? = runCatching {
            if (o == null) return null
            val ss = o.getJSONArray("senses")
            val senses = (0 until ss.length()).map { i -> val s = ss.getJSONObject(i); val ex = s.optJSONArray("ex")
                Sense(s.optString("d"), s.optString("lab"), (0 until (ex?.length() ?: 0)).map { j -> ex!!.getJSONObject(j).let { Example(it.optString("tt").ifBlank { it.optString("mhr") }, it.optString("ru")) } }) }
            if (senses.isEmpty()) null else Definition(o.optString("stress"), senses, o.optString("source"))
        }.getOrNull()
        fun load(ctx: Context, asset: String = "dictionary.json"): Dictionary {
            val root = JSONObject(ctx.assets.open(asset).bufferedReader(Charsets.UTF_8).readText())
            val lang = root.getString("lang")
            val e = root.getJSONObject("entries")
            val m = HashMap<String, Entry>()
            for (k in e.keys()) {
                val o = e.getJSONObject(k)
                val w = o.optString(lang)
                m[k] = Entry(o.optString("label", k), w, o.optString("ru"),
                    o.optString("ru_stress", o.optString("ru")), o.optString("status") != "ok", if (w.isBlank()) null else parseDef(o.optJSONObject("def")))
            }
            val al = HashMap<String, String>()
            runCatching { val a = JSONObject(ctx.assets.open("imagenet_map.json").bufferedReader(Charsets.UTF_8).readText()); for (k in a.keys()) al[k] = a.getString(k) }
            return Dictionary(lang, root.optString("name", lang), m, al)
        }
    }
}
