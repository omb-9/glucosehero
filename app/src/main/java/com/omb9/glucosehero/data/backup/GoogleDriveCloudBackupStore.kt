package com.omb9.glucosehero.data.backup

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

/**
 * Google Drive REST v3 client using OkHttp (no Drive SDK dependency).
 *
 * Structurally complete: upload (multipart), download (`alt=media`), list, and
 * delete. Requires a user-supplied OAuth access token with `drive.file` scope.
 * The token is stored Keystore-wrapped in DataStore and is never logged.
 */
class GoogleDriveCloudBackupStore(
    private val client: OkHttpClient,
    private val accessToken: String,
) : CloudBackupStore {

    override val provider: CloudBackupProvider = CloudBackupProvider.GOOGLE_DRIVE

    override suspend fun upload(remoteName: String, encryptedFile: File): CloudBackupFile {
        val metadata = JSONObject()
            .put("name", remoteName)
            .put("mimeType", EncryptedBackupCipher.ENCRYPTED_MIME)
            .toString()
        val body = MultipartBody.Builder()
            .setType(RELATED)
            .addPart(MultipartBody.Part.create(metadata.toRequestBody(JSON)))
            .addPart(MultipartBody.Part.create(encryptedFile.asRequestBody(OCTET_STREAM)))
            .build()
        val request = Request.Builder()
            .url(UPLOAD_URL)
            .header(AUTHORIZATION, bearer())
            .post(body)
            .build()
        val json = JSONObject(execute(request, "upload").toString(Charsets.UTF_8))
        return CloudBackupFile(
            id = json.optString("id"),
            name = json.optString("name", remoteName),
            sizeBytes = encryptedFile.length(),
            modifiedAtMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun download(file: CloudBackupFile, destination: File) {
        if (file.id.isBlank()) throw CloudBackupException("Drive file id is missing.")
        val request = Request.Builder()
            .url("$FILES_URL/${file.id}?alt=media")
            .header(AUTHORIZATION, bearer())
            .get()
            .build()
        sinkToFile(request, "download", destination)
        if (destination.length() == 0L) {
            throw CloudBackupException("Drive download returned an empty body.")
        }
    }

    override suspend fun list(): List<CloudBackupFile> {
        val query = "name contains 'glucosehero' and trashed = false"
        val url = "$FILES_URL?pageSize=20&fields=files(id,name,size,modifiedTime)" +
            "&q=${java.net.URLEncoder.encode(query, Charsets.UTF_8.name())}"
        val request = Request.Builder()
            .url(url)
            .header(AUTHORIZATION, bearer())
            .get()
            .build()
        val json = JSONObject(execute(request, "list").toString(Charsets.UTF_8))
        val files: JSONArray = json.optJSONArray("files") ?: JSONArray()
        val result = ArrayList<CloudBackupFile>(files.length())
        for (i in 0 until files.length()) {
            val item = files.getJSONObject(i)
            val name = item.optString("name")
            if (!name.endsWith(EncryptedBackupCipher.FILE_SUFFIX)) continue
            result += CloudBackupFile(
                id = item.optString("id"),
                name = name,
                sizeBytes = item.optString("size").toLongOrNull(),
                modifiedAtMillis = null,
            )
        }
        return result
    }

    override suspend fun delete(file: CloudBackupFile) {
        if (file.id.isBlank()) throw CloudBackupException("Drive file id is missing.")
        val request = Request.Builder()
            .url("$FILES_URL/${file.id}")
            .header(AUTHORIZATION, bearer())
            .delete()
            .build()
        execute(request, "delete")
    }

    private fun bearer(): String {
        val token = accessToken.trim()
        if (token.isEmpty()) throw CloudBackupException("Google Drive access token is missing.")
        return "Bearer $token"
    }

    private fun execute(request: Request, action: String): ByteArray {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw CloudBackupException("Google Drive $action failed.", e)
        }
        response.use { result ->
            if (!result.isSuccessful) {
                throw CloudBackupException("Google Drive $action failed (HTTP ${result.code}).")
            }
            return result.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun sinkToFile(request: Request, action: String, destination: File) {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw CloudBackupException("Google Drive $action failed.", e)
        }
        response.use { result ->
            if (!result.isSuccessful) {
                throw CloudBackupException("Google Drive $action failed (HTTP ${result.code}).")
            }
            val body = result.body ?: throw CloudBackupException("Google Drive $action returned an empty body.")
            destination.outputStream().use { output ->
                body.byteStream().copyTo(output)
            }
        }
    }

    companion object {
        private const val AUTHORIZATION = "Authorization"
        private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val OCTET_STREAM = EncryptedBackupCipher.ENCRYPTED_MIME.toMediaType()
        private val RELATED = "multipart/related".toMediaType()
    }
}

class GoogleDriveCloudBackupStoreFactory @Inject constructor(
    @Named("webdav") private val client: OkHttpClient,
) {
    fun create(accessToken: String): GoogleDriveCloudBackupStore =
        GoogleDriveCloudBackupStore(client, accessToken)
}
