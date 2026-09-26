package com.clearscan

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

enum class ScanMode { Document, Book, IdCard }

/**
 * Basic document info lives in SQLite; everything else (per-page crop/filter
 * state and image paths) lives in the per-document metadata.json managed by
 * [DocumentStore]. Folder `documents_legacy` is a v2 leftover kept only while
 * the one-time file migration runs on first launch.
 */
@Entity(tableName = "documents")
data class Document(
    @PrimaryKey val id: Long,
    val title: String,
    val createdAt: Long,
    val pageCount: Int,
    /** Cached first-page thumbnail path for list rendering; pages live in metadata.json. */
    val thumbnailPath: String = "",
    /** Cached total size of the document folder in bytes. */
    val sizeBytes: Long = 0,
    val folderId: Long? = null,
    val scanMode: String = ScanMode.Document.name,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val parentId: Long? = null,
    val createdAt: Long,
)

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey val id: Long,
    val mode: String,
    val createdAt: Long,
    val updatedAt: Long,
    val state: String = "CAPTURING",
)

@Entity(tableName = "draft_scan_pages")
data class DraftScanPageEntity(
    @PrimaryKey val id: Long,
    val sessionId: Long,
    val pageIndex: Int,
    val originalPath: String,
    val processedPath: String = "",
    val thumbnailPath: String,
    val cropPoints: String,
    val confidence: Float,
    val rotation: Int = 0,
    val sourceType: String,
)

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun document(id: Long): Document?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: Document)

    @Query("UPDATE documents SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE documents SET folderId = :folderId WHERE id = :id")
    suspend fun moveDocument(id: Long, folderId: Long?)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("UPDATE folders SET parentId = :parentId WHERE id = :id")
    suspend fun moveFolder(id: Long, parentId: Long?)

    @Query("UPDATE documents SET folderId = :parentId WHERE folderId = :folderId")
    suspend fun moveFolderDocumentsToParent(folderId: Long, parentId: Long?)

    @Query("UPDATE folders SET parentId = :parentId WHERE parentId = :folderId")
    suspend fun moveChildFoldersToParent(folderId: Long, parentId: Long?)

    @Query("SELECT * FROM scan_sessions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestSession(): ScanSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ScanSessionEntity)

    @Query("DELETE FROM scan_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM draft_scan_pages WHERE sessionId = :sessionId ORDER BY pageIndex")
    suspend fun draftPages(sessionId: Long): List<DraftScanPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraftPage(page: DraftScanPageEntity)

    @Query("DELETE FROM draft_scan_pages WHERE id = :id")
    suspend fun deleteDraftPage(id: Long)

    @Query("DELETE FROM draft_scan_pages WHERE sessionId = :sessionId")
    suspend fun deleteDraftPages(sessionId: Long)
}

@Database(
    entities = [Document::class, FolderEntity::class, ScanSessionEntity::class, DraftScanPageEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class ClearScanDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN folderId INTEGER")
                db.execSQL("ALTER TABLE documents ADD COLUMN scanMode TEXT NOT NULL DEFAULT 'Document'")
                db.execSQL("ALTER TABLE scan_pages ADD COLUMN pageIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scan_pages ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'Document'")
                db.execSQL("ALTER TABLE scan_pages ADD COLUMN originalWidth INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scan_pages ADD COLUMN originalHeight INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS folders (id INTEGER NOT NULL, name TEXT NOT NULL, parentId INTEGER, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS scan_sessions (id INTEGER NOT NULL, mode TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, state TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS draft_scan_pages (id INTEGER NOT NULL, sessionId INTEGER NOT NULL, pageIndex INTEGER NOT NULL, originalPath TEXT NOT NULL, processedPath TEXT NOT NULL, thumbnailPath TEXT NOT NULL, cropPoints TEXT NOT NULL, confidence REAL NOT NULL, rotation INTEGER NOT NULL, sourceType TEXT NOT NULL, PRIMARY KEY(id))")
            }
        }

        /**
         * v3 keeps only basic document info in SQLite. The v2 table is renamed
         * to `documents_legacy` so the ViewModel can copy image files into the
         * new per-document folders (PDF documents are dropped) before dropping
         * it. Per-page rows move into each document's metadata.json.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents RENAME TO documents_legacy")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS documents (id INTEGER NOT NULL, title TEXT NOT NULL, createdAt INTEGER NOT NULL, pageCount INTEGER NOT NULL, thumbnailPath TEXT NOT NULL DEFAULT '', sizeBytes INTEGER NOT NULL DEFAULT 0, folderId INTEGER, scanMode TEXT NOT NULL DEFAULT 'Document', PRIMARY KEY(id))",
                )
                db.execSQL("DROP TABLE IF EXISTS scan_pages")
            }
        }
    }
}
