package com.fontschecker.app

import java.nio.ByteBuffer
import java.util.BitSet
import java.util.zip.Inflater

class UnsupportedFontException(message: String) : Exception(message)

/** Результат разбора шрифта. */
class FontData(
    /** Данные в формате sfnt (TTF/OTF/TTC), пригодные для Android Typeface. */
    val sfnt: ByteArray,
    val format: String,
    val fullName: String?,
    /** Множество кодовых точек Unicode, для которых в шрифте есть глиф. */
    val codepoints: BitSet,
)

/**
 * Минимальный разборщик sfnt: достаёт таблицу cmap (какие символы есть в шрифте)
 * и имя шрифта. Умеет открывать TTF, OTF, TTC и конвертировать WOFF в sfnt.
 */
object FontParser {

    fun load(raw: ByteArray): FontData {
        if (raw.size < 12) throw UnsupportedFontException("Файл слишком маленький для шрифта")
        val magic = u32(raw, 0)
        val sfnt: ByteArray
        val format: String
        when (magic) {
            0x00010000L, 0x74727565L -> { sfnt = raw; format = "TrueType (TTF)" }
            0x4F54544FL -> { sfnt = raw; format = "OpenType (OTF)" }
            0x74746366L -> { sfnt = raw; format = "TrueType Collection (TTC)" }
            0x774F4646L -> { sfnt = woffToSfnt(raw); format = "WOFF" }
            0x774F4632L -> throw UnsupportedFontException(
                "Формат WOFF2 (сжат Brotli) пока не поддерживается. Используйте TTF, OTF, TTC или WOFF."
            )
            else -> throw UnsupportedFontException(
                "Не удалось распознать формат. Поддерживаются TTF, OTF, TTC и WOFF."
            )
        }

        var name: String? = null
        var cps = BitSet()
        try {
            val base = if (u32(sfnt, 0) == 0x74746366L) u32(sfnt, 12).toInt() else 0
            val tables = readTableDirectory(sfnt, base)
            tables[TAG_CMAP]?.let { cps = readCmap(sfnt, it) }
            tables[TAG_NAME]?.let { name = readName(sfnt, it) }
        } catch (e: Exception) {
            // Повреждённая таблица: показываем шрифт без списка языков.
        }
        return FontData(sfnt, format, name, cps)
    }

    private const val TAG_CMAP = 0x636D6170L // 'cmap'
    private const val TAG_NAME = 0x6E616D65L // 'name'

    private fun readTableDirectory(d: ByteArray, base: Int): Map<Long, Int> {
        val n = u16(d, base + 4)
        val result = HashMap<Long, Int>()
        for (i in 0 until n) {
            val e = base + 12 + i * 16
            result[u32(d, e)] = u32(d, e + 8).toInt()
        }
        return result
    }

    // ---------- cmap ----------

    private fun readCmap(d: ByteArray, tableOffset: Int): BitSet {
        val result = BitSet()
        val count = u16(d, tableOffset + 2)
        for (i in 0 until count) {
            val rec = tableOffset + 4 + i * 8
            val platform = u16(d, rec)
            val encoding = u16(d, rec + 2)
            val unicode = platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10))
            if (!unicode) continue
            val sub = tableOffset + u32(d, rec + 4).toInt()
            try {
                when (u16(d, sub)) {
                    4 -> readFormat4(d, sub, result)
                    6 -> readFormat6(d, sub, result)
                    12 -> readFormat12(d, sub, result)
                }
            } catch (e: Exception) {
                // пропускаем битую подтаблицу
            }
        }
        return result
    }

    private fun readFormat4(d: ByteArray, o: Int, out: BitSet) {
        val segX2 = u16(d, o + 6)
        val seg = segX2 / 2
        val endCodes = o + 14
        val startCodes = o + 16 + segX2
        val idDeltas = o + 16 + 2 * segX2
        val idRangeOffsets = o + 16 + 3 * segX2
        for (s in 0 until seg) {
            val end = u16(d, endCodes + s * 2)
            val start = u16(d, startCodes + s * 2)
            val delta = u16(d, idDeltas + s * 2)
            val rangeOffsetPos = idRangeOffsets + s * 2
            val rangeOffset = u16(d, rangeOffsetPos)
            if (start > end) continue
            for (c in start..minOf(end, 0xFFFE)) {
                val glyph = if (rangeOffset == 0) {
                    (c + delta) and 0xFFFF
                } else {
                    val addr = rangeOffsetPos + rangeOffset + 2 * (c - start)
                    if (addr + 1 >= d.size) 0 else {
                        val g = u16(d, addr)
                        if (g == 0) 0 else (g + delta) and 0xFFFF
                    }
                }
                if (glyph != 0) out.set(c)
            }
        }
    }

    private fun readFormat6(d: ByteArray, o: Int, out: BitSet) {
        val first = u16(d, o + 6)
        val count = u16(d, o + 8)
        for (i in 0 until count) {
            if (u16(d, o + 10 + i * 2) != 0) out.set(first + i)
        }
    }

    private fun readFormat12(d: ByteArray, o: Int, out: BitSet) {
        val groups = u32(d, o + 12).toInt()
        for (g in 0 until groups) {
            val e = o + 16 + g * 12
            val start = u32(d, e).toInt()
            val end = u32(d, e + 4).toInt()
            if (start < 0 || end < start || start > 0x10FFFF) continue
            out.set(start, minOf(end, 0x10FFFF) + 1)
        }
    }

    // ---------- name ----------

    private fun readName(d: ByteArray, o: Int): String? {
        val count = u16(d, o + 2)
        val stringsBase = o + u16(d, o + 4)
        var best: String? = null
        var bestScore = -1
        for (i in 0 until count) {
            val r = o + 6 + i * 12
            val platform = u16(d, r)
            val lang = u16(d, r + 4)
            val nameId = u16(d, r + 6)
            if (nameId != 4 && nameId != 1) continue
            val len = u16(d, r + 8)
            val off = stringsBase + u16(d, r + 10)
            if (off + len > d.size) continue
            val str = if (platform == 3 || platform == 0) {
                String(d, off, len, Charsets.UTF_16BE)
            } else {
                String(d, off, len, Charsets.ISO_8859_1)
            }
            var score = if (nameId == 4) 2 else 0
            if (platform == 3 && lang == 0x409) score += 1
            if (score > bestScore && str.isNotBlank()) {
                best = str
                bestScore = score
            }
        }
        return best
    }

    // ---------- WOFF -> sfnt ----------

    private class Table(val tag: Long, val checksum: Long, val data: ByteArray)

    private fun woffToSfnt(d: ByteArray): ByteArray {
        val flavor = u32(d, 4)
        val n = u16(d, 12)
        val tables = ArrayList<Table>()
        for (i in 0 until n) {
            val e = 44 + i * 20
            val tag = u32(d, e)
            val off = u32(d, e + 4).toInt()
            val comp = u32(d, e + 8).toInt()
            val orig = u32(d, e + 12).toInt()
            val checksum = u32(d, e + 16)
            val data = if (comp < orig) inflate(d, off, comp, orig) else d.copyOfRange(off, off + orig)
            tables.add(Table(tag, checksum, data))
        }
        var total = 12 + 16 * n
        val offsets = IntArray(n)
        for (i in 0 until n) {
            offsets[i] = total
            total += (tables[i].data.size + 3) and 3.inv()
        }
        val buf = ByteBuffer.allocate(total)
        var entrySelector = 0
        while ((1 shl (entrySelector + 1)) <= n) entrySelector++
        val searchRange = (1 shl entrySelector) * 16
        buf.putInt(flavor.toInt())
        buf.putShort(n.toShort())
        buf.putShort(searchRange.toShort())
        buf.putShort(entrySelector.toShort())
        buf.putShort((n * 16 - searchRange).toShort())
        for (i in 0 until n) {
            buf.putInt(tables[i].tag.toInt())
            buf.putInt(tables[i].checksum.toInt())
            buf.putInt(offsets[i])
            buf.putInt(tables[i].data.size)
        }
        for (i in 0 until n) {
            buf.position(offsets[i])
            buf.put(tables[i].data)
        }
        return buf.array()
    }

    private fun inflate(d: ByteArray, off: Int, len: Int, origLen: Int): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(d, off, len)
            val out = ByteArray(origLen)
            var pos = 0
            while (pos < origLen && !inflater.finished()) {
                val read = inflater.inflate(out, pos, origLen - pos)
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                pos += read
            }
            return out
        } finally {
            inflater.end()
        }
    }

    // ---------- чтение чисел (big-endian) ----------

    private fun u16(d: ByteArray, o: Int): Int =
        ((d[o].toInt() and 0xFF) shl 8) or (d[o + 1].toInt() and 0xFF)

    private fun u32(d: ByteArray, o: Int): Long =
        (u16(d, o).toLong() shl 16) or u16(d, o + 2).toLong()
}
