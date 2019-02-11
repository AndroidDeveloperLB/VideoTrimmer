package com.lb.video_trimmer_sample

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.util.*

object ThirdPartyIntentsUtil {
    //    https://medium.com/@louis993546/how-to-ask-system-to-open-intent-to-select-jpg-and-png-only-on-android-i-e-no-gif-e0491af240bf
    //example usage: mainType= "*/*"  extraMimeTypes= arrayOf("image/*", "video/*") - choose all images and videos
    //example usage: mainType= "image/*"  extraMimeTypes= arrayOf("image/jpeg", "image/png") - choose all images of png and jpeg types
    /**note that this only requests to choose the files, but it's not guaranteed that this is what you will get*/
    @JvmStatic
    fun getPickFileIntent(context: Context, mainType: String = "*/*", extraMimeTypes: Array<String>? = null): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
            return null
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = mainType
        if (!extraMimeTypes.isNullOrEmpty())
            intent.putExtra(Intent.EXTRA_MIME_TYPES, extraMimeTypes)
        if (context.packageManager.queryIntentActivities(intent, 0).isNullOrEmpty())
            return null
        return intent
    }

    //    https://github.com/linchaolong/ImagePicker/blob/master/library/src/main/java/com/linchaolong/android/imagepicker/cropper/CropImage.java
    @JvmStatic
    fun getPickFileChooserIntent(
            context: Context, title: CharSequence?, preferDocuments: Boolean = true, includeCameraIntents: Boolean, mainType: String
            , extraMimeTypes: Array<String>? = null, extraIntents: ArrayList<Intent>? = null
    ): Intent? {
        val packageManager = context.packageManager
        var allIntents =
                getGalleryIntents(packageManager, Intent.ACTION_GET_CONTENT, mainType, extraMimeTypes)
        if (allIntents.isEmpty()) {
            // if no intents found for get-content try pick intent action (Huawei P9).
            allIntents =
                    getGalleryIntents(packageManager, Intent.ACTION_PICK, mainType, extraMimeTypes)
        }
        if (includeCameraIntents) {
            val cameraIntents = getCameraIntents(packageManager)
            allIntents.addAll(0, cameraIntents)
        }
        //        Log.d("AppLog", "got ${allIntents.size} intents")
        if (allIntents.isEmpty())
            return null
        if (preferDocuments)
            for (intent in allIntents)
                if (intent.component!!.packageName == "com.android.documentsui")
                    return intent
        if (allIntents.size == 1)
            return allIntents[0]
        var target: Intent? = null
        for ((index, intent) in allIntents.withIndex()) {
            if (intent.component!!.packageName == "com.android.documentsui") {
                target = intent
                allIntents.removeAt(index)
                break
            }
        }
        if (target == null)
            target = allIntents[allIntents.size - 1]
        allIntents.removeAt(allIntents.size - 1)
        // Create a chooser from the main  intent
        val chooserIntent = Intent.createChooser(target, title)
        if (extraIntents != null && extraIntents.isNotEmpty())
            allIntents.addAll(extraIntents)
        // Add all other intents
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, allIntents.toTypedArray<Parcelable>())
        return chooserIntent
    }

    private fun getCameraIntents(packageManager: PackageManager): ArrayList<Intent> {
        val cameraIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        val listCamera = packageManager.queryIntentActivities(cameraIntent, 0)
        val intents = ArrayList<Intent>()
        for (res in listCamera) {
            val intent = Intent(cameraIntent)
            intent.component = ComponentName(res.activityInfo.packageName, res.activityInfo.name)
            intent.`package` = res.activityInfo.packageName
            intents.add(intent)
        }
        return intents
    }

    /**
     * Get all Gallery intents for getting image from one of the apps of the device that handle
     * images. Intent.ACTION_GET_CONTENT and then Intent.ACTION_PICK
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private fun getGalleryIntents(
            packageManager: PackageManager, action: String,
            mainType: String, extraMimeTypes: Array<String>? = null
    ): ArrayList<Intent> {
        val galleryIntent = if (action == Intent.ACTION_GET_CONTENT)
            Intent(action)
        else
            Intent(action, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryIntent.type = mainType
        if (!extraMimeTypes.isNullOrEmpty()) {
            galleryIntent.addCategory(Intent.CATEGORY_OPENABLE)
            galleryIntent.putExtra(Intent.EXTRA_MIME_TYPES, extraMimeTypes)
        }
        val listGallery = packageManager.queryIntentActivities(galleryIntent, 0)
        val intents = ArrayList<Intent>()
        for (res in listGallery) {
            val intent = Intent(galleryIntent)
            intent.component = ComponentName(res.activityInfo.packageName, res.activityInfo.name)
            intent.`package` = res.activityInfo.packageName
            intents.add(intent)
        }
        return intents
    }

    @JvmStatic
    fun getMimeType(context: Context, uri: Uri): String? {
        return if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
            val cr = context.contentResolver
            cr.getType(uri)
        } else {
            val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase())
        }
    }

}
