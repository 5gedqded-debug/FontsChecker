package com.fontschecker.app

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile

/** Поддержка архивов (ZIP и 7z): поиск шрифтов внутри и извлечение отдельного файла. */
object Archives {

    enum class Kind { NONE, ZIP, SEVEN_Z }

    class Entry(val path: String, val size: Long) {
        val name: String get() = path.substringAfterLast('/')
    }

    private val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc", "otc", "woff", "woff2")
    private const val MAX_ENTRY_SIZE = 200L * 1024 * 1024

    fun detect(file: File): Kind {
        val head = ByteArray(6)
        val n = FileInputStream(file).use { it.read(head) }
        if (n >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
            (head[2] == 0x03.toByte() || head[2] == 0x05.toByte() || head[2] == 0x07.toByte())
        ) return Kind.ZIP
        if (n >= 6 && head[0] == 0x37.toByte() && head[1] == 0x7A.toByte() &&
            head[2] == 0xBC.toByte() && head[3] == 0xAF.toByte() &&
            head[4] == 0x27.toByte() && head[5] == 0x1C.toByte()
        ) return Kind.SEVEN_Z
        return Kind.NONE
    }

    private fun isFontPath(path: String): Boolean {
        val name = path.substringAfterLast('/')
        if (name.startsWith("._") || path.startsWith("__MACOSX/")) return false
        return name.substringAfterLast('.', "").lowercase() in FONT_EXTENSIONS
    }

    fun listFonts(file: File, kind: Kind): List<Entry> {
        val result = ArrayList<Entry>()
        when (kind) {
            Kind.ZIP -> openZip(file).use { zf ->
                for (e in zf.entries()) {
                    if (!e.isDirectory && isFontPath(e.name)) result.add(Entry(e.name, e.size))
                }
            }
            Kind.SEVEN_Z -> SevenZFile(file).use { sz ->
                var e = sz.nextEntry
                while (e != null) {
                    if (!e.isDirectory && isFontPath(e.name)) result.add(Entry(e.name, e.size))
                    e = sz.nextEntry
                }
            }
            Kind.NONE -> {}
        }
        return result.sortedBy { it.path.lowercase() }
    }

    fun read(file: File, kind: Kind, path: String): ByteArray {
        when (kind) {
            Kind.ZIP -> openZip(file).use { zf ->
                val e = zf.getEntry(path) ?: throw UnsupportedFontException("Файл не найден в архиве")
                if (e.size > MAX_ENTRY_SIZE) throw UnsupportedFontException("Файл слишком большой")
                return zf.getInputStream(e).use { it.readBytes() }
            }
            Kind.SEVEN_Z -> SevenZFile(file).use { sz ->
                var e = sz.nextEntry
                while (e != null) {
                    if (e.name == path) {
                        if (e.size > MAX_ENTRY_SIZE) throw UnsupportedFontException("Файл слишком большой")
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = sz.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                        return out.toByteArray()
                    }
                    e = sz.nextEntry
                }
            }
            Kind.NONE -> {}
        }
        throw UnsupportedFontException("Файл не найден в архиве")
    }

    private fun openZip(file: File): ZipFile = try {
        ZipFile(file)
    } catch (e: IllegalArgumentException) {
        // имена файлов не в UTF-8 (старые архиваторы)
        ZipFile(file, Charsets.ISO_8859_1)
    }
}
