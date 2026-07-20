package com.ibrahim.darulishaatsorter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var txtLog: TextView
    private lateinit var editKeyword: EditText
    private lateinit var editFolder: EditText

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val msg = intent?.getStringExtra("message") ?: return
            appendLog(msg)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }
        if (denied.isEmpty()) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_LONG).show()
        }
        maybeRequestAllFilesAccess()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        txtLog = findViewById(R.id.txtLog)
        editKeyword = findViewById(R.id.editKeyword)
        editFolder = findViewById(R.id.editFolder)

        editKeyword.setText(prefs.getString(KEY_KEYWORD, "Darul Ishaat"))
        editFolder.setText(prefs.getString(KEY_FOLDER, "DarulIshaat"))

        findViewById<Button>(R.id.btnGrantPermissions).setOnClickListener {
            requestRuntimePermissions()
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            saveSettings()
            if (!hasAllFilesAccess()) {
                Toast.makeText(this, "Grant permissions first", Toast.LENGTH_LONG).show()
                requestRuntimePermissions()
                return@setOnClickListener
            }
            val intent = Intent(this, PhotoWatcherService::class.java)
            intent.action = PhotoWatcherService.ACTION_START
            ContextCompat.startForegroundService(this, intent)
            appendLog("Started watching for new photos…")
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            val intent = Intent(this, PhotoWatcherService::class.java)
            stopService(intent)
            appendLog("Stopped watching.")
        }

        findViewById<Button>(R.id.btnScanExisting).setOnClickListener {
            saveSettings()
            if (!hasAllFilesAccess()) {
                Toast.makeText(this, "Grant permissions first", Toast.LENGTH_LONG).show()
                requestRuntimePermissions()
                return@setOnClickListener
            }
            val intent = Intent(this, PhotoWatcherService::class.java)
            intent.action = PhotoWatcherService.ACTION_SCAN_EXISTING
            ContextCompat.startForegroundService(this, intent)
            appendLog("Scanning existing gallery photos…")
        }
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(logReceiver, IntentFilter(PhotoWatcherService.LOG_ACTION))
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
    }

    private fun saveSettings() {
        prefs.edit()
            .putString(KEY_KEYWORD, editKeyword.text.toString().trim())
            .putString(KEY_FOLDER, editFolder.text.toString().trim())
            .apply()
    }

    private fun appendLog(msg: String) {
        txtLog.append("\n$msg")
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            perms.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            perms.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val toRequest = perms.filter {
            ActivityCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        } else {
            maybeRequestAllFilesAccess()
        }
    }

    private fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun maybeRequestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Toast.makeText(
                this,
                "Please allow 'All files access' on the next screen so the app can move photos",
                Toast.LENGTH_LONG
            ).show()
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    companion object {
        const val PREFS_NAME = "sorter_prefs"
        const val KEY_KEYWORD = "keyword"
        const val KEY_FOLDER = "folder"
    }
}
