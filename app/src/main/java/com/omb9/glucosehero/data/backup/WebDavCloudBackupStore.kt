package com.omb9.glucosehero.data.backup

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named

/**
 * WebDAV client (PUT / GET / PROPFIND / DELETE) over the dedicated OkHttp
 * client that already refuses public cleartext. Basic auth is sent only to
 * the configured origin. Response bodies are treated as opaque ciphertext.
 */
class WebDavCloudBackupStore(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val username: String,
    private val password: String,
) : CloudBackupStore {

    override val provider: CloudBackupProvider = CloudBackupProvider.WEBDAV

    override suspend fun upload(remoteName: String, encryptedFile: File): CloudBackupFile {
        val url = objectUrl(remoteName)
        val request = Request.Builder()
            .url(url)
            .header(AUTHORIZATION, credentials())
            .put(encryptedFile.asRequestBody(OCTET_STREAM))
            .build()
        execute(request, "upload")
        return CloudBackupFile(
            id = url,
            name = remoteName,
            sizeBytes = encryptedFile.length(),
            modifiedAtMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun download(file: CloudBackupFile, destination: File) {
        val request = Request.Builder()
            .url(file.id.ifBlank { objectUrl(file.name) })
            .header(AUTHORIZATION, credentials())
            .get()
            .build()
        sinkToFile(request, "download", destination)
        if (destination.length() == 0L) {
            throw CloudBackupException("WebDAV download returned an empty body.")
        }
    }

    override suspend fun list(): List<CloudBackupFile> {
        val request = Request.Builder()
            .url(collectionUrl())
            .header(AUTHORIZATION, credentials())
            .header("Depth", "1")
            .method("PROPFIND", EMPTY_XML.toRequestBody(XML))
            .build()
        val xml = execute(request, "list").toString(Charsets.UTF_8)
        return parsePropfind(xml).filter { it.name.endsWith(EncryptedBackupCipher.FILE_SUFFIX) }
    }

    override suspend fun delete(file: CloudBackupFile) {
        val request = Request.Builder()
            .url(file.id.ifBlank { objectUrl(file.name) })
            .header(AUTHORIZATION, credentials())
            .delete()
            .build()
        execute(request, "delete")
    }

    private fun credentials(): String = Credentials.basic(username, password)

    private fun collectionUrl(): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) throw CloudBackupException("WebDAV URL is missing.")
        return "$trimmed/"
    }

    private fun objectUrl(remoteName: String): String {
        val safe = remoteName.trim().substringAfterLast('/').ifBlank {
            throw CloudBackupException("Remote backup name is missing.")
        }
        return collectionUrl() + safe
    }

    private fun execute(request: Request, action: String): ByteArray {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw CloudBackupException("WebDAV $action failed.", e)
        }
        response.use { result ->
            if (!result.isSuccessful) {
                throw CloudBackupException("WebDAV $action failed (HTTP ${result.code}).")
            }
            return result.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun sinkToFile(request: Request, action: String, destination: File) {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw CloudBackupException("WebDAV $action failed.", e)
        }
        response.use { result ->
            if (!result.isSuccessful) {
                throw CloudBackupException("WebDAV $action failed (HTTP ${result.code}).")
            }
            val body = result.body ?: throw CloudBackupException("WebDAV $action returned an empty body.")
            destination.outputStream().use { output ->
                body.byteStream().copyTo(output)
            }
        }
    }

    /**
     * Minimal PROPFIND href extractor. WebDAV servers emit DAV: or d: prefixes;
     * we only need hrefs that look like backup objects.
     */
    internal fun parsePropfind(xml: String): List<CloudBackupFile> {
        val hrefRegex = Regex("(?i)<(?:[a-z0-9]+:)?href>([^<]+)</(?:[a-z0-9]+:)?href>")
        val getLastModified =
            Regex("(?i)<(?:[a-z0-9]+:)?getlastmodified>([^<]+)</(?:[a-z0-9]+:)?getlastmodified>")
        val hrefs = hrefRegex.findAll(xml).map { it.groupValues[1].trim() }.toList()
        val modified = getLastModified.findAll(xml).map { parseHttpDate(it.groupValues[1].trim()) }.toList()
        val collection = collectionUrl()
        return hrefs.mapIndexedNotNull { index, href ->
            val name = href.trimEnd('/').substringAfterLast('/')
            if (name.isBlank() || href.trimEnd('/') == collection.trimEnd('/')) return@mapIndexedNotNull null
            CloudBackupFile(
                id = if (href.startsWith("http")) href else collectionUrl() + name,
                name = name,
                modifiedAtMillis = modified.getOrNull(index),
            )
        }
    }

    private fun parseHttpDate(raw: String): Long? =
        runCatching { Instant.from(HTTP_DATE.parse(raw)).toEpochMilli() }.getOrNull()

    companion object {
        private const val AUTHORIZATION = "Authorization"
        private val OCTET_STREAM = EncryptedBackupCipher.ENCRYPTED_MIME.toMediaType()
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private const val EMPTY_XML =
            """<?xml version="1.0" encoding="utf-8"?><propfind xmlns="DAV:"><prop><getlastmodified/></prop></propfind>"""
        private val HTTP_DATE = DateTimeFormatter.RFC_1123_DATE_TIME
    }
}

/**
 * Hilt-friendly factory. Credentials are supplied per call so a settings
 * change does not require a process restart.
 */
class WebDavCloudBackupStoreFactory @Inject constructor(
    @Named("webdav") private val client: OkHttpClient,
) {
    fun create(baseUrl: String, username: String, password: String): WebDavCloudBackupStore =
        WebDavCloudBackupStore(client, baseUrl, username, password)
}
