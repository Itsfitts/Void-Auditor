package com.kuzyamond.adbstudio

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kuzyamond.adbstudio.core.ShizukuExecutor
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(val role: String, val text: String, val riskLevel: String? = null)

enum class RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN
}

fun parseRiskLevel(text: String): RiskLevel {
    val upper = text.uppercase()
    return when {
        upper.contains("CRITICAL") || false -> RiskLevel.CRITICAL
        upper.contains("HIGH") || false -> RiskLevel.HIGH
        upper.contains("MEDIUM") || false -> RiskLevel.MEDIUM
        upper.contains("LOW") || false -> RiskLevel.LOW
        else -> RiskLevel.UNKNOWN
    }
}

fun getRiskColor(level: RiskLevel): Color {
    return when (level) {
        RiskLevel.CRITICAL -> Color(0xFFFF2D55)   // Red
        RiskLevel.HIGH     -> Color(0xFFFF9500)   // Orange
        RiskLevel.MEDIUM   -> Color(0xFFFFCC00)   // Yellow
        RiskLevel.LOW      -> CyberAccent         // Acid lime
        else               -> CyberText
    }
}

val availableModels = listOf(
    "gemini-2.0-flash-lite" to "FLASH_LITE",
    "gemini-2.0-flash" to "FLASH",
    "gemini-1.5-flash" to "1.5_FLASH"
)

val systemInstruction = """
You are a certified Senior Android Security & Digital Forensics expert (Red/Blue Team, 10+ years of experience).
Specialization: Shizuku, ADB, pm/am/dumpsys, SELinux, Accessibility Abuse, Banking Trojan detection, IOC hunting, DevSecOps.

Response rules (must strictly follow):
- Start strictly with **RISK LEVEL**: Low / Medium / High / Critical
- Clearly highlight **found IOCs** (suspicious permissions, services, packages, behavior)
- For Azerbaijani banking apps (az.unibank, az.dpc.sima, etc.) — pay extra attention
- Always provide ready-to-use commands for ADB Studio (SHELL / SCRIPT_EXECUTOR)
- Suggest automated scripts and hardening recommendations
- Reply in English, structured, in a professional cyber style
""".trimIndent()

@Composable
fun AIAssistantScreen(scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val prefs = remember { context.getSharedPreferences("adb_studio_ai", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString("gemini_key", "") ?: "") }
    var selectedModel by remember { mutableStateOf(prefs.getString("gemini_model", "gemini-2.0-flash-lite") ?: "gemini-2.0-flash-lite") }
    var showKeyInput by remember { mutableStateOf(apiKey.isEmpty()) }
    var inputText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(loadMessages(prefs)) }
    var isProcessing by remember { mutableStateOf(false) }
    var retryCooldown by remember { mutableStateOf(0) }
    var showSaveDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        ShizukuExecutor.init()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LaunchedEffect(retryCooldown) {
        if (retryCooldown > 0) { delay(1000); retryCooldown-- }
    }

    fun saveKey(key: String) { prefs.edit().putString("gemini_key", key.trim()).apply() }
    fun saveModel(model: String) { prefs.edit().putString("gemini_model", model).apply() }
    fun saveMessages() {
        val json = JSONArray().apply {
            messages.forEach { put(JSONObject().apply { put("r", it.role); put("t", it.text); it.riskLevel?.let { l -> put("rl", l) } }) }
        }.toString()
        prefs.edit().putString("chat_history", json).apply()
    }
    fun clearChat() { messages = emptyList(); prefs.edit().remove("chat_history").apply() }

    fun parseGeminiResponse(body: String): String? {
        return try {
            val obj = JSONObject(body)
            val candidates = obj.optJSONArray("candidates") ?: return null
            val c = candidates.optJSONObject(0) ?: return null
            val content = c.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            parts.optJSONObject(0)?.optString("text", null)
        } catch (_: Exception) { null }
    }

    suspend fun askGemini(prompt: String, extraContext: String = "") {
        if (apiKey.isBlank() || prompt.isBlank()) return
        val fullPrompt = if (extraContext.isNotBlank()) "$prompt\n\n=== CONTEXT ===\n$extraContext" else prompt
        messages = messages + ChatMessage("user", prompt)
        isProcessing = true
        inputText = ""

        val result = withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val body = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", "$systemInstruction\n\nUser query: $fullPrompt") })
                            })
                        })
                    })
                }.toString()

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:generateContent?key=$apiKey")
                conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "POST"; setRequestProperty("Content-Type", "application/json")
                    doOutput = true; connectTimeout = 45000; readTimeout = 90000
                }
                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().readText()
                    parseGeminiResponse(resp) ?: "PARSE_ERROR"
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "{}"
                    if (conn.responseCode == 429) {
                        val sec = Regex("""(\d+)s""").find(err)?.groupValues?.get(1)?.toIntOrNull() ?: 60
                        retryCooldown = sec.coerceIn(5, 120)
                        "QUOTA_EXCEEDED — retry in ${retryCooldown}s"
                    } else {
                        val msg = try { JSONObject(err).optJSONObject("error")?.optString("message", "") ?: err } catch (_: Exception) { err }
                        "API_ERR_${conn.responseCode}: ${msg.take(300)}"
                    }
                }
            } catch (e: Exception) { "NETWORK_ERR: ${e.localizedMessage ?: e.message}" }
            finally { conn?.disconnect() }
        }

        val riskLevel = Regex("""RISK[_\s]LEVEL[:\s]*(Low|Medium|High|Critical)""", RegexOption.IGNORE_CASE)
            .find(result)?.groupValues?.get(1)?.replaceFirstChar { it.uppercase() }
        messages = messages + ChatMessage("assistant", result, riskLevel)
        isProcessing = false
        saveMessages()

        if (retryCooldown > 0) {
            delay(retryCooldown * 1000L + 1000L)
            retryCooldown = 0
            askGemini(prompt, extraContext)
        }
    }

    suspend fun performDeviceAudit() {
        isProcessing = true

        val auditCommands = listOf(
            "getprop ro.build.description",
            "getprop ro.product.model",
            "getprop ro.build.version.release",
            "getprop ro.build.date",
            "dumpsys battery | grep -E 'level|health|status|temperature|plugged|present'",
            "dumpsys accessibility",
            "id && getenforce",
            "pm list packages -3 | grep -E 'bank|finance|gov|proton|unibank|sima|az\\.'",
            "dumpsys deviceidle whitelist",
            "settings get global adb_wifi_enabled",
            "settings get global adb_authorization_timeout"
        )

        val results = ShizukuExecutor.executeBatch(auditCommands)

        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

        val auditReport = buildString {
            appendLine("=== DEVICE SECURITY AUDIT REPORT ===")
            appendLine("Timestamp: $timestamp")
            appendLine("Device Model: ${results.getOrNull(1)?.output ?: "Unknown"}\n")

            results.forEachIndexed { i, res ->
                appendLine("[Command ${i + 1}] ${auditCommands[i].take(65)}")
                appendLine(res.fullOutput.ifBlank { "<empty>" })
                appendLine("\u2500".repeat(60))
            }
        }

        val prompt = """
            FULL DEVICE SECURITY AUDIT
            
            $auditReport
        """.trimIndent()

        // Auto-save audit report to file
        try {
            val dir = File(Environment.getExternalStorageDirectory(), "ADB_Studio_Logs")
            if (!dir.exists()) dir.mkdirs()
            val auditFile = File(dir, "audit_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt")
            FileWriter(auditFile).use { it.write(auditReport) }
            ShizukuExecutor.logListener?.invoke("INFO", "Audit report auto-saved: ${auditFile.name}")
        } catch (e: Exception) {
            ShizukuExecutor.logListener?.invoke("ERROR", "Auto-save audit failed: ${e.message}")
        }

        askGemini(prompt)
        showSaveDialog = true
    }

    suspend fun analyzeLogs() {
        isProcessing = true

        val recentLogs = GlobalLog.getRecentLogs(50)

        askGemini("""
            ANALYZE RECENT LOGS + IOC SEARCH
            
            === RECENT LOGS FROM ADB STUDIO ===
            $recentLogs
        """.trimIndent())
    }

    // ====================== BANKING DEEP SCAN ======================
    // ====================== BANKING DEEP SCAN v2.0 ======================
    suspend fun performBankingDeepScan() {
        isProcessing = true
        messages = messages + ChatMessage("user", "🔴 BANKING DEEP SCAN v2.0 — Extended Threat Analysis")

        val bankingPackages = listOf(
            "az.unibank.mbanking",
            "az.dpc.sima",
            "az.gov.my",
            "iba.mobilbank",
            "ch.protonmail.android",
            "ch.protonvpn.android"
        )

        val scanReport = buildString {
            appendLine("=== BANKING DEEP SCAN v2.0 ===")
            appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Target: High-Value Financial & Secure Apps\n")

            bankingPackages.forEach { pkg ->
                appendLine("[$pkg]")
                val result = ShizukuExecutor.executeCommand("dumpsys package $pkg")

                if (result.isSuccessful) {
                    val output = result.output

                    // Basic info
                    val version = output.lines().firstOrNull { it.contains("versionName") }?.substringAfter("versionName=") ?: "N/A"
                    val installer = output.lines().firstOrNull { it.contains("installerPackageName") }?.substringAfter("installerPackageName=") ?: "N/A"
                    
                    appendLine("• Version     : $version")
                    appendLine("• Installer   : $installer")

                    // Dangerous permissions
                    val dangerousPerms = listOf("SMS", "CALL_LOG", "CONTACTS", "ACCESSIBILITY", "SYSTEM_ALERT_WINDOW", "READ_PHONE_STATE", "CAMERA", "RECORD_AUDIO")
                    val foundPerms = dangerousPerms.filter { output.contains(it, ignoreCase = true) }
                    appendLine("• Dangerous Permissions : ${if (foundPerms.isNotEmpty()) foundPerms.joinToString(", ") + " ⚠" else "None"}")

                    // Exported components
                    if (output.contains("exported=true")) {
                        appendLine("• Exported Activities/Services : FOUND (High Attack Surface)")
                    }

                    // WebView check
                    if (output.contains("WebView", ignoreCase = true)) {
                        appendLine("• WebView detected — check for mixed content / JS enabled")
                    }

                    // Deep Links / Schemes
                    if (output.contains("android.intent.category.BROWSABLE") || output.contains("scheme")) {
                        appendLine("• Deep Links / Custom Schemes : DETECTED")
                    }

                    // Background services
                    if (output.contains("Service") && output.contains("persistent")) {
                        appendLine("• Persistent background services found")
                    }
                } else {
                    appendLine("• Package not found or permission denied")
                }
                appendLine("─".repeat(70))
            }
        }

        val prompt = """
            BANKING DEEP SCAN v2.0 — Extended Threat Analysis
            
            $scanReport
            
            Analyze risks, find IOCs and give specific protection recommendations.
        """.trimIndent()

        askGemini(prompt)
        showSaveDialog = true
    }

    // ====================== EXPORT REPORT ======================
    // ====================== EXPORT REPORT (FIXED) ======================
    fun exportCurrentReport() {
        if (messages.isEmpty()) {
            Toast.makeText(context, "No data to save", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "Audit_Report_$timestamp.txt"

        val reportContent = buildString {
            appendLine("ADB Studio Security Audit Report")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Device: SM-A115F")
            appendLine("=".repeat(80))
            appendLine()

            messages.forEach { msg ->
                val role = if (msg.role == "user") "USER QUERY" else "AI RESPONSE"
                appendLine("[$role]")
                appendLine(msg.text)
                appendLine("-".repeat(80))
                appendLine()
            }
        }

        try {
            // New reliable place - Downloads (works stably)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(downloadsDir, "ADB_Studio_Logs")
            if (!appDir.exists()) appDir.mkdirs()

            val file = File(appDir, filename)
            
            FileWriter(file).use { writer ->
                writer.write(reportContent)
            }

            Toast.makeText(
                context,
                "✅ Report saved!\nDownloads/ADB_Studio_Logs/$filename",
                Toast.LENGTH_LONG
            ).show()

            ShizukuExecutor.logListener?.invoke("SUCCESS", "Report saved: $filename")

        } catch (e: Exception) {
            Toast.makeText(
                context,
                "❌ Save error: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
            ShizukuExecutor.logListener?.invoke("ERROR", "Export failed: ${e.message}")
        }
    }

    // ====================== UI ======================
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = CyberSurface,
            titleContentColor = CyberAccent,
            textContentColor = CyberText,
            title = { Text("SAVE REPORT?", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
            text = { Text("Audit successfully completed. Do you want to export the full report to a file?", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = { 
                        exportCurrentReport()
                        showSaveDialog = false 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberAccent, contentColor = CyberBackground),
                    shape = RoundedCornerShape(2.dp)
                ) { Text("YES", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("NO", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.border(1.dp, CyberAccent)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (showKeyInput) {
            CyberCard(title = "GEMINI_API_SETUP", color = CyberAccent, modifier = Modifier.padding(bottom = 10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Gemini API key (get at aistudio.google.com):", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("AIza...", color = Color.Gray, fontSize = 11.sp) },
                        textStyle = TextStyle(color = CyberInfo, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberAccent, unfocusedBorderColor = CyberBorder,
                            focusedContainerColor = CyberBackground, unfocusedContainerColor = CyberBackground
                        )
                    )
                    Button(
                        onClick = { saveKey(apiKey); showKeyInput = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberAccent, contentColor = CyberBackground),
                        shape = RoundedCornerShape(2.dp)
                    ) { Text("SAVE_KEY", fontWeight = FontWeight.Bold) }
                }
            }
            return
        }

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                availableModels.forEach { (model, label) ->
                    val active = selectedModel == model
                    Surface(
                        modifier = Modifier.height(24.dp).border(1.dp, if (active) CyberAccent else CyberBorder).clickable { selectedModel = model; saveModel(model) },
                        color = if (active) CyberAccent.copy(alpha = 0.15f) else Color.Transparent, shape = RoundedCornerShape(2.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text(label, color = if (active) CyberAccent else Color(0xFF64748B), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { clearChat() }) { Text("CLR", color = Color(0xFF64748B), fontSize = 9.sp) }
                TextButton(onClick = { showKeyInput = true }) { Text("KEY", color = Color(0xFF64748B), fontSize = 9.sp) }
            }
        }

        // Messages
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, CyberBorder),
            state = listState, contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text("AI_DEVICE_ASSISTANT", color = CyberInfo, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    Text("Ask about device issues, app optimization, ADB commands.\nExample: \"why isn't Element X getting notifications?\"",
                        color = Color.Gray, fontSize = 10.sp, lineHeight = 16.sp)
                }
            }
            items(messages) { msg ->
                val isUser = msg.role == "user"
                val riskLevel = if (!isUser) parseRiskLevel(msg.text) else RiskLevel.UNKNOWN
                val bgColor = if (isUser) CyberSurface else CyberBackground
                val accentColor = if (isUser) CyberInfo else getRiskColor(riskLevel)

                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        clipboardManager.setText(AnnotatedString(msg.text))
                    },
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isUser) "YOU" else "VOID AI",
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (!isUser && riskLevel != RiskLevel.UNKNOWN) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = when(riskLevel) {
                                            RiskLevel.CRITICAL -> "☢ CRITICAL"
                                            RiskLevel.HIGH -> "⚠ HIGH"
                                            RiskLevel.MEDIUM -> "⚡ MEDIUM"
                                            else -> "✓ LOW"
                                        },
                                        color = accentColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Text("COPY", color = Color(0xFF475569), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = msg.text,
                            color = CyberText,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            if (isProcessing) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = CyberAccent2, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text("AI_THINKING...", color = CyberAccent2, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (retryCooldown > 0) {
                item { Text("⏳ RETRY_IN ${retryCooldown}s", color = CyberAccent2, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(8.dp)) }
            }
        }

        // Action buttons
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = { scope.launch { performDeviceAudit() } }, enabled = !isProcessing,
                modifier = Modifier.weight(1f).height(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberAccent2.copy(alpha = 0.15f), contentColor = CyberAccent2),
                shape = RoundedCornerShape(2.dp), contentPadding = PaddingValues(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberAccent2.copy(alpha = 0.5f))
            ) { Icon(Icons.Default.Shield, null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("AUDIT", fontSize = 8.sp, fontWeight = FontWeight.Bold) }

            Button(
                onClick = { scope.launch { performBankingDeepScan() } }, enabled = !isProcessing,
                modifier = Modifier.weight(1f).height(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberWarning.copy(alpha = 0.15f), contentColor = CyberWarning),
                shape = RoundedCornerShape(2.dp), contentPadding = PaddingValues(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberWarning.copy(alpha = 0.5f))
            ) { Icon(Icons.Default.Security, null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("BANK SCAN", fontSize = 8.sp, fontWeight = FontWeight.Bold) }

            Button(
                onClick = { scope.launch { analyzeLogs() } }, enabled = !isProcessing,
                modifier = Modifier.weight(1f).height(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberInfo.copy(alpha = 0.15f), contentColor = CyberInfo),
                shape = RoundedCornerShape(2.dp), contentPadding = PaddingValues(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberInfo.copy(alpha = 0.5f))
            ) { Icon(Icons.Default.Analytics, null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("LOGS", fontSize = 8.sp, fontWeight = FontWeight.Bold) }

            Button(
                onClick = { exportCurrentReport() }, enabled = messages.isNotEmpty(),
                modifier = Modifier.weight(1f).height(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberAccent2.copy(alpha = 0.15f), contentColor = CyberAccent2),
                shape = RoundedCornerShape(2.dp), contentPadding = PaddingValues(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberAccent2.copy(alpha = 0.5f))
            ) { Icon(Icons.Default.Save, null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("EXPORT", fontSize = 8.sp, fontWeight = FontWeight.Bold) }
        }

        // Input
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = inputText, onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("ASK_AI...", color = Color.Gray, fontSize = 11.sp) },
                textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberAccent, unfocusedBorderColor = CyberBorder, focusedContainerColor = CyberBackground, unfocusedContainerColor = CyberBackground),
                enabled = !isProcessing && retryCooldown == 0
            )
            IconButton(
                onClick = { scope.launch { askGemini(inputText.trim()) } },
                modifier = Modifier.size(48.dp).border(1.dp, if (retryCooldown > 0) CyberAccent2 else CyberAccent).background(CyberSurface),
                enabled = !isProcessing && apiKey.isNotBlank() && retryCooldown == 0
            ) {
                if (retryCooldown > 0) Text("$retryCooldown", color = CyberAccent2, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                else Icon(Icons.Default.Send, null, tint = if (isProcessing) Color.Gray else CyberAccent)
            }
        }
    }
}

fun loadMessages(prefs: android.content.SharedPreferences): List<ChatMessage> {
    val raw = prefs.getString("chat_history", "") ?: ""
    if (raw.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.getJSONObject(i)
            ChatMessage(obj.getString("r"), obj.getString("t"), obj.optString("rl", null))
        }
    } catch (_: Exception) { emptyList() }
}

// ====================== RISK AGGREGATOR ======================
data class SecurityIssue(val title: String, val severity: RiskLevel, val fix: String)
data class SecuritySummary(val level: RiskLevel, val score: Int, val issues: List<SecurityIssue>)

fun generateSecuritySummary(auditReport: String, bankingReport: String = ""): SecuritySummary {
    val issues = mutableListOf<SecurityIssue>()
    var score = 0

    fun add(condition: Boolean, title: String, sev: RiskLevel, fix: String) {
        if (condition) {
            issues.add(SecurityIssue(title, sev, fix))
            score += when(sev) {
                RiskLevel.CRITICAL -> 40
                RiskLevel.HIGH -> 25
                RiskLevel.MEDIUM -> 15
                RiskLevel.LOW -> 5
                RiskLevel.UNKNOWN -> 0
            }
        }
    }

    val report = auditReport.uppercase()

    add(report.contains("ADB_WIFI_ENABLED=1"), "ADB Wi-Fi is enabled", RiskLevel.HIGH,
        "Settings → Developer options → Wireless debugging → OFF")
    add(report.contains("ADB_WIFI_ENABLED=0").not() && report.contains("ADB_WIFI_ENABLED"),
        "ADB Wi-Fi status undefined", RiskLevel.LOW,
        "Check: settings get global adb_wifi_enabled")

    add(report.contains("ACCESSIBILITY") && (report.contains("SERVICE") || report.contains("SERVICES")),
        "Accessibility services are active", RiskLevel.CRITICAL,
        "Settings → Accessibility → Disable unnecessary services")

    add(report.contains("ENFORCING").not() && report.contains("SELINUX"),
        "SELinux is not in Enforcing mode", RiskLevel.CRITICAL,
        "Run: setenforce 1 (requires root)")

    add(report.contains("UNIBANK") || report.contains("SIMA") || report.contains("AZ."),
        "Azerbaijani banking apps detected", RiskLevel.HIGH,
        "Run BANK SCAN for full permission check")

    add(report.contains("INSTALL_NON_MARKET_APPS=1"),
        "Installation from unknown sources is allowed", RiskLevel.HIGH,
        "Settings → Security → Unknown sources → DENY")

    add(report.contains("WHITELIST") && report.contains("IO.ELEMENT"),
        "Element X in DeviceIdle whitelist", RiskLevel.LOW,
        "OK — app will not be frozen by system")

    add(report.contains("DEVICE_ADMIN") || report.contains("ADMIN="),
        "Device Administrators are active", RiskLevel.MEDIUM,
        "Settings → Security → Device administrators → Check the list")

    add(report.contains("TOP") && report.contains("CPU"),
        "Background processes not checked", RiskLevel.LOW,
        "Run top -n 1 -b to analyze load")

    if (bankingReport.isNotBlank()) {
        val bankUpper = bankingReport.uppercase()
        add(bankUpper.contains("DANGEROUS") || bankUpper.contains("HIGH RISK"),
            "Banking apps have dangerous permissions", RiskLevel.CRITICAL,
            "Revoke permissions: pm revoke <pkg> android.permission.READ_SMS etc.")
        add(bankUpper.contains("OVERLAY") || bankUpper.contains("SYSTEM_ALERT_WINDOW"),
            "Banking apps have SYSTEM_ALERT_WINDOW", RiskLevel.HIGH,
            "Revoke: pm revoke <pkg> android.permission.SYSTEM_ALERT_WINDOW")
    }

    val level = when {
        score >= 70 -> RiskLevel.CRITICAL
        score >= 40 -> RiskLevel.HIGH
        score >= 20 -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }

    return SecuritySummary(level = level, score = score.coerceAtMost(100), issues = issues)
}
