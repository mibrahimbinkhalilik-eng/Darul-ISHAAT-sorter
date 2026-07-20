package com.ibrahim.darulishaatsorter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

class PhotoWatcherService : Service() {

    private lateinit var prefs: android.content.SharedPreferences
    private var observer: ContentObserver? = null
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Watching gallery for new photos"))

        when (intent?.action) {
            ACTION_SCAN_EXISTING -> {
                Thread { scanExisting() }.start()
            }
            else -> {
                if (!prefs.contains(KEY_LAST_ID)) {
                    prefs.edit().putLong(KEY_LAST_ID, currentMaxImageId()).apply()
                }
                registerObserver()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        observer?.let { contentResolver.unregisterContentObserver(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerObserver() {
        if (observer != null) return
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                Thread { processNewImages() }.start()
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer!!
        )
        log("Watcher registered.")
    }

    private fun currentMaxImageId(): Long {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
            "${MediaStore.Images.Media._ID} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            }
        }
        return 0L
    }

    private fun processNewImages() {
        val lastId = prefs.getLong(KEY_LAST_ID, 0L)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val selection = "${MediaStore.Images.Media._ID} > ?"
        val args = arrayOf(lastId.toString())
        var newMaxId = lastId

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, args,
            "${MediaStore.Images.Media._ID} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val path = cursor.getString(dataCol)
                val name = cursor.getString(nameCol)
                if (id > newMaxId) newMaxId = id
                if (path != null) {
                    handlePhoto(File(path), name ?: File(path).name)
                }
            }
        }
        prefs.edit().putLong(KEY_LAST_ID, newMaxId).apply()
    }

    private fun scanExisting() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        var count = 0
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
            "${MediaStore.Images.Media._ID} DESC"
        )?.use { cursor ->
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol)
                val name = cursor.getString(nameCol)
                if (path != null) {
                    handlePhoto(File(path), name ?: File(path).name)
                    count++
                }
            }
        }
        log("Existing scan finished. Checked $count photos.")
    }

    private fun handlePhoto(file: File, displayName: String) {
        if (!file.exists()) return
        val destDirName = prefs.getString(MainActivity.KEY_FOLDER, "DarulIshaat") ?: "DarulIshaat"
        if (file.parentFile?.name == destDirName) return

        val keyword = (prefs.getString(MainActivity.KEY_KEYWORD, "Darul Ishaat") ?: "Darul Ishaat")
            .lowercase().trim()
        if (keyword.isEmpty()) return

        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text.lowercase()
                    if (text.contains(keyword)) {
                        moveFile(file, destDirName)
                    }
                }
                .addOnFailureListener {
                    log("OCR failed for $displayName: ${it.message}")
                }
        } catch (e: Exception) {
            log("Error reading $displayName: ${e.message}")
        }
    }

    private fun moveFile(file: File, destDirName: String) {
        try {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val destDir = File(picturesDir, destDirName)
            if (!destDir.exists()) destDir.mkdirs()
