package com.omnieval.app

import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

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
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Evaluation?>(null) }
    var busy by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
        if (uri != null) {
            busy = true
            try {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                recognizer.process(image)
                    .addOnSuccessListener { vision ->
                        if (vision.text.isNotBlank()) input = listOf(input, vision.text).filter { it.isNotBlank() }.joinToString("\n\n")
                        busy = false
                    }
                    .addOnFailureListener { busy = false }
            } catch (_: Exception) { busy = false }
        }
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
                        Text(if (language) "Input content" else "输入评估内容", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = input, onValueChange = { input = it; result = null },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp),
                            placeholder = { Text(if (language) "Paste text, ideas, data or descriptions…" else "粘贴任意长度文字、理论、方案、数据……") }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                                Text(if (language) "Choose image" else "选择图片")
                            }
                            OutlinedButton(onClick = { input = ""; result = null; imageUri = null }, modifier = Modifier.weight(1f)) {
                                Text(if (language) "Clear" else "清空")
                            }
                        }
                        if (imageUri != null) Text(if (language) "Image selected; local OCR attempted." else "已选择图片，已尝试本地 OCR。", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(onClick = { result = OfflineEvaluator.evaluate(input) }, enabled = input.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (busy) "处理中…" else if (language) "Run local evaluation" else "开始基础评估")
                }
                result?.let { EvaluationView(it, language) }
                Text(
                    if (language) "Prototype: local rule checks and Chinese OCR only. Cloud multimodal analysis and source verification are not connected yet."
                    else "当前为第一阶段原型：本地规则检查与中文 OCR。在线多模态分析、联网核验尚未接入。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class Evaluation(val score: Int, val chars: Int, val checks: List<Pair<String, String>>, val findings: List<String>, val limits: List<String>)

object OfflineEvaluator {
    fun evaluate(text: String): Evaluation {
        val t = text.trim()
        val hasNumber = Regex("[0-9]+([.,][0-9]+)?").containsMatchIn(t)
        val strongClaim = listOf("因此", "所以", "证明", "必然", "一定", "because", "therefore", "proves").any { t.contains(it, true) }
        val source = listOf("来源", "参考文献", "doi", "http://", "https://", "reference", "source:").any { t.contains(it, true) }
        val findings = mutableListOf<String>()
        if (t.length < 80) findings += "输入较短，当前只能进行有限的结构检查。"
        if (strongClaim && !source) findings += "发现较强结论性措辞，但未发现明确来源标记，建议补充证据。"
        if (hasNumber) findings += "发现数值表达；仍需结合单位、公式和原始数据核验。"
        if (findings.isEmpty()) findings += "未发现明显的基础结构风险；这不代表内容已获事实验证。"
        val score = (50 + (if (t.length >= 300) 10 else 0) + (if (source) 10 else 0) - (if (strongClaim && !source) 10 else 0)).coerceIn(0, 100)
        return Evaluation(score, t.length, listOf(
            "内容长度" to "${t.length} 字符",
            "数值线索" to if (hasNumber) "已检测，待核算" else "未检测到",
            "来源标记" to if (source) "检测到" else "未检测到",
            "强结论措辞" to if (strongClaim) "检测到" else "未明显检测到"
        ), findings, listOf(
            "此分数仅为规则检查提示，不是内容正确率或科学可信度。",
            "离线规则无法完整理解语义，也不能独立判定事实真伪。",
            "当前图片仅尝试 OCR；尚未完成照片语义、图表及图文关系分析。",
            "当前未连接在线模型或外部资料库。"
        ))
    }
}

@Composable
fun EvaluationView(e: Evaluation, english: Boolean) {
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(if (english) "Preliminary result" else "初步评估结果", style = MaterialTheme.typography.titleLarge)
            Text("${e.score}/100", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { e.score / 100f }, modifier = Modifier.fillMaxWidth())
            Text(if (english) "Checks" else "分项观察", fontWeight = FontWeight.Bold)
            e.checks.forEach { (k, v) -> Text("$k：$v") }
            HorizontalDivider()
            Text(if (english) "Findings" else "发现", fontWeight = FontWeight.Bold)
            e.findings.forEach { Text("• $it") }
            HorizontalDivider()
            Text(if (english) "Limitations" else "评估边界", fontWeight = FontWeight.Bold)
            e.limits.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
