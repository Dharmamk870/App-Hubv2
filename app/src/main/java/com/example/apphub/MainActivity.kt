package com.example.apphub

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

data class ApkItem(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val apkFile: File,
    val isInstalled: Boolean
)

class MainActivity : AppCompatActivity() {

    private lateinit var gridView: GridView
    private val targetFolderName = "MyApps"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gridView = findViewById(R.id.appGridView)
        checkPermissionsAndLoad()
    }

    override fun onResume() {
        super.onResume()
        loadApksFromFolder()
    }

    private fun checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                loadApksFromFolder()
            }
        } else {
            loadApksFromFolder()
        }
    }

    private fun loadApksFromFolder() {
        val folder = File(Environment.getExternalStorageDirectory(), targetFolderName)
        if (!folder.exists()) {
            folder.mkdirs()
            Toast.makeText(this, "Created /$targetFolderName folder. Add APKs there!", Toast.LENGTH_LONG).show()
            return
        }

        val apkFiles = folder.listFiles { file -> file.extension.equals("apk", ignoreCase = true) } ?: return
        val pm = packageManager
        val appList = mutableListOf<ApkItem>()

        for (file in apkFiles) {
            val pkgInfo = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: continue
            val appInfo = pkgInfo.applicationInfo ?: continue

            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath

            val appName = pm.getApplicationLabel(appInfo).toString()
            val appIcon = pm.getApplicationIcon(appInfo)
            val pkgName = pkgInfo.packageName

            val isInstalled = try {
                pm.getPackageInfo(pkgName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }

            appList.add(ApkItem(appName, pkgName, appIcon, file, isInstalled))
        }

        gridView.adapter = AppGridAdapter(this, appList)
        gridView.setOnItemClickListener { _, _, position, _ ->
            openOrInstallApp(appList[position])
        }
    }

    private fun openOrInstallApp(item: ApkItem) {
        if (item.isInstalled) {
            val launchIntent = packageManager.getLaunchIntentForPackage(item.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Cannot launch this app directly", Toast.LENGTH_SHORT).show()
            }
        } else {
            val apkUri = FileProvider.getUriForFile(this, "$packageName.provider", item.apkFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(installIntent)
        }
    }
}

class AppGridAdapter(private val context: Context, private val items: List<ApkItem>) : BaseAdapter() {
    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.grid_item, parent, false)
        val item = items[position]

        view.findViewById<ImageView>(R.id.appIcon).setImageDrawable(item.icon)
        view.findViewById<TextView>(R.id.appName).text = item.name
        return view
    }
}
