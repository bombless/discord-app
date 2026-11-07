package dev.bombless.discordapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var repository: ScriptRepository
    private lateinit var runtimeState: RuntimeState
    private lateinit var scriptRuntime: ScriptRuntime
    private lateinit var syncServer: TcpSyncServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = ScriptRepository(applicationContext)
        val starter = assets.open("main.js").bufferedReader().use { it.readText() }
        repository.ensureSeeded(starter)

        runtimeState = RuntimeState()
        runtimeState.refresh(repository)
        scriptRuntime = ScriptRuntime(this, repository, runtimeState)
        syncServer = TcpSyncServer(repository, runtimeState, scriptRuntime::apply)
        runtimeState.pairingToken.value = syncServer.token
        syncServer.start()

        setContent {
            val activeVersion by runtimeState.activeVersion.collectAsState()
            val versions by runtimeState.versions.collectAsState()
            val tree by runtimeState.tree.collectAsState()
            val token by runtimeState.pairingToken.collectAsState()
            val serverReady by runtimeState.serverReady.collectAsState()
            val status by runtimeState.statusMessage.collectAsState()
            val lastLog by runtimeState.lastLog.collectAsState()
            val scope = rememberCoroutineScope()

            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Discord App", style = MaterialTheme.typography.headlineSmall)
                                Text("Live JavaScript Runtime", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                if (serverReady) "● ONLINE" else "○ OFFLINE",
                                color = if (serverReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("ACTIVE SCRIPT", style = MaterialTheme.typography.labelMedium)
                                Text("main.js · v$activeVersion", style = MaterialTheme.typography.titleLarge)
                                Text("SHA-256: ${versions.firstOrNull { it.version == activeVersion }?.sha256?.take(16) ?: "pending"}…",
                                    style = MaterialTheme.typography.bodySmall)
                                Text(status, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("TCP SCRIPT SYNC", style = MaterialTheme.typography.labelMedium)
                                Text("127.0.0.1:8765", style = MaterialTheme.typography.titleMedium)
                                Text("adb forward tcp:8765 tcp:8765", style = MaterialTheme.typography.bodyMedium)
                                Text("Pairing token", style = MaterialTheme.typography.labelSmall)
                                Text(token, style = MaterialTheme.typography.bodySmall)
                                Text("Loopback only. Use this token with tools/sync.py.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        HorizontalDivider()
                        Text("SCRIPT-RENDERED UI", style = MaterialTheme.typography.labelMedium)
                        DynamicNode(tree, scriptRuntime::dispatch)

                        if (lastLog.isNotBlank()) {
                            Text("Runtime log: $lastLog", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }

                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("VERSION HISTORY", style = MaterialTheme.typography.titleMedium)
                            Text("${versions.size} revisions", style = MaterialTheme.typography.bodySmall)
                        }

                        versions.forEach { version ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("v${version.version}" + if (version.version == activeVersion) " · ACTIVE" else "")
                                        Text(version.note, style = MaterialTheme.typography.bodySmall)
                                        Text(version.sha256.take(16), style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (version.version != activeVersion) {
                                        Button(onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val restored = repository.rollback(version.version)
                                                scriptRuntime.apply(restored)
                                                runtimeState.refresh(repository)
                                            }
                                        }) { Text("Restore") }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("One editable file: main.js · Uploads create immutable revisions; restore switches the active revision.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)

                        AndroidView(
                            factory = { scriptRuntime.webView },
                            modifier = Modifier.size(1.dp).alpha(0.01f)
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::syncServer.isInitialized) syncServer.close()
        if (::scriptRuntime.isInitialized) scriptRuntime.close()
        super.onDestroy()
    }
}
