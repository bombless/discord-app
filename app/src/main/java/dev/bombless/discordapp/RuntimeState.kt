package dev.bombless.discordapp

import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

class RuntimeState {
    val tree = MutableStateFlow(
        UiNode("text", JSONObject().put("text", "Loading JavaScript runtime…"), emptyMap(), emptyList())
    )
    val activeVersion = MutableStateFlow(0)
    val versions = MutableStateFlow<List<ScriptVersion>>(emptyList())
    val serverReady = MutableStateFlow(false)
    val pairingToken = MutableStateFlow("")
    val statusMessage = MutableStateFlow("Starting…")
    val lastLog = MutableStateFlow("")

    fun refresh(repository: ScriptRepository) {
        activeVersion.value = repository.activeVersion()
        versions.value = repository.listVersions()
    }

    fun appendLog(message: String) { lastLog.value = message.take(500) }
}
