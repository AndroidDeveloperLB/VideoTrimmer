package com.lb.video_trimmer_sample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lb.video_trimmer_library.interfaces.VideoTrimmingListener
import kotlinx.android.synthetic.main.activity_trimmer.*
import java.io.File

class TrimmerActivity : AppCompatActivity(), VideoTrimmingListener {
//    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trimmer)
        val inputVideoUri: Uri? = intent?.getParcelableExtra(MainActivity.EXTRA_INPUT_URI)
        if (inputVideoUri == null) {
            finish()
            return
        }
        //setting progressbar
//        progressDialog = ProgressDialog(this)
//        progressDialog!!.setCancelable(false)
//        progressDialog!!.setMessage(getString(R.string.trimming_progress))
        videoTrimmerView.setMaxDurationInMs(10 * 1000)
        videoTrimmerView.setMinDurationInMs(1 * 1000)
        videoTrimmerView.setOnK4LVideoListener(this)
        val parentFolder = getExternalFilesDir(null)!!
        parentFolder.mkdirs()
        val fileName = "trimmedVideo_${System.currentTimeMillis()}.mp4"
        val trimmedVideoFile = File(parentFolder, fileName)
        videoTrimmerView.setDestinationFile(trimmedVideoFile)
        videoTrimmerView.setVideoURI(inputVideoUri)
        videoTrimmerView.setVideoInformationVisibility(true)
    }

    override fun onTrimStarted() {
        trimmingProgressView.visibility = View.VISIBLE
    }

    override fun onFinishedTrimming(uri: Uri?) {
        trimmingProgressView.visibility = View.GONE
        if (uri == null) {
            Toast.makeText(this@TrimmerActivity, "failed trimming", Toast.LENGTH_SHORT).show()
        } else {
            val msg = getString(R.string.video_saved_at, uri.path)
            Toast.makeText(this@TrimmerActivity, msg, Toast.LENGTH_SHORT).show()
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setDataAndType(uri, "video/mp4")
            startActivity(intent)
        }
        finish()
    }

    override fun onErrorWhileViewingVideo(what: Int, extra: Int) {
        trimmingProgressView.visibility = View.GONE
        Toast.makeText(this@TrimmerActivity, "error while previewing video", Toast.LENGTH_SHORT).show()
    }

    override fun onVideoPrepared() {
        //        Toast.makeText(TrimmerActivity.this, "onVideoPrepared", Toast.LENGTH_SHORT).show();
    }
}
