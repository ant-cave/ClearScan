package com.clearscan

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationInstrumentedTest {
    @Test
    fun migration1To2_preservesDocumentsInRoot() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-1-2.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE documents (id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, type TEXT NOT NULL, createdAt INTEGER NOT NULL, sizeBytes INTEGER NOT NULL, pageCount INTEGER NOT NULL, thumbnailPath TEXT NOT NULL, exportPath TEXT NOT NULL)")
            db.execSQL("CREATE TABLE scan_pages (id INTEGER NOT NULL PRIMARY KEY, documentId INTEGER NOT NULL, originalPath TEXT NOT NULL, processedPath TEXT NOT NULL, cropPoints TEXT NOT NULL, filter TEXT NOT NULL, brightness REAL NOT NULL, contrast REAL NOT NULL, saturation REAL NOT NULL, rotation INTEGER NOT NULL)")
            db.execSQL("INSERT INTO documents VALUES (7, 'Existing Scan', 'PDF', 7, 100, 1, '/thumb', '/file')")
            db.version = 1
        }
        val database = Room.databaseBuilder(context, ClearScanDatabase::class.java, name)
            .addMigrations(ClearScanDatabase.MIGRATION_1_2)
            .build()
        val document = database.documentDao().document(7)!!
        assertEquals("Existing Scan", document.title)
        assertNull(document.folderId)
        assertEquals(ScanMode.Document.name, document.scanMode)
        database.close()
        context.deleteDatabase(name)
        Unit
    }

    @Test
    fun migration2To3_resetsDocumentsAndDropsScanPages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-2-3.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE documents (id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, type TEXT NOT NULL, createdAt INTEGER NOT NULL, sizeBytes INTEGER NOT NULL, pageCount INTEGER NOT NULL, thumbnailPath TEXT NOT NULL, exportPath TEXT NOT NULL, folderId INTEGER, scanMode TEXT NOT NULL)")
            db.execSQL("CREATE TABLE scan_pages (id INTEGER NOT NULL PRIMARY KEY, documentId INTEGER NOT NULL, pageIndex INTEGER NOT NULL DEFAULT 0, originalPath TEXT NOT NULL, processedPath TEXT NOT NULL, cropPoints TEXT NOT NULL, filter TEXT NOT NULL, brightness REAL NOT NULL, contrast REAL NOT NULL, saturation REAL NOT NULL, rotation INTEGER NOT NULL, sourceType TEXT NOT NULL, originalWidth INTEGER NOT NULL DEFAULT 0, originalHeight INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("INSERT INTO documents VALUES (9, 'Legacy Image', 'JPG', 9, 200, 2, '/thumb', '/file.jpg', NULL, 'Document')")
            db.version = 2
        }
        val database = Room.databaseBuilder(context, ClearScanDatabase::class.java, name)
            .addMigrations(ClearScanDatabase.MIGRATION_2_3)
            .build()
        // New table starts empty; legacy rows are re-hydrated from files by
        // DocumentStore.migrateLegacyDocuments on first launch, then dropped.
        assertNull(database.documentDao().document(9))
        database.openHelper.writableDatabase
            .query("SELECT name FROM sqlite_master WHERE type='table' AND name='scan_pages'")
            .use { cursor -> assertEquals(false, cursor.moveToFirst()) }
        database.close()
        context.deleteDatabase(name)
        Unit
    }
}
