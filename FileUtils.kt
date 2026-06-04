package com.notevault.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import com.notevault.data.local.entities.AttachmentType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notesDir: File get() = File(context.filesDir, "notes").also { it.mkdirs() }
    private val thumbsDir: File get() = File(context.filesDir, "thumbs").also { it.mkdirs() }

    fun noteDir(noteId: Long): File =
        File(notesDir, "note_$noteId").also { it.mkdirs() }

    fun copyFileToInternal(uri: Uri, noteId: Long): File? {
        return try {
            val dir = noteDir(noteId)
            val fileName = getFileName(uri) ?: "file_${System.currentTimeMillis()}"
            val destFile = File(dir, fileName).uniquify()
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            destFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveBytesToInternal(bytes: ByteArray, fileName: String, noteId: Long): File? {
        return try {
            val destFile = File(noteDir(noteId), fileName).uniquify()
            destFile.writeBytes(bytes)
            destFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteFile(path: String) {
        File(path).takeIf { it.exists() }?.delete()
    }

    fun deleteNoteDirectory(noteId: Long) {
        File(notesDir, "note_$noteId").deleteRecursively()
        File(thumbsDir, "note_$noteId").deleteRecursively()
    }

    fun getMimeType(uri: Uri): String? {
        return context.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            )
    }

    fun detectAttachmentType(uri: Uri): AttachmentType {
        val mime = getMimeType(uri) ?: return AttachmentType.FILE
        return detectTypeFromMime(mime)
    }

    fun detectTypeFromMime(mimeType: String): AttachmentType {
        return when {
            mimeType.startsWith("image/") -> AttachmentType.IMAGE
            mimeType.startsWith("video/") -> AttachmentType.VIDEO
            mimeType.startsWith("audio/") -> AttachmentType.AUDIO
            mimeType == "application/pdf"
                || mimeType.contains("word")
                || mimeType.contains("sheet")
                || mimeType.contains("presentation") -> AttachmentType.DOCUMENT
            else -> AttachmentType.FILE
        }
    }

    fun getImageWidth(path: String): Int? {
        val size = tryDecodeSize(path) ?: return null
        return size.first
    }

    fun getImageHeight(path: String): Int? {
        val size = tryDecodeSize(path) ?: return null
        return size.second
    }

    private fun tryDecodeSize(path: String): Pair<Int, Int>? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                Pair(opts.outWidth, opts.outHeight)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun createThumbnail(sourceFile: File, noteId: Long): File? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, opts)
            val maxDim = 256
            val scale = maxOf(opts.outWidth, opts.outHeight) / maxDim
            val bitmapOpts = BitmapFactory.Options().apply {
                inSampleSize = if (scale > 1) scale else 1
            }
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, bitmapOpts)
                ?: return null
            val thumbDir = File(thumbsDir, "note_$noteId").also { it.mkdirs() }
            val thumbFile = File(thumbDir, "thumb_${sourceFile.nameWithoutExtension}.jpg")
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            bitmap.recycle()
            thumbFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getFileName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }

    private fun File.uniquify(): File {
        if (!exists()) return this
        val name = nameWithoutExtension
        val ext = if (extension.isNotEmpty()) ".$extension" else ""
        var counter = 1
        var result: File
        do {
            result = File(parent, "${name}_${counter++}$ext")
        } while (result.exists())
        return result
    }
}
