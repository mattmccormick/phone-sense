package ca.mattmccormick.screenbudget

import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupConfigurationTest {
    @Test
    fun manifestEnablesBackupWithRulesForEverySupportedApi() {
        val document = parseXml("src/main/AndroidManifest.xml")
        val application = document.getElementsByTagName("application").item(0)
        val androidNamespace = "http://schemas.android.com/apk/res/android"

        assertEquals("true", application.attributes.getNamedItemNS(androidNamespace, "allowBackup").nodeValue)
        assertEquals(
            "@xml/backup_rules",
            application.attributes.getNamedItemNS(androidNamespace, "fullBackupContent").nodeValue,
        )
        assertEquals(
            "@xml/data_extraction_rules",
            application.attributes.getNamedItemNS(androidNamespace, "dataExtractionRules").nodeValue,
        )
    }

    @Test
    fun rulesBackUpAppDataAndExcludeWorkManagerDatabase() {
        val legacyRules = parseXml("src/main/res/xml/backup_rules.xml").documentElement
        val extractionRules = parseXml("src/main/res/xml/data_extraction_rules.xml")
        val ruleSets = listOf(
            legacyRules,
            extractionRules.getElementsByTagName("cloud-backup").item(0) as Element,
            extractionRules.getElementsByTagName("device-transfer").item(0) as Element,
        )

        ruleSets.forEach { rules ->
            val includes = rules.childRules("include")
            val excludes = rules.childRules("exclude")

            assertTrue(includes.contains("database" to "."))
            assertTrue(includes.contains("file" to "datastore/settings.preferences_pb"))
            assertTrue(excludes.containsAll(WORK_MANAGER_DATABASE_FILES.map { "database" to it }))
        }
    }

    @Test
    fun roomAutoCloseCheckpointsWalAsDocumentedByBackupRules() {
        val application = ApplicationProvider.getApplicationContext<ScreenBudgetApplication>()
        val autoCloserField = RoomDatabase::class.java.getDeclaredField("autoCloser").apply {
            isAccessible = true
        }

        assertNotNull(autoCloserField.get(application.database))
        listOf("backup_rules.xml", "data_extraction_rules.xml").forEach { fileName ->
            val rules = File("src/main/res/xml/$fileName").readText()
            assertTrue(rules.contains("Room auto-close checkpoints the WAL"))
        }
    }

    private fun parseXml(path: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File(path))

    private fun Element.childRules(tagName: String): Set<Pair<String, String>> =
        (0 until childNodes.length)
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName == tagName }
            .map { it.getAttribute("domain") to it.getAttribute("path") }
            .toSet()

    private companion object {
        val WORK_MANAGER_DATABASE_FILES = listOf(
            "androidx.work.workdb",
            "androidx.work.workdb-shm",
            "androidx.work.workdb-wal",
        )
    }
}
