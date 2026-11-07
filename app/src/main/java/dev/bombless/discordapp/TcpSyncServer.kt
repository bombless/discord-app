package dev.bombless.discordapp

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class TcpSyncServer(
    private val repository: ScriptRepository,
    private val state: RuntimeState,
    private val onScriptActivated: (ActiveScript) -> Unit,
    private val port: Int = 8765
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    @Volatile private var serverSocket: ServerSocket? = null
    val token: String = ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread(name = "script-sync-server", isDaemon = true) {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
                serverSocket = server
                state.serverReady.value = true
                state.statusMessage.value = "TCP sync listening on 127.0.0.1:$port"
                while (running.get()) {
                    val client = server.accept()
                    thread(name = "script-sync-client", isDaemon = true) { handle(client) }
                }
            } catch (error: Exception) {
                if (running.get()) {
                    state.statusMessage.value = "TCP server error: ${error.message}"
                    state.appendLog(error.stackTraceToString())
                }
            } finally {
                state.serverReady.value = false
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 20_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val writer = OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8)
            val line = reader.readLine() ?: return
            val response = try {
                val request = JSONObject(line)
                if (!constantTimeEquals(request.optString("token"), token)) {
                    JSONObject().put("ok", false).put("error", "unauthorized")
                } else execute(request)
            } catch (error: Exception) {
                JSONObject().put("ok", false).put("error", error.message ?: "bad request")
            }
            writer.write(response.toString())
            writer.write("\n")
            writer.flush()
        }
    }

    private fun execute(request: JSONObject): JSONObject = when (request.optString("cmd")) {
        "status", "list" -> statusResponse()
        "push" -> {
            val active = repository.push(request.getString("source"), request.optString("note", "TCP upload"))
            onScriptActivated(active)
            state.refresh(repository)
            statusResponse().put("message", "activated v${active.version}")
        }
        "rollback" -> {
            val active = repository.rollback(request.getInt("version"))
            onScriptActivated(active)
            state.refresh(repository)
            statusResponse().put("message", "rolled back to v${active.version}")
        }
        else -> JSONObject().put("ok", false).put("error", "unknown command")
    }

    private fun statusResponse(): JSONObject {
        val versions = JSONArray()
        repository.listVersions().forEach { item ->
            versions.put(JSONObject()
                .put("version", item.version)
                .put("sha256", item.sha256)
                .put("createdAt", item.createdAt)
                .put("note", item.note))
        }
        return JSONObject()
            .put("ok", true)
            .put("activeVersion", repository.activeVersion())
            .put("versions", versions)
            .put("port", port)
    }

    private fun constantTimeEquals(candidate: String, expected: String): Boolean =
        MessageDigest.isEqual(candidate.toByteArray(StandardCharsets.UTF_8),
            expected.toByteArray(StandardCharsets.UTF_8))

    override fun close() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        state.serverReady.value = false
    }
}
