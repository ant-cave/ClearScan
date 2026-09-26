package com.clearscan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One page of a saved document. `originalPath` is the untouched capture,
 * `processedPath` is the cropped/filtered result and `thumbPath` is the
 * thumbnail of the processed image. Crop/filter parameters allow re-deriving
 * the processed image from the original at any time.
 */
data class StoredPage(
    val id: Long,
    val pageIndex: Int,
    val originalPath: String,
    val processedPath: String,
    val thumbPath: String,
    val cropPoints: String,
    val filter: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val rotation: Int,
    val confidence: Float,
    val width: Int,
    val height: Int,
)

/** Document-level metadata, persisted as metadata.json inside each document folder. */
data class DocumentMeta(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val scanMode: String,
    val pages: List<StoredPage>,
) {
    val firstThumbPath: String? get() = pages.firstOrNull()?.thumbPath
}

/**
 * Storage layout (all under the app-private filesDir):
 *
 *   documents/{docId}/metadata.json
 *   documents/{docId}/{pageId}-original.jpg
 *   documents/{docId}/{pageId}-processed.jpg
 *   documents/{docId}/{pageId}-thumb.jpg
 */
object DocumentStore {
    private const val FLAG = "migrated_to_v3_storage"

    fun documentsRoot(context: Context): File = File(context.filesDir, "documents")

    fun documentDir(context: Context, id: Long): File = File(documentsRoot(context), id.toString())

    fun metaFile(context: Context, id: Long): File = File(documentDir(context, id), "metadata.json")

    fun directorySize(dir: File): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun writeMeta(context: Context, meta: DocumentMeta) {
        val dir = documentDir(context, meta.id).apply { mkdirs() }
        val root = JSONObject()
        root.put("id", meta.id)
        root.put("title", meta.title)
        root.put("createdAt", meta.createdAt)
        root.put("scanMode", meta.scanMode)
        val pages = JSONArray()
        meta.pages.forEach { page ->
            pages.put(
                JSONObject()
                    .put("id", page.id)
                    .put("pageIndex", page.pageIndex)
                    .put("originalPath", page.originalPath)
                    .put("processedPath", page.processedPath)
                    .put("thumbPath", page.thumbPath)
                    .put("cropPoints", page.cropPoints)
                    .put("filter", page.filter)
                    .put("brightness", page.brightness.toDouble())
                    .put("contrast", page.contrast.toDouble())
                    .put("saturation", page.saturation.toDouble())
                    .put("rotation", page.rotation)
                    .put("confidence", page.confidence.toDouble())
                    .put("width", page.width)
                    .put("height", page.height),
            )
        }
        root.put("pages", pages)
        File(dir, "metadata.json").writeText(root.toString())
    }

    fun readMeta(context: Context, id: Long): DocumentMeta? = runCatching {
        val file = metaFile(context, id)
        if (!file.exists()) return null
        val root = JSONObject(file.readText())
        val pagesJson = root.optJSONArray("pages") ?: JSONArray()
        val pages = (0 until pagesJson.length()).mapNotNull { index ->
            val page = pagesJson.getJSONObject(index)
            StoredPage(
                id = page.getLong("id"),
                pageIndex = page.optInt("pageIndex", index),
                originalPath = page.optString("originalPath"),
                processedPath = page.optString("processedPath"),
                thumbPath = page.optString("thumbPath"),
                cropPoints = page.optString("cropPoints"),
                filter = page.optString("filter", "None"),
                brightness = page.optDouble("brightness", 0.0).toFloat(),
                contrast = page.optDouble("contrast", 1.0).toFloat(),
                saturation = page.optDouble("saturation", 1.0).toFloat(),
                rotation = page.optInt("rotation", 0),
                confidence = page.optDouble("confidence", 0.0).toFloat(),
                width = page.optInt("width", 0),
                height = page.optInt("height", 0),
            )
        }
        DocumentMeta(
            id = root.getLong("id"),
            title = root.optString("title"),
            createdAt = root.optLong("createdAt"),
            scanMode = root.optString("scanMode", ScanMode.Document.name),
            pages = pages,
        )
    }.getOrNull()

    fun deleteDocument(context: Context, id: Long) {
        documentDir(context, id).deleteRecursively()
    }

    /**
     * One-time migration from the v2 layout (loose files in filesDir plus a
     * `documents_legacy` SQLite table left behind by MIGRATION_2_3). Image
     * documents are copied into the new per-document folders; PDF documents
     * and their files are dropped entirely. Safe to re-run after a crash:
     * copying is idempotent and the legacy table is only dropped at the end.
     * Must be called after the database is open (MIGRATION_2_3 has run).
     */
    suspend fun migrateLegacyDocuments(context: Context, database: ClearScanDatabase, dao: DocumentDao) {
        val prefs = context.getSharedPreferences("clearscan-settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean(FLAG, false)) return
        runCatching {
            val db = database.openHelper.writableDatabase
            val cursor = db.query("SELECT id, title, type, createdAt, pageCount, folderId, scanMode, thumbnailPath, exportPath FROM documents_legacy")
                cursor.use { rows ->
                    while (rows.moveToNext()) {
                        val id = rows.getLong(0)
                        val title = rows.getString(1) ?: "Untitled Scan"
                        val type = rows.getString(2) ?: "JPG"
                        val createdAt = rows.getLong(3)
                        val pageCount = rows.getInt(4)
                        val folderId = if (rows.isNull(5)) null else rows.getLong(5)
                        val scanMode = rows.getString(6) ?: ScanMode.Document.name
                        val thumbnailPath = rows.getString(7) ?: ""
                        val exportPath = rows.getString(8) ?: ""

                        if (type.equals("PDF", ignoreCase = true)) {
                            // PDF support is fully removed: drop the document and its files.
                            File(exportPath).delete()
                            File(thumbnailPath).delete()
                            continue
                        }

                        val dir = documentDir(context, id).apply { mkdirs() }
                        val pageId = id
                        val original = File(dir, "$pageId-original.jpg")
                        val source = sequenceOf(exportPath, thumbnailPath).map(::File).firstOrNull { it.exists() }
                        if (source == null) continue
                        source.copyTo(original, overwrite = true)
                        val processed = File(dir, "$pageId-processed.jpg")
                        source.copyTo(processed, overwrite = true)
                        val thumb = File(dir, "$pageId-thumb.jpg")
                        if (File(thumbnailPath).exists()) {
                            File(thumbnailPath).copyTo(thumb, overwrite = true)
                        } else {
                            thumb.writeBytes(processed.readBytes())
                        }
                        writeMeta(
                            context,
                            DocumentMeta(
                                id = id,
                                title = title,
                                createdAt = createdAt,
                                scanMode = scanMode,
                                pages = listOf(
                                    StoredPage(
                                        id = pageId,
                                        pageIndex = 0,
                                        originalPath = original.absolutePath,
                                        processedPath = processed.absolutePath,
                                        thumbPath = thumb.absolutePath,
                                        cropPoints = "0,0;1,0;1,1;0,1",
                                        filter = "None",
                                        brightness = 0f,
                                        contrast = 1f,
                                        saturation = 1f,
                                        rotation = 0,
                                        confidence = 0f,
                                        width = 0,
                                        height = 0,
                                    ),
                                ),
                            ),
                        )
                        dao.upsert(
                            Document(
                                id = id,
                                title = title,
                                createdAt = createdAt,
                                pageCount = if (pageCount > 0) pageCount else 1,
                                folderId = folderId,
                                scanMode = scanMode,
                            ),
                        )
                    }
                }
                db.execSQL("DROP TABLE IF EXISTS documents_legacy")
        }
        prefs.edit().putBoolean(FLAG, true).apply()
    }
}
