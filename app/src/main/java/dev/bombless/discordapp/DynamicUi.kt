package dev.bombless.discordapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

data class UiNode(
    val type: String,
    val props: JSONObject,
    val events: Map<String, Int>,
    val children: List<UiNode>,
    val id: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): UiNode {
            val eventJson = json.optJSONObject("events") ?: JSONObject()
            val events = buildMap {
                val keys = eventJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, eventJson.optInt(key))
                }
            }
            val array = json.optJSONArray("children") ?: JSONArray()
            val children = (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::fromJson) }
            return UiNode(json.optString("type", "text"),
                json.optJSONObject("props") ?: JSONObject(), events, children, json.optString("id"))
        }
    }
}

@Composable
fun DynamicNode(node: UiNode, dispatch: (Int, Any?) -> Unit, modifier: Modifier = Modifier) {
    when (node.type) {
        "column" -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            node.children.forEach { DynamicNode(it, dispatch) }
        }
        "row" -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            node.children.forEach { DynamicNode(it, dispatch, Modifier.weight(1f)) }
        }
        "text" -> Text(node.props.optString("text"), modifier,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = node.props.optDouble("fontSize", 16.0).toFloat().sp)
        "button" -> Button(modifier = modifier, onClick = {
            node.events["click"]?.let { dispatch(it, null) }
        }) {
            Text(node.props.optString("label", node.props.optString("text", "Button")))
        }
        "input" -> {
            var value by remember(node.id) { mutableStateOf(node.props.optString("value")) }
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    node.events["change"]?.let { callback -> dispatch(callback, it) }
                },
                modifier = modifier.fillMaxWidth(),
                label = { Text(node.props.optString("placeholder", "Input")) },
                singleLine = node.props.optBoolean("singleLine", true)
            )
        }
        "card" -> Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                node.children.forEach { DynamicNode(it, dispatch) }
            }
        }
        "spacer" -> Spacer(modifier.width(node.props.optDouble("width", 0.0).toFloat().dp)
            .height(node.props.optDouble("height", 12.0).toFloat().dp))
        else -> Text("Unsupported component: ${node.type}", modifier = modifier)
    }
}
