package com.lb.video_trimmer_sample

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*


class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_VIDEO_TRIMMER = 1
        private const val REQUEST_STORAGE_READ_ACCESS_PERMISSION = 2
        internal const val EXTRA_INPUT_URI = "EXTRA_INPUT_URI"
        private val allowedVideoFileExtensions = arrayOf("mkv", "mp4", "3gp", "mov", "mts")
        private val videosMimeTypes = ArrayList<String>(allowedVideoFileExtensions.size)
    }

    init {
        val mimeTypeMap = MimeTypeMap.getSingleton()
        for (fileExtension in allowedVideoFileExtensions) {
            val mimeTypeFromExtension = mimeTypeMap.getMimeTypeFromExtension(fileExtension)
            if (mimeTypeFromExtension != null)
                videosMimeTypes.add(mimeTypeFromExtension)
        }
    }

    @SuppressLint("StaticFieldLeak")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        galleryButton.setOnClickListener { pickFromGallery() }
        cameraButton.setOnClickListener { openVideoCapture() }
    }

    private fun openVideoCapture() {
        val videoCapture = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        startActivityForResult(videoCapture, REQUEST_VIDEO_TRIMMER)
    }

    private fun pickFromGallery() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                getString(R.string.permission_read_storage_rationale),
                REQUEST_STORAGE_READ_ACCESS_PERMISSION
            )
        } else {
            var intentForChoosingVideos = ThirdPartyIntentsUtil.getPickFileChooserIntent(
                this,
                null,
                false,
                true,
                "video/*",
                videosMimeTypes.toTypedArray(),
                null
            )
            if (intentForChoosingVideos == null)
                intentForChoosingVideos =
                    ThirdPartyIntentsUtil.getPickFileIntent(this, "video/*,", videosMimeTypes.toTypedArray())
            if (intentForChoosingVideos != null)
                startActivityForResult(intentForChoosingVideos, REQUEST_VIDEO_TRIMMER)
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_VIDEO_TRIMMER) {
                val uri = data!!.data
                if (uri != null && checkIfUriCanBeUsedForVideo(uri)) {
                    startTrimActivity(uri)
                } else {
                    Toast.makeText(this@MainActivity, R.string.toast_cannot_retrieve_selected_video, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private fun checkIfUriCanBeUsedForVideo(uri: Uri): Boolean {
        val mimeType = ThirdPartyIntentsUtil.getMimeType(this, uri)
        val identifiedAsVideo = mimeType != null && videosMimeTypes.contains(mimeType)
        if (!identifiedAsVideo)
            return false
        try {
            //check that it can be opened and trimmed using our technique
            val fileDescriptor = contentResolver.openFileDescriptor(uri, "r")?.fileDescriptor
            val inputStream = (if (fileDescriptor == null) null else contentResolver.openInputStream(uri))
                ?: return false
            inputStream.close()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun startTrimActivity(uri: Uri) {
        val intent = Intent(this, TrimmerActivity::class.java)
        intent.putExtra(EXTRA_INPUT_URI, uri)
        startActivity(intent)
    }

    /**
     * Requests given permission.
     * If the permission has been denied previously, a Dialog will prompt the user to grant the
     * permission, otherwise it is requested directly.
     */
    private fun requestPermission(permission: String, rationale: String, requestCode: Int) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.permission_title_rationale))
            builder.setMessage(rationale)
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(permission),
                    requestCode
                )
            }
            builder.setNegativeButton(android.R.string.cancel, null)
            builder.show()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_STORAGE_READ_ACCESS_PERMISSION -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickFromGallery()
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var url: String? = null
        when (item.itemId) {
            R.id.menuItem_all_my_apps -> url = "https://play.google.com/store/apps/developer?id=AndroidDeveloperLB"
            R.id.menuItem_all_my_repositories -> url = "https://github.com/AndroidDeveloperLB"
            R.id.menuItem_current_repository_website -> url = "https://github.com/AndroidDeveloperLB/VideoTrimmer"
            R.id.menuItem_show_recyclerViewSample -> {
                startActivity(Intent(this, MainActivity::class.java))
                return true
            }
        }
        if (url == null)
            return true
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        startActivity(intent)
        return true
    }
}
