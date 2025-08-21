package com.ghet.nomediacreator

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var addButton: Button
    private lateinit var deleteButton: Button
    private lateinit var listView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ArrayAdapter<String>

    private val nomediaFiles = mutableListOf<String>()

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                askRecursiveChoice(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        addButton = findViewById(R.id.addButton)
        deleteButton = findViewById(R.id.deleteButton)
        listView = findViewById(R.id.listView)
        progressBar = findViewById(R.id.progressBar)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, nomediaFiles)
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        listView.adapter = adapter

        addButton.setOnClickListener {
            folderPicker.launch(null)
        }

        deleteButton.setOnClickListener {
            deleteSelectedNomedia()
        }
    }

    private fun askRecursiveChoice(folderUri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Apply .nomedia")
            .setMessage("Do you want just this folder, or this folder and all subfolders?")
            .setPositiveButton("This folder only") { _, _ ->
                createNomediaWithLoading(folderUri, recursive = false)
            }
            .setNegativeButton("Include subfolders") { _, _ ->
                createNomediaWithLoading(folderUri, recursive = true)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun createNomediaWithLoading(folderUri: Uri, recursive: Boolean) {
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0

        CoroutineScope(Dispatchers.IO).launch {
            val folder = DocumentFile.fromTreeUri(this@MainActivity, folderUri)
            folder?.let {
                if (recursive) {
                    val allFolders = collectFolders(it)
                    val total = allFolders.size
                    var done = 0

                    for (f in allFolders) {
                        createNomediaInFolder(f)
                        done++
                        withContext(Dispatchers.Main) {
                            val percent = (done * 100) / total
                            progressBar.progress = percent
                        }
                    }
                } else {
                    createNomediaInFolder(it)
                    withContext(Dispatchers.Main) {
                        progressBar.progress = 100
                    }
                }
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                adapter.notifyDataSetChanged()
                Toast.makeText(
                    this@MainActivity,
                    ".nomedia file(s) created",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Collect all subfolders recursively so we can count them.
     */
    private fun collectFolders(folder: DocumentFile): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        result.add(folder)
        for (f in folder.listFiles()) {
            if (f.isDirectory) {
                result.addAll(collectFolders(f))
            }
        }
        return result
    }


    private fun createNomediaRecursive(folder: DocumentFile) {
        createNomediaInFolder(folder)
        folder.listFiles().forEach { file ->
            if (file.isDirectory) {
                createNomediaRecursive(file)
            }
        }
    }

    private fun createNomediaInFolder(folder: DocumentFile) {
        val existing = folder.findFile(".nomedia")
        if (existing == null) {
            folder.createFile("application/octet-stream", ".nomedia")?.let { file ->
                nomediaFiles.add(file.uri.toString())
            }
        }
    }

    private fun deleteSelectedNomedia() {
        val checked = listView.checkedItemPositions
        for (i in (nomediaFiles.size - 1) downTo 0) {
            if (checked.get(i)) {
                val uri = Uri.parse(nomediaFiles[i])
                val docFile = DocumentFile.fromSingleUri(this, uri)
                docFile?.delete()
                nomediaFiles.removeAt(i)
            }
        }
        adapter.notifyDataSetChanged()
    }
}
