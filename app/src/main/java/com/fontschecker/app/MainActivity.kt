package com.fontschecker.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** Стартовый экран: выбор файла шрифта любого формата. */
class MainActivity : AppCompatActivity() {

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openPreview(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.pickButton).setOnClickListener {
            // */* — чтобы можно было выбрать файл любого расширения (ttf, otf, ttc, woff...).
            picker.launch(arrayOf("*/*"))
        }
    }

    private fun openPreview(uri: Uri) {
        val intent = Intent(this, PreviewActivity::class.java)
            .setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }
}
