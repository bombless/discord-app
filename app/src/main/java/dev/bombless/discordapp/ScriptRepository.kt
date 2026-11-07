package dev.bombless.discordapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class ScriptVersion(val version: Int, val sha256: String, val createdAt: Long, val note: String)
data class ActiveScript(val version: Int, val source: String, val sha256: String)

class ScriptRepository(context: Context) {
    private val root = File(context.filesDir, "script-store")
    private val versionsDir = File(root, "versions")
    private val indexFile = File(root, "index.json")

    init { versionsDir.mkdirs() }

    @Synchronized
    fun ensureSeeded(initialSource: String) {
        if (indexFile.exists()) return
        require(initialSource.toByteArray(Charsets.UTF_8).size <= MAX_SCRIPT_BYTES)
        val hash = sha256(initialSource)
        writeVersionFile(1, initialSource)
        val index = JSONObject().put("activeVersion", 1).put(
            "versions",
            JSONArray().put(
                JSONObject()
                    .put("version", 1)
                    .put("sha256", hash)
                    .put("createdAt", System.currentTimeMillis())
                    .put("note", "Bundled starter script")
            )
        )
        writeIndex(index)
    }

    @Synchronized
    fun active(): ActiveScript {
        val version = readIndex().getInt("activeVersion")
        val file = versionFile(version)
        check(file.isFile) { "Active script v$version is missing" }
        val source = file.readText(Charsets.UTF_8)
        return ActiveScript(version, source, sha256(source))
    }

    @Synchronized
    fun listVersions(): List<ScriptVersion> {
        val array = readIndex().optJSONArray("versions") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            ScriptVersion(item.optInt("version"), item.optString("sha256"),
                item.optLong("createdAt"), item.optString("note"))
        }.sortedByDescending { it.version }
    }

    @Synchronized
    fun push(source: String, note: String = "TCP upload"): ActiveScript {
        require(source.toByteArray(Charsets.UTF_8).size <= MAX_SCRIPT_BYTES) {
            "Script exceeds the 1 MiB limit"
        }
        val index = readIndex()
        val versions = index.optJSONArray("versions") ?: JSONArray()
        val next = (0 until versions.length())
            .mapNotNull { versions.optJSONObject(it)?.optInt("version") }
            .maxOrNull()?.plus(1) ?: 1
        val hash = sha256(source)
        writeVersionFile(next, source)
        versions.put(JSONObject()
            .put("version", next)
            .put("sha256", hash)
            .put("createdAt", System.currentTimeMillis())
            .put("note", note.take(120)))
        index.put("versions", versions).put("activeVersion", next)
        writeIndex(index)
        return ActiveScript(next, source, hash)
    }

    @Synchronized
    fun rollback(version: Int): ActiveScript {
        val index = readIndex()
        require(listVersions().any { it.version == version }) { "Unknown script version: $version" }
        require(versionFile(version).isFile) { "Script file for v$version is missing" }
        index.put("activeVersion", version)
        writeIndex(index)
        return active()
    }

    @Synchronized
    fun activeVersion(): Int = readIndex().getInt("activeVersion")

    private fun readIndex() = JSONObject(indexFile.readText(Charsets.UTF_8))

    private fun writeVersionFile(version: Int, source: String) {
        val target = versionFile(version)
        val temp = File(versionsDir, "v$version.js.tmp")
        FileOutputStream(temp).use {
            it.write(source.toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        check(temp.renameTo(target)) { "Unable to save script v$version" }
    }

    private fun writeIndex(index: JSONObject) {
        val temp = File(root, "index.json.tmp")
        FileOutputStream(temp).use {
            it.write(index.toString(2).toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        if (indexFile.exists()) indexFile.delete()
        check(temp.renameTo(indexFile)) { "Unable to update version index" }
    }

    private fun versionFile(version: Int) = File(versionsDir, "v$version.js")

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object { const val MAX_SCRIPT_BYTES = 1024 * 1024 }
}
