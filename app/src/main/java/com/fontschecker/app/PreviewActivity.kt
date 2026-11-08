package com.fontschecker.app

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * Экран просмотра: карусель языков, которые есть в шрифте, и примеры текста.
 * Если открыт архив (ZIP / 7z), сначала показывается список найденных в нём шрифтов.
 */
class PreviewActivity : AppCompatActivity() {

    private class Page(
        val label: String,
        val lines: List<String>,
        val sample: String,
        val custom: Boolean = false,
    )

    private lateinit var toolbar: MaterialToolbar
    private lateinit var chipGroup: ChipGroup
    private lateinit var content: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var errorView: TextView
    private lateinit var chipScroll: View
    private lateinit var scroll: View
    private lateinit var fontList: ListView

    private var typeface: Typeface? = null
    private var fontData: FontData? = null
    private var fileName: String = ""
    private var fileSize: Long = 0
    private var languages: List<Lang> = emptyList()
    private var pages: List<Page> = emptyList()
    private var customText = "Hamburgefonstiv 0123456789"

    // Архив
    private var archiveName: String = ""
    private var archiveFile: File? = null
    private var archiveKind = Archives.Kind.NONE
    private var entries: List<Archives.Entry> = emptyList()

    private val backToList = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        toolbar = findViewById(R.id.toolbar)
        chipGroup = findViewById(R.id.chipGroup)
        content = findViewById(R.id.content)
        progress = findViewById(R.id.progress)
        errorView = findViewById(R.id.errorView)
        chipScroll = findViewById(R.id.chipScroll)
        scroll = findViewById(R.id.scroll)
        fontList = findViewById(R.id.fontList)

        onBackPressedDispatcher.addCallback(this, backToList)

        toolbar.setTitle(R.string.preview_title)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.inflateMenu(R.menu.preview_menu)
        toolbar.menu.findItem(R.id.action_info).isVisible = false
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_info) {
                showInfo()
                true
            } else false
        }

        val uri: Uri? = intent.data
        if (uri == null) {
            showError(getString(R.string.error_no_file))
            return
        }
        loadSource(uri)
    }

    // ---------- загрузка ----------

    private fun loadSource(uri: Uri) {
        queryFileInfo(uri)
        archiveName = fileName
        toolbar.subtitle = fileName
        showProgress()
        cacheDir.listFiles()
            ?.filter { it.name.startsWith("font_") || it.name.startsWith("src_") }
            ?.forEach { it.delete() }

        Thread {
            try {
                val src = File(cacheDir, "src_${System.nanoTime()}")
                val input = contentResolver.openInputStream(uri)
                    ?: throw UnsupportedFontException(getString(R.string.error_read))
                input.use { i -> src.outputStream().use { o -> i.copyTo(o) } }

                val kind = Archives.detect(src)
                if (kind == Archives.Kind.NONE) {
                    if (src.length() > MAX_FILE_SIZE) throw UnsupportedFontException(getString(R.string.error_too_big))
                    val raw = src.readBytes()
                    src.delete()
                    val (data, tf) = prepare(raw)
                    runOnUiThread { onLoaded(data, tf, fileName, fileSize) }
                } else {
                    archiveFile = src
                    archiveKind = kind
                    val list = try {
                        Archives.listFonts(src, kind)
                    } catch (e: UnsupportedFontException) {
                        throw e
                    } catch (e: Exception) {
                        throw UnsupportedFontException(getString(R.string.error_archive))
                    }
                    if (list.isEmpty()) throw UnsupportedFontException(getString(R.string.error_no_fonts))
                    entries = list
                    if (list.size == 1) {
                        val entry = list[0]
                        val raw = readEntry(entry)
                        val (data, tf) = prepare(raw)
                        runOnUiThread { onLoaded(data, tf, entry.name, entry.size) }
                    } else {
                        runOnUiThread { showList() }
                    }
                }
            } catch (e: Throwable) {
                reportError(e)
            }
        }.start()
    }

    private fun openEntry(entry: Archives.Entry) {
        showProgress()
        Thread {
            try {
                val raw = readEntry(entry)
                val (data, tf) = prepare(raw)
                runOnUiThread { onLoaded(data, tf, entry.name, entry.size) }
            } catch (e: Throwable) {
                reportError(e)
            }
        }.start()
    }

    private fun readEntry(entry: Archives.Entry): ByteArray {
        val file = archiveFile ?: throw UnsupportedFontException(getString(R.string.error_read))
        try {
            return Archives.read(file, archiveKind, entry.path)
        } catch (e: UnsupportedFontException) {
            throw e
        } catch (e: Exception) {
            throw UnsupportedFontException(getString(R.string.error_archive))
        }
    }

    private fun prepare(raw: ByteArray): Pair<FontData, Typeface> {
        val data = FontParser.load(raw)
        val file = File(cacheDir, "font_${System.nanoTime()}.ttf")
        file.writeBytes(data.sfnt)
        val tf = Typeface.Builder(file).build()
            ?: throw UnsupportedFontException(getString(R.string.error_typeface))
        return data to tf
    }

    private fun reportError(e: Throwable) {
        val msg = if (e is UnsupportedFontException) e.message
        else getString(R.string.error_generic, e.javaClass.simpleName)
        runOnUiThread { showError(msg ?: getString(R.string.error_read)) }
    }

    private fun queryFileInfo(uri: Uri) {
        fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "font"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0) c.getString(n)?.let { fileName = it }
                    if (s >= 0 && !c.isNull(s)) fileSize = c.getLong(s)
                }
            }
        } catch (e: Exception) {
            // имя и размер файла необязательны
        }
    }

    // ---------- состояния экрана ----------

    private fun showProgress() {
        progress.visibility = View.VISIBLE
        chipScroll.visibility = View.GONE
        scroll.visibility = View.GONE
        fontList.visibility = View.GONE
        errorView.visibility = View.GONE
        toolbar.menu.findItem(R.id.action_info).isVisible = false
    }

    /** Список шрифтов внутри архива. */
    private fun showList() {
        progress.visibility = View.GONE
        chipScroll.visibility = View.GONE
        scroll.visibility = View.GONE
        errorView.visibility = View.GONE
        toolbar.menu.findItem(R.id.action_info).isVisible = false
        backToList.isEnabled = false
        toolbar.subtitle = getString(R.string.list_title, archiveName, entries.size)

        val labels = entries.map {
            if (it.size > 0) "${it.path}  (${formatSize(it.size)})" else it.path
        }
        fontList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        fontList.setOnItemClickListener { _, _, position, _ -> openEntry(entries[position]) }
        fontList.visibility = View.VISIBLE
    }

    private fun onLoaded(data: FontData, tf: Typeface, name: String, size: Long) {
        fontData = data
        typeface = tf
        fileName = name
        fileSize = size
        progress.visibility = View.GONE
        fontList.visibility = View.GONE
        errorView.visibility = View.GONE
        toolbar.menu.findItem(R.id.action_info).isVisible = true
        toolbar.subtitle = if (archiveFile != null) "$archiveName › $name" else name
        backToList.isEnabled = entries.size > 1

        languages = Languages.supportedBy(data.codepoints)
        val list = ArrayList<Page>()
        for (l in languages) {
            val lines = ArrayList<String>()
            l.upper?.let { lines.add(it) }
            lines.add(l.lower)
            lines.add(DIGITS)
            list.add(Page(l.name, lines, l.sample))
        }
        list.add(Page(getString(R.string.page_symbols), listOf(DIGITS, PUNCT, MATH), "Aa Bb 123 #?!"))
        list.add(Page(getString(R.string.page_custom), emptyList(), customText, custom = true))
        pages = list

        chipScroll.visibility = View.VISIBLE
        scroll.visibility = View.VISIBLE
        buildChips()
    }

    private fun showError(message: String) {
        progress.visibility = View.GONE
        chipScroll.visibility = View.GONE
        scroll.visibility = View.GONE
        fontList.visibility = View.GONE
        errorView.text = message
        errorView.visibility = View.VISIBLE
        // Из ошибки открытия одного шрифта в архиве можно вернуться к списку.
        backToList.isEnabled = entries.size > 1
    }

    // ---------- карусель и примеры ----------

    private fun buildChips() {
        chipGroup.removeAllViews()
        val ids = ArrayList<Int>()
        for (p in pages) {
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = p.label
                isCheckable = true
                isCheckedIconVisible = true
            }
            chipGroup.addView(chip)
            ids.add(chip.id)
        }
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val idx = ids.indexOf(checkedIds.firstOrNull())
            if (idx >= 0) render(pages[idx])
        }
        chipGroup.check(ids.first())
    }

    private fun render(page: Page) {
        content.removeAllViews()
        val tf = typeface ?: return

        for (line in page.lines) {
            content.addView(makeText(line, 22f, tf).apply {
                setPadding(0, 0, 0, dp(6))
            })
        }
        if (page.lines.isNotEmpty()) content.addView(divider())

        val sampleViews = ArrayList<TextView>()
        if (page.custom) {
            val edit = EditText(this).apply {
                setText(customText)
                hint = getString(R.string.custom_hint)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setPadding(0, dp(4), 0, dp(12))
            }
            content.addView(edit)
            edit.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    customText = s?.toString().orEmpty()
                    sampleViews.forEach { it.text = customText }
                }
            })
        }

        val text = if (page.custom) customText else page.sample
        for (size in SIZES) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val label = TextView(this).apply {
                this.text = size.toString()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                alpha = 0.6f
                layoutParams = LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(6)
                }
            }
            val sample = makeText(text, size.toFloat(), tf).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            sampleViews.add(sample)
            row.addView(label)
            row.addView(sample)
            content.addView(row)
        }
    }

    private fun makeText(text: String, sp: Float, tf: Typeface) = TextView(this).apply {
        this.text = text
        typeface = tf
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(0x33888888)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(6)
            bottomMargin = dp(10)
        }
    }

    private fun showInfo() {
        val d = fontData ?: return
        val langs = if (languages.isEmpty()) getString(R.string.info_no_langs)
        else languages.joinToString(", ") { it.name }
        val msg = buildString {
            appendLine(getString(R.string.info_name, d.fullName ?: "—"))
            appendLine(getString(R.string.info_file, fileName))
            appendLine(getString(R.string.info_format, d.format))
            if (fileSize > 0) appendLine(getString(R.string.info_size, formatSize(fileSize)))
            appendLine(getString(R.string.info_glyphs, d.codepoints.cardinality()))
            append(getString(R.string.info_langs, languages.size, langs))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.info_title)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format("%.1f МБ", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format("%.1f КБ", bytes / 1024.0)
        else -> "$bytes Б"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val DIGITS = "0123456789"
        private const val PUNCT = ",.:;!?'\"()[]{}«»…"
        private const val MATH = "+-*/=<>@#\$%&_~^\\|"
        private const val MAX_FILE_SIZE = 200L * 1024 * 1024
        private val SIZES = listOf(12, 18, 24, 36, 48, 60)
    }
}
