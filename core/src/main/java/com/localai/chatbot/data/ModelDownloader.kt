package com.localai.chatbot.data

import android.content.Context
import com.localai.chatbot.models.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ModelDownloader(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun checkStorageSpace(requiredSizeInBytes: Long): Boolean {
        val path = context.getExternalFilesDir(null) ?: return false
        val freeBytes = path.freeSpace
        return freeBytes > requiredSizeInBytes + (100 * 1024 * 1024) // Buffer of 100MB
    }

    fun getModelPath(fileName: String): String {
        val dir = File(context.getExternalFilesDir(null), "models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName).absolutePath
    }

    fun isModelDownloaded(fileName: String): Boolean {
        return File(getModelPath(fileName)).exists()
    }

    /**
     * If a model is bundled inside the APK as an asset (e.g. app/src/main/assets/models/<fileName>),
     * copy it once to external files so it behaves like a normal downloaded model.
     *
     * Returns true if the model file exists after this call.
     */
    fun installBundledModelIfMissing(fileName: String, assetRelativePath: String = "models/$fileName"): Boolean {
        val targetFile = File(getModelPath(fileName))
        if (targetFile.exists()) return true

        // If the asset is not present, do nothing.
        val input = try {
            context.assets.open(assetRelativePath)
        } catch (_: IOException) {
            return false
        } catch (_: RuntimeException) {
            // Some devices/ROMs throw RuntimeException for missing assets.
            return false
        }

        val tempFile = File(getModelPath(fileName + ".asset.tmp"))
        if (tempFile.exists()) tempFile.delete()

        return try {
            input.use { inStream ->
                FileOutputStream(tempFile).use { outStream ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        outStream.write(buffer, 0, bytesRead)
                    }
                    outStream.flush()
                }
            }

            if (tempFile.renameTo(targetFile)) {
                true
            } else {
                // Fallback: try copy then delete temp.
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
                true
            }
        } catch (e: Exception) {
            println("ModelDownloader: Bundled install failed: ${e.message}")
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }

    suspend fun downloadModel(model: ModelInfo, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val targetFile = File(getModelPath(model.fileName))
        val tempFile = File(getModelPath(model.fileName + ".tmp"))
        
        if (targetFile.exists()) return@withContext true
        if (tempFile.exists()) tempFile.delete()

        val request = Request.Builder()
            .url(model.url)
            .header("User-Agent", "PocketMindAI-Android")
            .build()
            
        try {
            println("ModelDownloader: Starting download from ${model.url}")
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                println("ModelDownloader: Download FAILED. Code: ${response.code}, Message: ${response.message}")
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val contentLength = body.contentLength()
            
            if (contentLength > 0 && !checkStorageSpace(contentLength)) {
                println("ModelDownloader: Insufficient storage space.")
                return@withContext false
            }
            
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024) // Larger buffer for speed
                    var bytesRead: Int
                    var totalBytesRead: Long = 0
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (contentLength > 0) {
                            val progress = totalBytesRead.toFloat() / contentLength
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }
            
            if (tempFile.renameTo(targetFile)) {
                println("ModelDownloader: Download Successful: ${model.name}")
                true
            } else {
                println("ModelDownloader: Failed to rename temp file.")
                false
            }
        } catch (e: Exception) {
            println("ModelDownloader: Exception during download: ${e.message}")
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }
    
    fun deleteModel(fileName: String) {
        val file = File(getModelPath(fileName))
        if (file.exists()) file.delete()
    }
}
