package com.omnieval.app

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OmniEvalApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniEvalApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("omnieval_settings", 0) }
    var input by remember { mutableStateOf("") }
    var report by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "https://api.openai.com/v1") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model", "gpt-4o-mini") ?: "") }
    var apiKey by remember { mutableStateOf(ApiKeyVault.decrypt(prefs.getString("api_key", "") ?: "")) }
    var settingsSaved by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
        report = ""
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF315C9B))) {
        Scaffold(topBar = {
            TopAppBar(title = {
                Column {
                    Text("OmniEval", fontWeight = FontWeight.Bold)
                    Text(if (language) "Multimodal Evaluator" else "全模态智能评估", style = MaterialTheme.typography.labelSmall)
                }
            }, actions = {
                TextButton(onClick = { language = !language }) { Text(if (language) "中文" else "EN") }
            })
        }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ElevatedCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (language) "AI connection" else "AI 模型连接", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = endpoint, onValueChange = { endpoint = it; settingsSaved = false },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(if (language) "API base URL" else "接口地址（兼容 Chat Completions）") },
                            placeholder = { Text("https://api.openai.com/v1") }
                        )
                        OutlinedTextField(
                            value = model, onValueChange = { model = it; settingsSaved = false },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(if (language) "Model name" else "模型名称") }
                        )
                        OutlinedTextField(
                            value = apiKey, onValueChange = { apiKey = it; settingsSaved = false },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(if (language) "API key" else "API Key（本机加密保存）") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                prefs.edit().putString("endpoint", endpoint.trim())
                                    .putString("model", model.trim())
                                    .putString("api_key", ApiKeyVault.encrypt(apiKey.trim())).apply()
                                settingsSaved = true
                            }, enabled = endpoint.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()) {
                                Text(if (language) "Save settings" else "保存连接设置")
                            }
                            if (settingsSaved) Text(if (language) "Saved" else "已保存", modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            if (language) "Use a trusted HTTPS OpenAI-compatible endpoint. The key is encrypted with Android Keystore on this device. Avoid plain HTTP and do not share your key."
                            else "请使用可信的 HTTPS 兼容接口。密钥通过 Android Keystore 在本机加密保存；不要使用不可信接口或分享密钥。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ElevatedCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (language) "Content to evaluate" else "输入评估内容", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = input, onValueChange = { input = it; report = "" },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp),
                            placeholder = { Text(if (language) "Paste text, ideas, claims, plans, data or formulas…" else "粘贴文字、观点、理论、方案、数据、公式或说明……") }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                                Text(if (language) "Attach image" else "添加图片")
                            }
                            OutlinedButton(onClick = { input = ""; report = ""; imageUri = null }, modifier = Modifier.weight(1f)) {
                                Text(if (language) "Clear" else "清空")
                            }
                        }
                        if (imageUri != null) Text(if (language) "Image attached; it will be sent to the vision-capable model." else "已添加图片；评估时将随请求发送给支持视觉的模型。", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Button(
                    onClick = {
                        busy = true
                        report = ""
                        val textSnapshot = input.trim()
                        val uriSnapshot = imageUri
                        val endpointSnapshot = endpoint.trim()
                        val modelSnapshot = model.trim()
                        val keySnapshot = apiKey.trim()
                        Thread {
                            val outcome = try {
                                AiEvaluator.evaluate(context, endpointSnapshot, modelSnapshot, keySnapshot, textSnapshot, uriSnapshot)
                            } catch (e: Exception) {
                                "请求失败：${e.message ?: "未知错误"}\n\n请检查网络、接口地址、模型名称、API Key，以及该模型是否支持图像输入。"
                            }
                            Handler(Looper.getMainLooper()).post {
                                report = outcome
                                busy = false
                            }
                        }.start()
                    },
                    enabled = input.isNotBlank() || imageUri != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (busy) (if (language) "Analyzing…" else "正在分析…") else (if (language) "Run AI evaluation" else "开始 AI 智能评估"))
                }

                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (report.isNotBlank()) {
                    ElevatedCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (language) "Evaluation report" else "评估报告", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(report, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(
                    if (language) "AI analysis is a reasoned assessment, not proof. External source verification is not yet connected; ask the model to mark uncertainty and never treat uncited claims as verified."
                    else "AI 分析属于推理评估，不等于事实证明。当前版本尚未接入独立联网来源核验；未提供可核查来源的主张不能视为已证实。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

object AiEvaluator {
    fun evaluate(
        context: android.content.Context,
        endpoint: String,
        model: String,
        apiKey: String,
        text: String,
        imageUri: Uri?
    ): String {
        require(endpoint.startsWith("https://", true)) { "接口地址必须使用 HTTPS。" }
        require(model.isNotBlank()) { "请填写模型名称。" }
        require(apiKey.isNotBlank()) { "请填写 API Key 并保存连接设置。" }
        require(text.isNotBlank() || imageUri != null) { "请先输入文字或添加图片。" }

        val content = JSONArray()
        content.put(JSONObject().put("type", "text").put("text", buildPrompt(text)))
        if (imageUri != null) {
            val mime = context.contentResolver.getType(imageUri) ?: "image/jpeg"
            require(mime.startsWith("image/")) { "所选文件不是图片。" }
            val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: throw IllegalStateException("无法读取所选图片。")
            require(bytes.size <= 8 * 1024 * 1024) { "图片超过 8 MB，请选择较小的图片。" }
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            content.put(JSONObject().put("type", "image_url").put(
                "image_url", JSONObject().put("url", "data:$mime;base64,$encoded")
            ))
        }

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content",
                "你是严谨的通用评估分析器。不要迎合用户，不要编造事实、数据、引文或来源。严格区分输入中明确给出的事实、推论、假设和待验证猜想。"))
            .put(JSONObject().put("role", "user").put("content", content))
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.2)
            .put("max_tokens", 5000)

        val base = endpoint.trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 90000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = try { JSONObject(response).optString("error", response) } catch (_: Exception) { response }
                throw IllegalStateException("HTTP $code：${detail.take(900)}")
            }
            val root = JSONObject(response)
            val choices = root.optJSONArray("choices") ?: throw IllegalStateException("接口返回中没有 choices。")
            val message = choices.optJSONObject(0)?.optJSONObject("message")
                ?: throw IllegalStateException("接口没有返回有效的 message。")
            val answer = message.opt("content")
            val output = when (answer) {
                is String -> answer
                is JSONArray -> buildString {
                    for (i in 0 until answer.length()) {
                        val item = answer.optJSONObject(i)
                        if (item != null) append(item.optString("text")).append("\n")
                    }
                }
                else -> ""
            }
            return output.trim().ifBlank { "模型返回了空内容。请检查所选模型与接口的兼容性。" }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPrompt(text: String): String = """
请对下面提交的内容进行严格、通用、可审计的评估。不要因为表达流畅或符合主流观点就默认正确，也不要因为观点非主流就默认错误。

请按以下结构输出中文报告：
1. 内容类型与评估范围：识别它是事实陈述、理论/模型、推导、方案、数据、代码说明或混合内容。
2. 核心主张：逐条提取最重要的主张。
3. 内部自洽性：检查定义是否清楚、前提是否一致、推理是否有效、是否循环论证、是否存在遗漏条件。
4. 事实与证据：区分输入中给出的事实、可由常识判断的内容、需要外部来源核查的内容。没有实际检索来源时，明确写“未独立核验”，严禁伪造引用。
5. 数学/数据/计算（如适用）：检查量纲、单位、公式、边界条件、数量级和可复算性；缺少数据时说明。
6. 反例与替代解释：提出最有力的反例、可证伪条件或其他解释。
7. 问题清单：按严重程度列出具体问题，并说明依据。
8. 结论分级：分别标记“支持充分 / 部分支持 / 证据不足 / 存在矛盾 / 暂无法判断”，不要强行给出单一准确率。
9. 下一步验证：给出最少且最有区分力的验证步骤。

若内容属于用户自建理论，必须将“模型内部自洽性”与“外部实验/观测符合度”分开评价；不得擅自改写用户给定的基础假设。明确区分公理/假设、推导结果、待验证猜想。

待评估内容：
$text
""".trim()
}

object ApiKeyVault {
    private const val ALIAS = "OmniEvalApiKey"
    private const val STORE = "AndroidKeyStore"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(STORE).apply { load(null) }
        val existing = store.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val parts = value.split(":")
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
