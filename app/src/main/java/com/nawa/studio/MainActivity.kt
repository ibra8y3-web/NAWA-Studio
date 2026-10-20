package com.nawa.studio

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.nawa.studio.core.NawaAndroidConfig
import com.nawa.studio.core.NawaElement
import com.nawa.studio.core.NawaControlKind
import com.nawa.studio.core.NawaParser
import com.nawa.studio.core.NawaRevision
import com.nawa.studio.core.NawaSourceEditor
import com.nawa.studio.core.NawaProject
import com.nawa.studio.core.NawaValue
import com.nawa.studio.core.NawaTraceEvent
import com.nawa.studio.core.NawaProjectAudit
import com.nawa.studio.core.NawaProjectFinding
import com.nawa.studio.core.NawaProjectTemplate
import com.nawa.studio.core.ProjectRepository
import com.nawa.studio.core.ProjectSearchMatch
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private val Canvas = Color(0xFF0B1018)
private val Panel = Color(0xFF131D2A)
private val PanelRaised = Color(0xFF192638)
private val Border = Color(0xFF26384B)
private val Mint = Color(0xFF67E8C2)
private val Ink = Color(0xFFEAF2FA)
private val Muted = Color(0xFF91A5B9)
private val Warning = Color(0xFFFFC777)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashReporter()
        window.statusBarColor = android.graphics.Color.rgb(11, 16, 24)
        window.navigationBarColor = android.graphics.Color.rgb(11, 16, 24)
        NawaTextMate.initialize(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Mint,
                onPrimary = Color(0xFF06251C),
                secondary = Color(0xFF87BFEA),
                background = Canvas,
                surface = Panel,
                onSurface = Ink,
                onBackground = Ink
            )) {
                androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Canvas) { NawaApp() }
                }
            }
        }
    }

    /** Persist the last uncaught crash so device-only failures can be diagnosed from the next launch. */
    private fun installCrashReporter() {
        val reportFile = File(filesDir, "nawa-last-crash.txt")
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                reportFile.writeText(
                    "NAWA Studio crash report\n" +
                        "time=${System.currentTimeMillis()}\n" +
                        "thread=${thread.name}\n\n" +
                        android.util.Log.getStackTraceString(error)
                )
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

}

@Composable
private fun NawaApp() {
    val context = LocalContext.current
    val repository = remember { ProjectRepository(context) }
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<NawaProject>>(emptyList()) }
    var activeProject by remember { mutableStateOf<NawaProject?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<NawaProject?>(null) }
    var projectName by remember { mutableStateOf("مشروعي الجديد") }
    var selectedProjectTemplate by remember { mutableStateOf(NawaProjectTemplate.NAWA_COUNTER) }
    var projectCreating by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var crashReport by remember { mutableStateOf<String?>(null) }
    var showCrashReport by remember { mutableStateOf(false) }

    fun refreshProjects() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.listProjects() } }
                .onSuccess { projects = it }
                .onFailure { message = "تعذر تحديث المشاريع: ${it.message ?: "خطأ غير معروف"}" }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val staging = File(context.cacheDir, "nawa-import-${System.currentTimeMillis()}.zip")
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                staging.outputStream().use { output -> copyLimited(input, output, 50L * 1024 * 1024) }
                            } ?: error("تعذر قراءة الملف المختار.")
                            repository.importProject(staging)
                        } finally {
                            staging.delete()
                        }
                    }
                }.onSuccess {
                    refreshProjects()
                    message = "تم استيراد المشروع: ${it.name}"
                }.onFailure { message = "فشل الاستيراد: ${it.message ?: "ملف غير صالح"}" }
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                repository.ensureStarterProject()
                repository.listProjects()
            }
        }.onSuccess { projects = it }
            .onFailure { message = "تعذر فتح مساحة المشاريع: ${it.message ?: "خطأ غير معروف"}" }
        loading = false
    }

    LaunchedEffect(Unit) {
        crashReport = withContext(Dispatchers.IO) {
            runCatching {
                File(context.filesDir, "nawa-last-crash.txt")
                    .takeIf { it.isFile && it.length() > 0L }
                    ?.readText()
                    ?.takeLast(12_000)
            }.getOrNull()
        }
        showCrashReport = !crashReport.isNullOrBlank()
    }

    if (activeProject == null) {
        Dashboard(
            projects = projects,
            loading = loading,
            message = message,
            onDismissMessage = { message = null },
            onNewProject = { projectName = "مشروعي الجديد"; selectedProjectTemplate = NawaProjectTemplate.NAWA_COUNTER; showCreateDialog = true },
            onImportProject = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            onOpenProject = { activeProject = it },
            onDeleteProject = { pendingDelete = it },
            onShareProject = { project ->
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { repository.exportProject(project) } }
                        .onSuccess { shareFile(context, it, "مشاركة حزمة مشروع نواة") }
                        .onFailure { message = "تعذر تصدير المشروع: ${it.message ?: "خطأ غير معروف"}" }
                }
            }
        )
    } else {
        key(activeProject!!.id) {
            EditorScreen(
                project = activeProject!!,
                repository = repository,
                onBack = {
                    activeProject = null
                    refreshProjects()
                },
                message = message,
                onDismissMessage = { message = null },
                onMessage = { message = it },
                onBuildSource = { project, currentPath, currentSource ->
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                repository.saveFile(project, currentPath, currentSource)
                                repository.exportAndroidProject(project, repository.readSource(project))
                            }
                        }.onSuccess {
                            shareFile(context, it, "مشروع Android المُنشأ من نواة")
                            message = "جهّزت مصدر Android وملفات Gradle. للحصول على APK، شغّل سير عمل GitHub المرفق أو ابنِ المشروع بأدوات Android."
                        }.onFailure { message = "تعذر تجهيز مشروع Android: ${it.message ?: "تحقق من الكود المدعوم"}" }
                    }
                }
            )
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("إنشاء مشروع جديد", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("سيُحفظ المشروع محليًا داخل نواة. اختر قالب البداية؛ الملفات الأساسية ستُنشأ فعليًا.", color = Muted, fontSize = 13.sp)
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it.take(48) },
                        label = { Text("اسم المشروع") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        NawaProjectTemplate.values().forEach { template ->
                            Surface(
                                color = if (selectedProjectTemplate == template) Color(0xFF173B35) else PanelRaised,
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, if (selectedProjectTemplate == template) Mint else Border),
                                modifier = Modifier.fillMaxWidth().clickable { selectedProjectTemplate = template }
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    Text(if (selectedProjectTemplate == template) "●" else "○", color = if (selectedProjectTemplate == template) Mint else Muted, fontSize = 15.sp)
                                    Column(Modifier.weight(1f)) {
                                        Text(template.title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        Text(template.description, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(enabled = !projectCreating && projectName.isNotBlank(), onClick = {
                    val requestedName = projectName
                    val requestedTemplate = selectedProjectTemplate
                    projectCreating = true
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { repository.createProject(requestedName, requestedTemplate) } }
                            .onSuccess { created ->
                                activeProject = created
                                projects = withContext(Dispatchers.IO) { repository.listProjects() }
                                showCreateDialog = false
                            }
                            .onFailure { message = it.message ?: "تعذر إنشاء المشروع." }
                        projectCreating = false
                    }
                }) { Text(if (projectCreating) "جارٍ الإنشاء…" else "إنشاء") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("إلغاء") } },
            containerColor = Panel,
            titleContentColor = Ink,
            textContentColor = Muted
        )
    }

    pendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف المشروع؟", color = Ink) },
            text = { Text("سيُحذف «${project.name}» وملفاته من مساحة نواة المحلية.", color = Muted) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB84C54)),
                    onClick = {
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { repository.deleteProject(project) } }
                                .onSuccess { refreshProjects(); message = "تم حذف المشروع." }
                                .onFailure { message = "تعذر الحذف: ${it.message ?: "خطأ غير معروف"}" }
                            pendingDelete = null
                        }
                    }
                ) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } },
            containerColor = Panel
        )
    }

    if (showCrashReport && !crashReport.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showCrashReport = false },
            title = { Text("تم رصد إغلاق مفاجئ سابق", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("انسخ التقرير وأرسله للدعم لتحديد سبب الإغلاق بدقة. التقرير محلي ولا يُرسل تلقائيًا.", color = Muted, fontSize = 12.sp)
                    Text(crashReport.orEmpty(), color = Ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NAWA crash report", crashReport.orEmpty()))
                        message = "تم نسخ تقرير الانهيار. الصقه هنا في المحادثة."
                    } else {
                        message = "تعذر الوصول إلى الحافظة؛ افتح التقرير وأرسل آخر الأسطر يدويًا."
                    }
                    showCrashReport = false
                }) { Text("نسخ التقرير") }
            },
            dismissButton = { TextButton(onClick = { showCrashReport = false }) { Text("لاحقًا") } },
            containerColor = Panel,
            titleContentColor = Ink,
            textContentColor = Muted
        )
    }

}

private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream, maxBytes: Long) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        copied += count
        require(copied <= maxBytes) { "ملف الاستيراد يتجاوز الحد المسموح (50 ميجابايت)." }
        output.write(buffer, 0, count)
    }
}

private fun shareFile(context: android.content.Context, file: File, chooserTitle: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}


/** Parsing a local file must report a diagnostic instead of crashing the whole UI. */
private fun safeParseNawa(source: String): com.nawa.studio.core.NawaProgram = runCatching {
    NawaParser.parse(source)
}.getOrElse { failure ->
    com.nawa.studio.core.NawaProgram(
        appName = "تعذر التحليل",
        screenName = "Home",
        states = emptyList(),
        elements = emptyList(),
        diagnostics = listOf(
            com.nawa.studio.core.NawaDiagnostic(
                1,
                com.nawa.studio.core.NawaDiagnostic.Severity.ERROR,
                "تعذر تحليل المصدر: ${failure.javaClass.simpleName}: ${failure.message ?: "خطأ غير معروف"}"
            )
        ),
        hasAppDeclaration = false,
        hasScreenDeclaration = false
    )
}

@Composable
private fun Dashboard(
    projects: List<NawaProject>,
    loading: Boolean,
    message: String?,
    onDismissMessage: () -> Unit,
    onNewProject: () -> Unit,
    onImportProject: () -> Unit,
    onOpenProject: (NawaProject) -> Unit,
    onDeleteProject: (NawaProject) -> Unit,
    onShareProject: (NawaProject) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Canvas).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(50.dp).background(
                    Brush.linearGradient(listOf(Color(0xFF67E8C2), Color(0xFF53A9D4))), RoundedCornerShape(16.dp)
                ), contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Code, contentDescription = null, tint = Color(0xFF06251C), modifier = Modifier.size(28.dp)) }
            Column(modifier = Modifier.weight(1f)) {
                Text("نواة", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("استوديو البرمجة على Android", color = Muted, fontSize = 12.sp)
            }
            Box(Modifier.background(Color(0xFF173B35), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("STUDIO", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text("ابنِ تطبيقك من هاتفك", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "اكتب NawaScript في محرر احترافي، جرّب المعاينة المحلية، وأصدر مشروع Android بمصادر وملفات Gradle.",
                    color = Muted, fontSize = 14.sp, lineHeight = 22.sp
                )
                Button(onClick = onNewProject, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("إنشاء مشروع جديد", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onImportProject, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(15.dp)) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("استيراد حزمة .nsqpack")
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("مشاريعك", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("${projects.size} مشروع محفوظ محليًا", color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Mint)
        }

        when {
            loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Mint) }
            projects.isEmpty() -> Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Text("لا توجد مشاريع بعد. أنشئ أول مشروع للبدء.", Modifier.padding(20.dp), color = Muted)
            }
            else -> projects.forEach { project ->
                ProjectCard(
                    project = project,
                    onOpen = { onOpenProject(project) },
                    onDelete = { onDeleteProject(project) },
                    onShare = { onShareProject(project) }
                )
            }
        }

        message?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF30291D)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, Modifier.weight(1f), color = Color(0xFFFFD6A5), fontSize = 12.sp, lineHeight = 18.sp)
                    TextButton(onClick = onDismissMessage) { Text("إغلاق") }
                }
            }
        }
        Text("يعمل التخزين والتحرير والمعاينة محليًا. بناء APK النهائي يستخدم أدوات Android أو سير البناء المرفق.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun ProjectCard(project: NawaProject, onOpen: () -> Unit, onDelete: () -> Unit, onShare: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(PanelRaised, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Code, contentDescription = null, tint = Mint, modifier = Modifier.size(23.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(project.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${project.id.takeLast(8)} · src/main.nawa", color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("آخر تعديل: ${formatDate(project.updatedAt)}", color = Muted, fontSize = 11.sp)
                }
                IconButton(onClick = onShare) { Icon(Icons.Filled.IosShare, contentDescription = "تصدير المشروع", tint = Muted) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف المشروع", tint = Color(0xFFE08B91)) }
            }
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("فتح المحرر")
            }
        }
    }
}

private enum class EditorPage { CODE, DESIGN, PREVIEW, DIAGNOSTICS }

@Composable
private fun EditorScreen(
    project: NawaProject,
    repository: ProjectRepository,
    onBack: () -> Unit,
    message: String?,
    onDismissMessage: () -> Unit,
    onMessage: (String) -> Unit,
    onBuildSource: (NawaProject, String, String) -> Unit
) {
    var files by remember(project.id) { mutableStateOf(runCatching { repository.listFiles(project) }.getOrDefault(emptyList())) }
    var folders by remember(project.id) { mutableStateOf(runCatching { repository.listFolders(project) }.getOrDefault(emptyList())) }
    var selectedPath by remember(project.id) { mutableStateOf("src/main.nawa") }
    var openTabs by remember(project.id) { mutableStateOf(listOf("src/main.nawa")) }
    var source by remember(project.id) { mutableStateOf(runCatching { repository.readSource(project) }.getOrDefault("")) }
    var requestedJumpLine by remember(project.id) { mutableStateOf<Int?>(null) }
    var designScreenName by remember(project.id) { mutableStateOf("Home") }
    var isDirty by remember(project.id) { mutableStateOf(false) }
    var page by remember(project.id) { mutableStateOf(EditorPage.CODE) }
    var status by remember(project.id) { mutableStateOf("جاهز") }
    var showFilesDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderPathInput by remember { mutableStateOf("src/new-folder") }
    var folderToDelete by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var importTargetPath by remember { mutableStateOf("assets/imported-file.bin") }
    var showImportTargetDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteFileDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showAndroidSettingsDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var historyItems by remember { mutableStateOf<List<NawaRevision>>(emptyList()) }
    var historyLoading by remember { mutableStateOf(false) }
    var pendingRevision by remember { mutableStateOf<NawaRevision?>(null) }
    var pendingRevisionPath by remember { mutableStateOf("") }
    var androidLabel by remember { mutableStateOf("") }
    var androidApplicationId by remember { mutableStateOf("") }
    var androidVersionName by remember { mutableStateOf("1.0.0") }
    var androidVersionCode by remember { mutableStateOf("1") }
    var androidMinSdk by remember { mutableStateOf("26") }
    var androidTargetSdk by remember { mutableStateOf("35") }
    var androidPermissionsText by remember { mutableStateOf("") }
    var androidDependenciesText by remember { mutableStateOf("") }
    var showProjectSearchDialog by remember { mutableStateOf(false) }
    var projectSearchQuery by remember { mutableStateOf("") }
    var projectSearchResults by remember { mutableStateOf<List<ProjectSearchMatch>>(emptyList()) }
    var projectSearchScanned by remember { mutableStateOf(0) }
    var projectSearchTruncated by remember { mutableStateOf(false) }
    var projectSearchRunning by remember { mutableStateOf(false) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var showAddButtonDialog by remember { mutableStateOf(false) }
    var showAddControlDialog by remember { mutableStateOf(false) }
    var addingControlKind by remember { mutableStateOf(NawaControlKind.INPUT) }
    var designControlLabel by remember { mutableStateOf("أدخل النص") }
    var designControlState by remember { mutableStateOf("") }
    var designControlMin by remember { mutableStateOf("0") }
    var designControlMax by remember { mutableStateOf("100") }
    var showEditElementDialog by remember { mutableStateOf(false) }
    var editingElement by remember { mutableStateOf<NawaElement?>(null) }
    var editElementContent by remember { mutableStateOf("") }
    var editElementAction by remember { mutableStateOf("") }
    var designX by remember { mutableStateOf("") }
    var designY by remember { mutableStateOf("") }
    var designTextInput by remember { mutableStateOf("نص جديد") }
    var designButtonLabel by remember { mutableStateOf("زر جديد") }
    var designButtonAction by remember { mutableStateOf("") }
    var pathInput by remember { mutableStateOf("src/helpers.nawa") }
    var searchText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val latestSource by rememberUpdatedState(source)
    val latestSelectedPath by rememberUpdatedState(selectedPath)
    val program = remember(source) { safeParseNawa(source) }    LaunchedEffect(source, designScreenName) {
        if (program.screens.isNotEmpty() && program.screens.none { it.name == designScreenName }) {
            designScreenName = program.screens.first().name
        }
    }
    val assetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val displayName = queryDisplayName(context, uri) ?: "asset-${System.currentTimeMillis()}"
                        context.contentResolver.openInputStream(uri)?.use { input -> repository.importAsset(project, displayName, input) }
                            ?: error("تعذر قراءة المورد المختار.")
                    }
                }.onSuccess { path ->
                    files = withContext(Dispatchers.IO) { repository.listFiles(project) }; folders = withContext(Dispatchers.IO) { repository.listFolders(project) }
                    onMessage("تم استيراد المورد إلى $path")
                }.onFailure { onMessage("تعذر استيراد المورد: ${it.message ?: "خطأ"}") }
            }
        }
    }

    val projectFileImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            pendingImportUri = uri
            val displayName = queryDisplayName(context, uri)?.replace('\\', '/')?.substringAfterLast('/')
                ?: "import-${System.currentTimeMillis()}.bin"
            importTargetPath = "assets/$displayName"
            showImportTargetDialog = true
        }
    }

    LaunchedEffect(project.id, selectedPath, source) {
        val pathToSave = selectedPath
        val contentsToSave = source
        delay(550)
        runCatching { withContext(Dispatchers.IO) { repository.saveFile(project, pathToSave, contentsToSave) } }
            .onSuccess {
                // A slower write for an older edit must not clear the dirty flag for newer text.
                if (latestSelectedPath == pathToSave && latestSource == contentsToSave) {
                    isDirty = false
                    status = "تم الحفظ تلقائيًا"
                    files = withContext(Dispatchers.IO) { repository.listFiles(project) }; folders = withContext(Dispatchers.IO) { repository.listFolders(project) }
                }
            }
            .onFailure { if (latestSelectedPath == pathToSave && latestSource == contentsToSave) status = "تعذّر الحفظ: ${it.message ?: "خطأ"}" }
    }

    fun selectFile(path: String, lineNumber: Int? = null) {
        if (isBinaryPath(path)) {
            onMessage("هذا مورد ثنائي محفوظ داخل المشروع، وليس ملف نص يمكن فتحه في المحرر: $path")
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.saveFile(project, selectedPath, source)
                    repository.readFile(project, path)
                }
            }.onSuccess {
                selectedPath = path
                if (path !in openTabs) openTabs = openTabs + path
                source = it
                requestedJumpLine = lineNumber
                isDirty = false
                status = if (lineNumber != null) "تم فتح $path عند السطر $lineNumber" else "تم فتح $path"
                page = if (path == "src/main.nawa") EditorPage.CODE else EditorPage.CODE
                showFilesDialog = false
            }.onFailure { onMessage("تعذر فتح الملف: ${it.message ?: "خطأ"}") }
        }
    }

    Column(Modifier.fillMaxSize().background(Canvas)) {
        Row(
            Modifier.fillMaxWidth().background(Panel).padding(horizontal = 5.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                val pathToSave = selectedPath
                val sourceToSave = source
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { repository.saveFile(project, pathToSave, sourceToSave) } }
                        .onSuccess {
                            if (latestSelectedPath == pathToSave && latestSource == sourceToSave) {
                                isDirty = false
                                onBack()
                            } else {
                                isDirty = true
                                status = "ظهرت تعديلات أحدث أثناء الحفظ؛ احفظها قبل الرجوع"
                                onMessage("لم أخرج من المحرر لأن هناك تعديلات أحدث لم تُحفظ بعد.")
                            }
                        }
                        .onFailure { onMessage("لم أرجع إلى قائمة المشاريع حتى لا تفقد التعديلات: ${it.message ?: "تعذر الحفظ"}") }
                }
            }) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع", tint = Ink) }
            Column(Modifier.weight(1f)) {
                Text(project.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${if (isDirty) "تعديلات غير محفوظة" else status} · $selectedPath", color = if (isDirty) Warning else Muted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { showFilesDialog = true }) { Icon(Icons.Filled.FolderOpen, contentDescription = "ملفات المشروع", tint = Mint) }
            IconButton(onClick = {
                val pathToSave = selectedPath
                val sourceToSave = source
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { repository.saveFile(project, pathToSave, sourceToSave) } }
                        .onSuccess {
                            if (latestSelectedPath == pathToSave && latestSource == sourceToSave) {
                                isDirty = false
                                status = "تم الحفظ"
                            } else {
                                isDirty = true
                                status = "تم حفظ النسخة السابقة؛ توجد تعديلات أحدث"
                            }
                        }
                        .onFailure { status = "فشل الحفظ: ${it.message ?: "خطأ"}" }
                }
            }) { Icon(Icons.Filled.Save, contentDescription = "حفظ", tint = Mint) }
            IconButton(onClick = {
                scope.launch {
                    runCatching {
                        val text = withContext(Dispatchers.IO) {
                            runCatching { repository.readFile(project, "android.properties") }
                                .getOrElse { NawaAndroidConfig.defaultTemplate() }
                        }
                        NawaAndroidConfig.parse(text)
                    }.onSuccess { config ->
                        androidLabel = config.label
                        androidApplicationId = config.applicationId
                        androidVersionName = config.versionName
                        androidVersionCode = config.versionCode.toString()
                        androidMinSdk = config.minSdk.toString()
                        androidTargetSdk = config.targetSdk.toString()
                        androidPermissionsText = config.permissions.joinToString("\n")
                        androidDependenciesText = config.dependencies.joinToString("\n")
                        showAndroidSettingsDialog = true
                    }.onFailure { onMessage("إعدادات Android غير صالحة: ${it.message ?: "خطأ"}") }
                }
            }) { Icon(Icons.Filled.Settings, contentDescription = "إعدادات بناء Android", tint = Mint) }
            IconButton(
                onClick = {
                    if (selectedPath == "project.nawa" || isBinaryPath(selectedPath)) {
                        onMessage("سجل النسخ متاح للملفات النصية فقط.")
                    } else {
                        scope.launch {
                            historyLoading = true
                            runCatching { withContext(Dispatchers.IO) { repository.listRevisions(project, selectedPath) } }
                                .onSuccess { historyItems = it; showHistoryDialog = true }
                                .onFailure { onMessage("تعذر قراءة سجل النسخ: ${it.message ?: "خطأ"}") }
                            historyLoading = false
                        }
                    }
                },
                enabled = !historyLoading
            ) { Icon(Icons.Filled.History, contentDescription = "سجل النسخ السابقة", tint = Mint) }
            IconButton(onClick = { showSearchDialog = true }) { Icon(Icons.Filled.Search, contentDescription = "بحث واستبدال", tint = Ink) }
        }

        message?.let { text ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF30291D)), modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text, Modifier.weight(1f), color = Color(0xFFFFD6A5), fontSize = 11.sp, lineHeight = 16.sp)
                    TextButton(onClick = onDismissMessage) { Text("×") }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            PageTab("الكود", selected = page == EditorPage.CODE, icon = { Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(15.dp)) }) { page = EditorPage.CODE }
            PageTab("التصميم", selected = page == EditorPage.DESIGN, icon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(15.dp)) }) {
                if (selectedPath == "src/main.nawa") page = EditorPage.DESIGN
                else onMessage("مصمم الواجهة الحالي يعمل على src/main.nawa. ملفات Kotlin/Java تُحرر في وضع الكود.")
            }
            PageTab("المعاينة", selected = page == EditorPage.PREVIEW, icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp)) }) { page = EditorPage.PREVIEW }
            PageTab("الأخطاء", selected = page == EditorPage.DIAGNOSTICS, icon = { Icon(Icons.Filled.WarningAmber, contentDescription = null, modifier = Modifier.size(15.dp)) }) { page = EditorPage.DIAGNOSTICS }
        }

        if (page == EditorPage.CODE) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                openTabs.forEach { tabPath ->
                    val selected = tabPath == selectedPath
                    Surface(
                        color = if (selected) Color(0xFF173B35) else Panel,
                        shape = RoundedCornerShape(9.dp),
                        border = BorderStroke(1.dp, if (selected) Color(0xFF276452) else Border),
                        modifier = Modifier.clickable { selectFile(tabPath) }
                    ) {
                        Row(
                            Modifier.padding(start = 10.dp, end = 5.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                tabPath.substringAfterLast('/'),
                                color = if (selected) Mint else Muted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (openTabs.size > 1) {
                                IconButton(
                                    onClick = {
                                        if (selected) {
                                            val replacementPath = openTabs.firstOrNull { it != tabPath }
                                            if (replacementPath != null) selectFile(replacementPath)
                                        }
                                        openTabs = openTabs.filterNot { it == tabPath }
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "إغلاق التبويب $tabPath", tint = if (selected) Mint else Muted, modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        when (page) {
            EditorPage.CODE -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Code, contentDescription = null, tint = Mint, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(selectedPath, color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (selectedPath == "src/main.nawa") {
                        TextButton(onClick = {
                            runCatching {
                                val formatted = NawaSourceEditor.formatNawa(source)
                                require(!NawaParser.parse(formatted).hasErrors) { "التنسيق لم يحافظ على صحة اللغة؛ لم أغيّر المصدر." }
                                formatted
                            }.onSuccess {
                                if (it != source) { source = it; isDirty = true; status = "تم تنسيق NawaScript" }
                            }.onFailure { onMessage("تعذر تنسيق المصدر: ${it.message ?: "خطأ"}") }
                        }) { Text("تنسيق", fontSize = 11.sp) }
                        TextButton(onClick = { designTextInput = "نص جديد"; showAddTextDialog = true }) { Text("+ نص", fontSize = 11.sp) }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                    key(selectedPath) {
                        NawaCodeEditor(
                            source = source,
                            path = selectedPath,
                            jumpToLine = requestedJumpLine,
                            onJumpHandled = { line -> if (requestedJumpLine == line) requestedJumpLine = null },
                            onChange = {
                                if (it != source) { source = it; isDirty = true; status = "جارٍ حفظ التعديلات…" }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { page = EditorPage.PREVIEW }, enabled = selectedPath == "src/main.nawa", modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(13.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("تشغيل المعاينة")
                    }
                    OutlinedButton(
                        onClick = { onBuildSource(project, selectedPath, source) },
                        enabled = selectedPath != "project.nawa" && !isBinaryPath(selectedPath),
                        modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(13.dp)
                    ) {
                        Icon(Icons.Filled.Build, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("تجهيز Android")
                    }
                }
            }
            EditorPage.DESIGN -> {
                if (selectedPath != "src/main.nawa") {
                    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("مصمم واجهات NawaScript", color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("افتح src/main.nawa حتى تعدل عناصر الواجهة المرئية.", color = Muted, fontSize = 13.sp)
                        Button(onClick = { selectFile("src/main.nawa") }) { Text("فتح ملف الواجهة") }
                    }
                } else {
                    DesignScreen(
                        source = source,
                        selectedScreenName = designScreenName,
                        onSelectScreen = { designScreenName = it },
                        onBackToCode = { page = EditorPage.CODE },
                        onAddTextRequest = { designTextInput = "نص جديد"; showAddTextDialog = true },
                        onAddButtonRequest = {
                            val numberState = program.screens.firstOrNull { it.name == designScreenName }
                                ?.states?.firstOrNull { it.initialValue is NawaValue.NumberValue }
                                ?: program.states.firstOrNull { it.initialValue is NawaValue.NumberValue }
                            designButtonLabel = "زر جديد"
                            designButtonAction = numberState?.let { "${it.name} = ${it.name} + 1" }.orEmpty()
                            showAddButtonDialog = true
                        },
                        onAddControlRequest = { kind ->
                            addingControlKind = kind
                            val states = program.screens.firstOrNull { it.name == designScreenName }?.states ?: program.states
                            val compatible = states.firstOrNull { state ->
                                when (kind) {
                                    NawaControlKind.INPUT -> state.initialValue is NawaValue.StringValue
                                    NawaControlKind.SWITCH, NawaControlKind.CHECKBOX -> state.initialValue is NawaValue.BooleanValue
                                    NawaControlKind.SLIDER -> state.initialValue is NawaValue.NumberValue
                                }
                            }
                            designControlState = compatible?.name.orEmpty()
                            designControlLabel = when (kind) {
                                NawaControlKind.INPUT -> "أدخل النص"
                                NawaControlKind.SWITCH -> "تفعيل الخيار"
                                NawaControlKind.CHECKBOX -> "اختيار"
                                NawaControlKind.SLIDER -> "القيمة"
                            }
                            designControlMin = "0"
                            designControlMax = "100"
                            showAddControlDialog = true
                        },
                        onPositionElementRequest = { element, x, y ->
                            runCatching {
                                val updated = NawaSourceEditor.replaceRange(
                                    source, element.sourceStart, element.sourceEnd,
                                    NawaSourceEditor.elementSource(element, x, y)
                                )
                                val parsed = NawaParser.parse(updated)
                                require(!parsed.hasErrors) {
                                    parsed.diagnostics.firstOrNull { it.severity == com.nawa.studio.core.NawaDiagnostic.Severity.ERROR }?.message
                                        ?: "تعذر التحقق من موضع العنصر."
                                }
                                updated
                            }.onSuccess {
                                source = it
                                isDirty = true
                                status = "تم حفظ موضع العنصر في المصدر"
                            }.onFailure { onMessage("تعذر نقل العنصر: ${it.message ?: "إحداثيات غير صالحة"}") }
                        },
                        onEditElementRequest = { element ->
                            editingElement = element
                            editElementContent = when (element) {
                                is NawaElement.Text -> element.value
                                is NawaElement.Button -> element.label
                                is NawaElement.Control -> element.label
                            }
                            editElementAction = (element as? NawaElement.Button)?.action?.let(NawaSourceEditor::actionSource).orEmpty()
                            designX = element.x?.toString().orEmpty()
                            designY = element.y?.toString().orEmpty()
                            showEditElementDialog = true
                        },
                        onMoveElementRequest = { element, direction ->
                            runCatching {
                                val selectedScreen = program.screens.firstOrNull { it.name == designScreenName }
                                val elements = (selectedScreen?.elements ?: program.elements).sortedBy { it.sourceStart }
                                val index = elements.indexOfFirst { it.sourceStart == element.sourceStart && it.sourceEnd == element.sourceEnd }
                                require(index >= 0) { "لم أجد العنصر في المصدر الحالي؛ حدّث المصمم ثم حاول مرة أخرى." }
                                val targetIndex = index + direction
                                require(targetIndex in elements.indices) { if (direction < 0) "العنصر في أعلى الشاشة بالفعل." else "العنصر في أسفل الشاشة بالفعل." }
                                val neighbour = elements[targetIndex]
                                val updated = NawaSourceEditor.swapElements(
                                    source, element.sourceStart, element.sourceEnd, neighbour.sourceStart, neighbour.sourceEnd
                                )
                                require(!NawaParser.parse(updated).hasErrors) { "إعادة الترتيب ستنتج مصدرًا غير صالح." }
                                updated
                            }.onSuccess {
                                source = it
                                isDirty = true
                                status = "تم تغيير ترتيب العناصر في المصدر"
                            }.onFailure { onMessage("تعذرت إعادة ترتيب العنصر: ${it.message ?: "خطأ في المصدر"}") }
                        },
                        onDeleteElementRequest = { element ->
                            runCatching {
                                val selectedScreen = program.screens.firstOrNull { it.name == designScreenName }
                                require((selectedScreen?.elements ?: program.elements).size > 1) { "لا يمكن حذف آخر عنصر؛ يجب أن تحتوي الشاشة على عنصر واحد على الأقل." }
                                val updated = NawaSourceEditor.removeElement(source, element.sourceStart, element.sourceEnd)
                                require(!NawaParser.parse(updated).hasErrors) { "حذف العنصر سيترك أخطاء في المصدر؛ صحّح الكود أولًا." }
                                updated
                            }.onSuccess {
                                source = it
                                isDirty = true
                                status = "تم حذف العنصر من المصدر"
                            }.onFailure { onMessage("تعذر حذف العنصر: ${it.message ?: "خطأ في المصدر"}") }
                        }
                    )
                }
            }
            EditorPage.PREVIEW -> {
                PreviewScreen(source, onBackToCode = { page = EditorPage.CODE })
            }
            EditorPage.DIAGNOSTICS -> {
                DiagnosticsScreen(source, selectedPath, project, repository, onJumpToCode = { page = EditorPage.CODE })
            }
        }
    }

    if (showFilesDialog) {
        AlertDialog(
            onDismissRequest = { showFilesDialog = false },
            title = { Text("ملفات المشروع", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    folders.forEach { folder ->
                        Row(
                            Modifier.fillMaxWidth().background(PanelRaised, RoundedCornerShape(10.dp))
                                .clickable { pathInput = "$folder/new-file.nawa"; showCreateFileDialog = true; showFilesDialog = false }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = "مجلد", tint = Mint, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("$folder/", color = Ink, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text("مجلد · اضغط لإنشاء ملف داخله", color = Muted, fontSize = 9.sp)
                            }
                            IconButton(onClick = { folderToDelete = folder }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف المجلد الفارغ $folder", tint = Color(0xFFE08B91))
                            }
                        }
                    }
                    files.forEach { path ->
                        Row(
                            Modifier.fillMaxWidth().background(if (path == selectedPath) Color(0xFF173B35) else PanelRaised, RoundedCornerShape(10.dp))
                                .clickable(enabled = !isBinaryPath(path)) { selectFile(path) }.padding(horizontal = 10.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Code, contentDescription = null, tint = if (path == selectedPath) Mint else Muted, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(path, color = if (path == selectedPath) Mint else Ink, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (isBinaryPath(path)) Text("مورد ثنائي محفوظ داخل الحزمة", color = Muted, fontSize = 9.sp)
                            }
                        }
                    }
                    Divider(color = Border)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { pathInput = "src/new-file.nawa"; showCreateFileDialog = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Text("جديد", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = { pathInput = selectedPath; showRenameDialog = true }, modifier = Modifier.weight(1f), enabled = selectedPath != "src/main.nawa" && !isBinaryPath(selectedPath)) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp)); Text("إعادة تسمية", fontSize = 10.sp)
                        }
                        OutlinedButton(onClick = { showDeleteFileDialog = true }, enabled = selectedPath != "src/main.nawa" && !isBinaryPath(selectedPath), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp)); Text("حذف", fontSize = 11.sp)
                        }
                    }
                    OutlinedButton(onClick = { folderPathInput = "src/new-folder"; showCreateFolderDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("إنشاء مجلد جديد")
                    }
                    OutlinedButton(onClick = { projectFileImportLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("استيراد أي ملف إلى المسار الذي تختاره")
                    }
                    OutlinedButton(onClick = { assetLauncher.launch(arrayOf("image/*", "audio/*", "video/*", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("استيراد صورة أو مورد إلى assets/")
                    }
                    Text("ملفات المشروع تحفظ محليًا وتدخل في حزمة .nsqpack. موارد assets الثنائية تُحفظ وتُصدّر، لكنها لا تُعرض في محرر النص.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showFilesDialog = false }) { Text("إغلاق") } },
            containerColor = Panel,
            titleContentColor = Ink,
            textContentColor = Muted
        )
    }

    if (showCreateFileDialog) {
        PathDialog("إنشاء ملف", "المسار النسبي مثل src/helpers.nawa", pathInput, { pathInput = it }, onDismiss = { showCreateFileDialog = false }) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { repository.createFile(project, pathInput) } }
                    .onSuccess { path -> files = withContext(Dispatchers.IO) { repository.listFiles(project) }; folders = withContext(Dispatchers.IO) { repository.listFolders(project) }; showCreateFileDialog = false; selectFile(path) }
                    .onFailure { onMessage("تعذر إنشاء الملف: ${it.message ?: "مسار غير صالح"}") }
            }
        }
    }

    if (showImportTargetDialog) {
        PathDialog("استيراد ملف إلى المشروع", "المسار النسبي مثل libs/library.jar أو src/main/res/drawable/icon.png", importTargetPath, { importTargetPath = it }, onDismiss = { showImportTargetDialog = false; pendingImportUri = null }) {
            val uri = pendingImportUri
            if (uri == null) {
                showImportTargetDialog = false
                onMessage("لم يتم اختيار ملف للاستيراد.")
            } else {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { input -> repository.importFile(project, importTargetPath, input) }
                                ?: error("تعذر قراءة الملف المختار.")
                        }
                    }.onSuccess { importedPath ->
                        files = withContext(Dispatchers.IO) { repository.listFiles(project) }
                        folders = withContext(Dispatchers.IO) { repository.listFolders(project) }
                        showImportTargetDialog = false
                        pendingImportUri = null
                        onMessage("تم استيراد الملف إلى $importedPath")
                    }.onFailure { onMessage("تعذر استيراد الملف: ${it.message ?: "ملف غير صالح"}") }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        PathDialog("إنشاء مجلد", "المسار النسبي مثل src/components", folderPathInput, { folderPathInput = it }, onDismiss = { showCreateFolderDialog = false }) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { repository.createDirectory(project, folderPathInput) } }
                    .onSuccess { folder ->
                        folders = withContext(Dispatchers.IO) { repository.listFolders(project) }
                        showCreateFolderDialog = false
                        onMessage("تم إنشاء المجلد $folder")
                    }
                    .onFailure { onMessage("تعذر إنشاء المجلد: ${it.message ?: "مسار غير صالح"}") }
            }
        }
    }

    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("حذف مجلد فارغ؟", color = Ink) },
            text = { Text("سيُحذف المجلد $folder إذا كان فارغًا فقط. لن تُحذف الملفات الموجودة داخله.", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { repository.deleteEmptyDirectory(project, folder) } }
                            .onSuccess {
                                folders = withContext(Dispatchers.IO) { repository.listFolders(project) }
                                files = withContext(Dispatchers.IO) { repository.listFiles(project) }
                                onMessage("تم حذف المجلد الفارغ $folder")
                            }
                            .onFailure { onMessage("لم يُحذف المجلد: ${it.message ?: "خطأ"}") }
                        folderToDelete = null
                    }
                }) { Text("حذف", color = Color(0xFFE08B91)) }
            },
            dismissButton = { TextButton(onClick = { folderToDelete = null }) { Text("إلغاء") } },
            containerColor = Panel,
            titleContentColor = Ink,
            textContentColor = Muted
        )
    }

    if (showRenameDialog) {
        PathDialog("إعادة تسمية الملف", "اكتب المسار الجديد", pathInput, { pathInput = it }, onDismiss = { showRenameDialog = false }) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { repository.saveFile(project, selectedPath, source); repository.renameFile(project, selectedPath, pathInput) } }
                    .onSuccess { renamed ->
                        val previousPath = selectedPath
                        selectedPath = renamed
                        openTabs = openTabs.map { if (it == previousPath) renamed else it }
                        files = withContext(Dispatchers.IO) { repository.listFiles(project) }; folders = withContext(Dispatchers.IO) { repository.listFolders(project) }
                        showRenameDialog = false
                        onMessage("تمت إعادة تسمية الملف.")
                    }
                    .onFailure { onMessage("تعذرت إعادة التسمية: ${it.message ?: "خطأ"}") }
            }
        }
    }

    if (showDeleteFileDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteFileDialog = false },
            title = { Text("حذف الملف؟", color = Ink) },
            text = { Text("سيتم حذف $selectedPath من المشروع نهائيًا.", color = Muted) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { repository.deleteFile(project, selectedPath) } }
                            .onSuccess {
                                files = withContext(Dispatchers.IO) { repository.listFiles(project) }; folders = withContext(Dispatchers.IO) { repository.listFolders(project) }
                                openTabs = openTabs.filterNot { it == selectedPath }.ifEmpty { listOf("src/main.nawa") }
                                selectedPath = "src/main.nawa"
                                source = withContext(Dispatchers.IO) { repository.readSource(project) }
                                isDirty = false
                                showDeleteFileDialog = false
                            }
                            .onFailure { onMessage("تعذر حذف الملف: ${it.message ?: "خطأ"}") }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB84C54))) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { showDeleteFileDialog = false }) { Text("إلغاء") } },
            containerColor = Panel
        )
    }

    if (showEditElementDialog && editingElement != null) {
        val targetElement = editingElement!!
        AlertDialog(
            onDismissRequest = { showEditElementDialog = false },
            title = { Text(when (targetElement) { is NawaElement.Text -> "تعديل النص"; is NawaElement.Button -> "تعديل الزر"; is NawaElement.Control -> "تعديل عنصر ${targetElement.kind.keyword}" }, color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("سيتم تحديث العنصر في ملف المصدر نفسه، مع الحفاظ على بقية الكود.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    OutlinedTextField(
                        value = editElementContent,
                        onValueChange = { editElementContent = it.take(500) },
                        label = { Text(if (targetElement is NawaElement.Text) "محتوى النص" else "اسم الزر") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (targetElement is NawaElement.Button) {
                        OutlinedTextField(
                            value = editElementAction,
                            onValueChange = { editElementAction = it.take(200) },
                            label = { Text("الإجراء البرمجي") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                    }
                    Text("الموضع المطلق بوحدة dp من الحافة العلوية اليسرى. اترك X وY فارغين لاستخدام الترتيب الرأسي المعتاد.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = designX, onValueChange = { designX = it.filter(Char::isDigit).take(4) }, label = { Text("X (dp)") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = designY, onValueChange = { designY = it.filter(Char::isDigit).take(4) }, label = { Text("Y (dp)") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
            },
            confirmButton = {
                Button(enabled = editElementContent.isNotBlank(), onClick = {
                    runCatching {
                        val x = designX.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                        val y = designY.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                        require((x == null) == (y == null)) { "أدخل X وY معًا، أو اتركهما فارغين." }
                        if (x != null && y != null) require(x in 0..2048 && y in 0..4096) { "النطاق المسموح: X من 0 إلى 2048 وY من 0 إلى 4096." }
                        val positionSuffix = if (x != null && y != null) ".at($x, $y)" else ""
                        val replacement = when (targetElement) {
                            is NawaElement.Text -> "text(${NawaSourceEditor.quote(editElementContent)})$positionSuffix"
                            is NawaElement.Button -> {
                                if (targetElement.action != null) require(editElementAction.isNotBlank()) { "الزر يعمل حاليًا؛ لا تترك الإجراء فارغًا." }
                                if (editElementAction.isBlank()) "button(${NawaSourceEditor.quote(editElementContent)})$positionSuffix"
                                else "button(${NawaSourceEditor.quote(editElementContent)}) { ${editElementAction.trim()} }$positionSuffix"
                            }
                            is NawaElement.Control -> {
                                val extra = if (targetElement.kind == NawaControlKind.SLIDER) ", ${targetElement.minimum}, ${targetElement.maximum}" else ""
                                "${targetElement.kind.keyword}(${NawaSourceEditor.quote(editElementContent)}, ${targetElement.stateName}$extra)$positionSuffix"
                            }
                        }
                        val updated = NawaSourceEditor.replaceRange(source, targetElement.sourceStart, targetElement.sourceEnd, replacement)
                        val parsed = NawaParser.parse(updated)
                        require(!parsed.hasErrors) { "التعديل غير صالح: ${parsed.diagnostics.firstOrNull { it.severity == com.nawa.studio.core.NawaDiagnostic.Severity.ERROR }?.message ?: "خطأ في اللغة"}" }
                        val changed = parsed.screens.flatMap { it.elements }.firstOrNull { it.sourceStart == targetElement.sourceStart }
                            ?: parsed.elements.firstOrNull { it.sourceStart == targetElement.sourceStart }
                        when (targetElement) {
                            is NawaElement.Text -> require(changed is NawaElement.Text && changed.value == editElementContent) { "لم أستطع التحقق من النص بعد التعديل." }
                            is NawaElement.Button -> {
                                require(changed is NawaElement.Button && changed.label == editElementContent) { "لم أستطع التحقق من الزر بعد التعديل." }
                                if (targetElement.action != null) require(changed.isExecutable) { "إجراء الزر الجديد غير مدعوم." }
                            }
                            is NawaElement.Control -> require(changed is NawaElement.Control && changed.label == editElementContent && changed.kind == targetElement.kind && changed.stateName == targetElement.stateName) { "لم أستطع التحقق من عنصر الواجهة بعد التعديل." }
                        }
                        updated
                    }.onSuccess {
                        source = it
                        isDirty = true
                        status = "تم تعديل العنصر في المصدر"
                        showEditElementDialog = false
                    }.onFailure { onMessage("تعذر تعديل العنصر: ${it.message ?: "خطأ في المصدر"}") }
                }) { Text("حفظ التعديل") }
            },
            dismissButton = { TextButton(onClick = { showEditElementDialog = false }) { Text("إلغاء") } },
            containerColor = Panel
        )
    }

    if (showAddTextDialog) {
        AlertDialog(
            onDismissRequest = { showAddTextDialog = false },
            title = { Text("إضافة عنصر نصي", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيُضاف عنصر text إلى المصدر الحقيقي للشاشة.", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(value = designTextInput, onValueChange = { designTextInput = it.take(500) }, label = { Text("النص") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(enabled = designTextInput.isNotBlank(), onClick = {
                    runCatching {
                        val updated = NawaSourceEditor.insertElement(source, "text(${NawaSourceEditor.quote(designTextInput)})", designScreenName)
                        require(!NawaParser.parse(updated).hasErrors) { "أصلح أخطاء المصدر قبل إضافة عنصر جديد." }
                        updated
                    }.onSuccess {
                        source = it
                        isDirty = true
                        status = "أُضيف عنصر نصي إلى المصدر"
                        showAddTextDialog = false
                    }.onFailure { onMessage("تعذرت إضافة العنصر: ${it.message ?: "خطأ في بنية الشاشة"}") }
                }) { Text("إضافة إلى الواجهة") }
            },
            dismissButton = { TextButton(onClick = { showAddTextDialog = false }) { Text("إلغاء") } },
            containerColor = Panel
        )
    }

    if (showAddButtonDialog) {
        AlertDialog(
            onDismissRequest = { showAddButtonDialog = false },
            title = { Text("إضافة زر وظيفي", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اكتب إجراءً من إجراءات NawaScript المدعومة. لن تتم إضافة الزر إذا لم يستطع المحرك تنفيذه.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    OutlinedTextField(value = designButtonLabel, onValueChange = { designButtonLabel = it.take(100) }, label = { Text("اسم الزر") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = designButtonAction, onValueChange = { designButtonAction = it.take(200) }, label = { Text("الإجراء البرمجي") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("مثال: count = count + 1  ·  name = \"Nawa\"  ·  enabled = !enabled", color = Muted, fontSize = 10.sp, lineHeight = 16.sp)
                }
            },
            confirmButton = {
                Button(enabled = designButtonLabel.isNotBlank() && designButtonAction.isNotBlank(), onClick = {
                    runCatching {
                        val updated = NawaSourceEditor.insertElement(
                            source,
                            "button(${NawaSourceEditor.quote(designButtonLabel)}) { ${designButtonAction.trim()} }",
                            designScreenName
                        )
                        val updatedProgram = NawaParser.parse(updated)
                        val added = updatedProgram.screens.firstOrNull { it.name == designScreenName }
                            ?.elements?.filterIsInstance<NawaElement.Button>()?.lastOrNull()
                        require(!updatedProgram.hasErrors && added?.isExecutable == true) {
                            "المصدر يحتوي على أخطاء، أو أن الإجراء غير مدعوم/لا يتوافق مع نوع الحالة. لم أضف زرًا غير صالح."
                        }
                        updated
                    }.onSuccess {
                        source = it
                        isDirty = true
                        status = "أُضيف زر قابل للتنفيذ إلى المصدر"
                        showAddButtonDialog = false
                    }.onFailure { onMessage("تعذرت إضافة الزر: ${it.message ?: "إجراء غير صالح"}") }
                }) { Text("إضافة زر") }
            },
            dismissButton = { TextButton(onClick = { showAddButtonDialog = false }) { Text("إلغاء") } },
            containerColor = Panel
        )
    }

    if (showAddControlDialog) {
        val kind = addingControlKind
        val currentScreenStates = program.screens.firstOrNull { it.name == designScreenName }?.states ?: program.states
        val expectedStateType = when (kind) {
            NawaControlKind.INPUT -> "String"
            NawaControlKind.SWITCH, NawaControlKind.CHECKBOX -> "Bool"
            NawaControlKind.SLIDER -> "Int"
        }
        AlertDialog(
            onDismissRequest = { showAddControlDialog = false },
            title = { Text("إضافة عنصر ${kind.keyword}", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text("العنصر مربوط بحالة موجودة في الشاشة؛ تعديلات المستخدم على العنصر تغيّر تلك الحالة أثناء المعاينة والتطبيق المولّد.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    OutlinedTextField(value = designControlLabel, onValueChange = { designControlLabel = it.take(200) }, label = { Text("التسمية") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = designControlState, onValueChange = { designControlState = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(80) }, label = { Text("اسم الحالة من النوع $expectedStateType") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (kind == NawaControlKind.SLIDER) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = designControlMin, onValueChange = { designControlMin = it.filter { ch -> ch == '-' || ch.isDigit() }.take(10) }, label = { Text("أقل قيمة") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = designControlMax, onValueChange = { designControlMax = it.filter { ch -> ch == '-' || ch.isDigit() }.take(10) }, label = { Text("أعلى قيمة") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                    }
                    val compatibleStates = currentScreenStates.filter { state ->
                        when (kind) {
                            NawaControlKind.INPUT -> state.initialValue is NawaValue.StringValue
                            NawaControlKind.SWITCH, NawaControlKind.CHECKBOX -> state.initialValue is NawaValue.BooleanValue
                            NawaControlKind.SLIDER -> state.initialValue is NawaValue.NumberValue
                        }
                    }
                    Text(
                        if (compatibleStates.isEmpty()) "لا توجد حالة من النوع المطلوب في الشاشة. أضف state من النوع $expectedStateType في الكود أولًا."
                        else "حالات متوافقة: ${compatibleStates.joinToString { it.name }}",
                        color = if (compatibleStates.isEmpty()) Warning else Muted, fontSize = 10.sp, lineHeight = 15.sp
                    )
                }
            },
            confirmButton = {
                Button(enabled = designControlLabel.isNotBlank() && designControlState.isNotBlank(), onClick = {
                    runCatching {
                        val matchingState = currentScreenStates.firstOrNull { it.name == designControlState }
                            ?: throw IllegalArgumentException("الحالة «$designControlState» غير موجودة في الشاشة الحالية.")
                        val rightType = when (kind) {
                            NawaControlKind.INPUT -> matchingState.initialValue is NawaValue.StringValue
                            NawaControlKind.SWITCH, NawaControlKind.CHECKBOX -> matchingState.initialValue is NawaValue.BooleanValue
                            NawaControlKind.SLIDER -> matchingState.initialValue is NawaValue.NumberValue
                        }
                        require(rightType) { "التحكم ${kind.keyword} يحتاج حالة من النوع $expectedStateType." }
                        val range = if (kind == NawaControlKind.SLIDER) {
                            val min = designControlMin.toIntOrNull() ?: throw IllegalArgumentException("أدخل أقل قيمة صحيحة.")
                            val max = designControlMax.toIntOrNull() ?: throw IllegalArgumentException("أدخل أعلى قيمة صحيحة.")
                            require(min < max && min >= -1_000_000 && max <= 1_000_000) { "يجب أن تكون أقل قيمة أصغر من الأعلى، وبداخل نطاق ±1000000." }
                            ", $min, $max"
                        } else ""
                        val snippet = "${kind.keyword}(${NawaSourceEditor.quote(designControlLabel)}, $designControlState$range)"
                        val updated = NawaSourceEditor.insertElement(source, snippet, designScreenName)
                        val parsed = NawaParser.parse(updated)
                        require(!parsed.hasErrors) { parsed.diagnostics.firstOrNull { it.severity == com.nawa.studio.core.NawaDiagnostic.Severity.ERROR }?.message ?: "لم يجتز العنصر فحص اللغة." }
                        val added = parsed.screens.firstOrNull { it.name == designScreenName }?.elements?.lastOrNull()
                            ?: parsed.elements.lastOrNull()
                        require(added is NawaElement.Control && added.kind == kind && added.stateName == designControlState) { "لم أستطع التحقق من عنصر التحكم الجديد." }
                        updated
                    }.onSuccess {
                        source = it
                        isDirty = true
                        status = "تمت إضافة ${kind.keyword} وربطه بالحالة ${designControlState}"
                        showAddControlDialog = false
                    }.onFailure { onMessage("تعذرت إضافة عنصر التحكم: ${it.message ?: "راجع النوع والقيم"}") }
                }) { Text("إضافة العنصر") }
            },
            dismissButton = { TextButton(onClick = { showAddControlDialog = false }) { Text("إلغاء") } },
            containerColor = Panel,
            titleContentColor = Ink,
            textContentColor = Muted
        )
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("سجل النسخ السابقة", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$selectedPath · ${historyItems.size} نسخة محفوظة", color = Muted, fontSize = 11.sp)
                    Text("تُحفظ نسخة قبل تغيير الملف. عند الاستعادة، تُحفظ النسخة الحالية أيضًا حتى تستطيع الرجوع إليها.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    if (historyItems.isEmpty()) {
                        Card(colors = CardDefaults.cardColors(containerColor = PanelRaised), modifier = Modifier.fillMaxWidth()) {
                            Text("لا توجد نسخ سابقة لهذا الملف بعد. ستظهر هنا بعد أول تعديل محفوظ.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                        }
                    }
                    historyItems.forEach { revision ->
                        Card(colors = CardDefaults.cardColors(containerColor = PanelRaised), border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(formatDate(revision.timestamp), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${revision.sizeBytes / 1024L} KB · ${revision.id.take(18)}", color = Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                                OutlinedButton(onClick = {
                                    pendingRevision = revision
                                    pendingRevisionPath = selectedPath
                                    showHistoryDialog = false
                                }) { Text("استعادة", fontSize = 11.sp) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistoryDialog = false }) { Text("إغلاق") } },
            containerColor = Panel,
            titleContentColor = Ink,
            textContentColor = Muted
        )
    }

    pendingRevision?.let { revision ->
        AlertDialog(
            onDismissRequest = { pendingRevision = null },
            title = { Text("استعادة نسخة سابقة؟", color = Ink, fontWeight = FontWeight.Bold) },
            text = { Text("سيُستبدل محتوى $pendingRevisionPath بتاريخ ${formatDate(revision.timestamp)}. ستُحفظ النسخة الحالية في السجل قبل الاستبدال.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp) },
            confirmButton = {
                Button(onClick = {
                    val restorePath = pendingRevisionPath
                    scope.launch {
                        runCatching {
                            val restored = withContext(Dispatchers.IO) { repository.restoreRevision(project, restorePath, revision.id) }
                            val updatedHistory = withContext(Dispatchers.IO) { repository.listRevisions(project, restorePath) }
                            restored to updatedHistory
                        }.onSuccess { (restored, updatedHistory) ->
                            if (selectedPath == restorePath) {
                                source = restored
                                isDirty = false
                                status = "تمت استعادة النسخة السابقة"
                                requestedJumpLine = null
                            }
                            historyItems = updatedHistory
                            files = withContext(Dispatchers.IO) { repository.listFiles(project) }; folders = withContext(Dispatchers.IO) { repository.listFolders(project) }
                            pendingRevision = null
                            onMessage("تمت استعادة النسخة. حُفظ المحتوى السابق في سجل النسخ.")
                        }.onFailure { onMessage("تعذرت استعادة النسخة: ${it.message ?: "خطأ"}") }
                    }
                }) { Text("استعادة النسخة") }
            },
            dismissButton = { TextButton(onClick = { pendingRevision = null }) { Text("إلغاء") } },
            containerColor = Panel,
            titleContentColor = Ink,
            textContentColor = Muted
        )
    }

    if (showAndroidSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showAndroidSettingsDialog = false },
            title = { Text("إعدادات تطبيق Android", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text("هذه الإعدادات تُستخدم عند تجهيز مشروع Gradle. اترك معرّف التطبيق فارغًا لإنشائه تلقائيًا.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    OutlinedTextField(value = androidLabel, onValueChange = { androidLabel = it.take(80) }, label = { Text("اسم التطبيق الظاهر") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = androidApplicationId, onValueChange = { androidApplicationId = it.take(120) }, label = { Text("Application ID اختياري") }, placeholder = { Text("com.example.myapp") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = androidVersionName, onValueChange = { androidVersionName = it.take(32) }, label = { Text("اسم الإصدار") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = androidVersionCode, onValueChange = { androidVersionCode = it.filter(Char::isDigit).take(10) }, label = { Text("رقم الإصدار") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = androidMinSdk, onValueChange = { androidMinSdk = it.filter(Char::isDigit).take(2) }, label = { Text("minSdk") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = androidTargetSdk, onValueChange = { androidTargetSdk = it.filter(Char::isDigit).take(2) }, label = { Text("targetSdk") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(
                        value = androidPermissionsText,
                        onValueChange = { androidPermissionsText = it.take(3000) },
                        label = { Text("صلاحيات Android — كل واحدة في سطر") },
                        placeholder = { Text("android.permission.INTERNET") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5
                    )
                    OutlinedTextField(
                        value = androidDependenciesText,
                        onValueChange = { androidDependenciesText = it.take(4000) },
                        label = { Text("اعتماديات Maven — كل واحدة في سطر") },
                        placeholder = { Text("group:artifact:version") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5
                    )
                    Text("الأمان: لا يقبل هذا الحقل شيفرة Gradle. اكتب الاعتماد بصيغة group:artifact:version، وتأكد أن الاعتمادية متاحة في Google Maven أو Maven Central.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            val config = NawaAndroidConfig(
                                label = androidLabel.trim(),
                                applicationId = androidApplicationId.trim(),
                                versionName = androidVersionName.trim(),
                                versionCode = androidVersionCode.toIntOrNull() ?: throw IllegalArgumentException("رقم الإصدار غير صالح."),
                                minSdk = androidMinSdk.toIntOrNull() ?: throw IllegalArgumentException("minSdk غير صالح."),
                                targetSdk = androidTargetSdk.toIntOrNull() ?: throw IllegalArgumentException("targetSdk غير صالح."),
                                permissions = androidPermissionsText.lines().flatMap { it.split(',') }.map(String::trim).filter(String::isNotEmpty).distinct(),
                                dependencies = androidDependenciesText.lines().flatMap { it.split(',') }.map(String::trim).filter(String::isNotEmpty).distinct()
                            ).validate()
                            withContext(Dispatchers.IO) { repository.saveFile(project, "android.properties", config.serialize()) }
                            config
                        }.onSuccess {
                            showAndroidSettingsDialog = false
                            status = "تم حفظ إعدادات Android"
                            onMessage("حُفظت الإعدادات. ستُطبق على مشروع Android القادم.")
                            files = withContext(Dispatchers.IO) { repository.listFiles(project) }; folders = withContext(Dispatchers.IO) { repository.listFolders(project) }
                        }.onFailure { onMessage("تعذّر حفظ إعدادات Android: ${it.message ?: "تحقق من القيم"}") }
                    }
                }) { Text("حفظ الإعدادات") }
            },
            dismissButton = { TextButton(onClick = { showAndroidSettingsDialog = false }) { Text("إلغاء") } },
            containerColor = Panel,
            titleContentColor = Ink,
            textContentColor = Muted
        )
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("بحث واستبدال", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = searchText, onValueChange = { searchText = it }, modifier = Modifier.fillMaxWidth(), label = { Text("النص المطلوب") }, singleLine = true)
                    OutlinedTextField(value = replaceText, onValueChange = { replaceText = it }, modifier = Modifier.fillMaxWidth(), label = { Text("استبدال بـ") }, singleLine = true)
                    Text(if (searchText.isBlank()) "اكتب نصًا لحساب التطابقات." else "التطابقات: ${Regex(Regex.escape(searchText)).findAll(source).count()}", color = Muted, fontSize = 12.sp)
                    OutlinedButton(
                        onClick = {
                            projectSearchQuery = searchText
                            projectSearchResults = emptyList()
                            projectSearchScanned = 0
                            projectSearchTruncated = false
                            showSearchDialog = false
                            showProjectSearchDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("البحث في جميع ملفات المشروع")
                    }
                }
            },
            confirmButton = {
                Button(enabled = searchText.isNotEmpty(), onClick = {
                    val changed = source.replace(searchText, replaceText)
                    source = changed
                    isDirty = true
                    showSearchDialog = false
                }) { Text("استبدال الكل") }
            },
            dismissButton = { TextButton(onClick = { showSearchDialog = false }) { Text("إغلاق") } },
            containerColor = Panel
        )
    }

    if (showProjectSearchDialog) {
        AlertDialog(
            onDismissRequest = { if (!projectSearchRunning) showProjectSearchDialog = false },
            title = { Text("بحث في المشروع بالكامل", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = projectSearchQuery,
                        onValueChange = { projectSearchQuery = it.take(160) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("ابحث في ملفات المصدر والنصوص") },
                        singleLine = true
                    )
                    Button(
                        enabled = projectSearchQuery.isNotBlank() && !projectSearchRunning,
                        onClick = {
                            projectSearchRunning = true
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) { repository.searchInFiles(project, projectSearchQuery) }
                                }.onSuccess { response ->
                                    projectSearchResults = response.matches
                                    projectSearchScanned = response.scannedFiles
                                    projectSearchTruncated = response.truncated
                                }.onFailure { onMessage("فشل البحث: ${it.message ?: "خطأ غير معروف"}") }
                                projectSearchRunning = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (projectSearchRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(if (projectSearchRunning) "جارٍ البحث…" else "بحث")
                    }
                    if (projectSearchScanned > 0 || projectSearchResults.isNotEmpty()) {
                        Text("${projectSearchResults.size} نتيجة · فُحص $projectSearchScanned ملفًا", color = Muted, fontSize = 11.sp)
                    }
                    if (projectSearchTruncated) {
                        Text("تم إيقاف عرض النتائج عند الحد الآمن. ضيّق عبارة البحث لنتائج أدق.", color = Warning, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    if (projectSearchResults.isEmpty() && projectSearchScanned > 0 && !projectSearchRunning) {
                        Text("لا توجد نتائج مطابقة.", color = Muted, fontSize = 12.sp)
                    }
                    projectSearchResults.forEach { result ->
                        Surface(
                            color = PanelRaised,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                showProjectSearchDialog = false
                                selectFile(result.path, result.lineNumber)
                            }
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${result.path}:${result.lineNumber}", color = Mint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text(result.snippet.ifBlank { "(سطر فارغ)" }, color = Ink, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Text("البحث نصّي وغير حساس لحالة الأحرف. تُفحص ملفات المصدر النصية المعروفة فقط، مع حدود للحجم وعدد النتائج.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showProjectSearchDialog = false }) { Text("إغلاق") } },
            containerColor = Panel,
            titleContentColor = Ink,
            textContentColor = Muted
        )
    }
}

@Composable
private fun NawaCodeEditor(
    source: String,
    path: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    jumpToLine: Int? = null,
    onJumpHandled: (Int) -> Unit = {}
) {
    val latestSource by rememberUpdatedState(source)
    val latestOnChange by rememberUpdatedState(onChange)
    val latestOnJumpHandled by rememberUpdatedState(onJumpHandled)
    var handledJumpLine by remember(path) { mutableStateOf<Int?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            CodeEditor(context).apply {
                typefaceText = Typeface.MONOSPACE
                setLineNumberEnabled(true)
                setPinLineNumber(true)
                setTabWidth(4)
                setHighlightCurrentLine(true)
                setHighlightCurrentBlock(true)
                setHighlightBracketPair(true)
                setScalable(true)
                setText(source)
                NawaTextMate.applyTo(this, path)
                subscribeAlways(ContentChangeEvent::class.java) { _ ->
                    val changed = text.toString()
                    if (changed != latestSource) latestOnChange(changed)
                }
            }
        },
        update = { editor ->
            if (editor.text.toString() != source) editor.setText(source)
            NawaTextMate.applyTo(editor, path)
            val requestedLine = jumpToLine
            if (requestedLine == null) {
                handledJumpLine = null
            } else if (handledJumpLine != requestedLine) {
                handledJumpLine = requestedLine
                editor.post {
                    val lastLine = (editor.lineCount - 1).coerceAtLeast(0)
                    val targetLine = (requestedLine - 1).coerceIn(0, lastLine)
                    runCatching { editor.setSelection(targetLine, 0) }
                    latestOnJumpHandled(requestedLine)
                }
            }
        },
        onRelease = { it.release() }
    )
}

@Composable
private fun PageTab(label: String, selected: Boolean, icon: @Composable () -> Unit, onClick: () -> Unit) {
    val container = if (selected) Color(0xFF173B35) else Panel
    val content = if (selected) Mint else Muted
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = container,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) Color(0xFF276452) else Border)
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides content) { icon() }
            Text(label, color = content, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

/** Long-press a design element and drag vertically to move it one source position. */
private fun Modifier.longPressReorder(
    elementKey: Int,
    source: String,
    screenName: String,
    thresholdPx: Float,
    onMove: (Int) -> Unit
): Modifier = pointerInput(elementKey, source, screenName, thresholdPx) {
    var totalDragY = 0f
    detectDragGesturesAfterLongPress(
        onDragStart = { totalDragY = 0f },
        onDragEnd = {
            if (kotlin.math.abs(totalDragY) >= thresholdPx) onMove(if (totalDragY < 0f) -1 else 1)
            totalDragY = 0f
        },
        onDragCancel = { totalDragY = 0f },
        onDrag = { change, dragAmount ->
            totalDragY += dragAmount.y
            change.consume()
        }
    )
}

/** Drag an item on the designer canvas; the final dp position is written back into NawaScript. */
private fun Modifier.longPressDragPosition(
    element: NawaElement,
    startX: Int,
    startY: Int,
    density: Float,
    onPosition: (NawaElement, Int, Int) -> Unit
): Modifier = pointerInput(element.sourceStart, element.sourceEnd, startX, startY, density) {
    var deltaX = 0f
    var deltaY = 0f
    detectDragGesturesAfterLongPress(
        onDragStart = { deltaX = 0f; deltaY = 0f },
        onDragEnd = {
            val targetX = (startX + deltaX / density).toInt().coerceIn(0, 2048)
            val targetY = (startY + deltaY / density).toInt().coerceIn(0, 4096)
            if (targetX != startX || targetY != startY) onPosition(element, targetX, targetY)
            deltaX = 0f
            deltaY = 0f
        },
        onDragCancel = { deltaX = 0f; deltaY = 0f },
        onDrag = { change, dragAmount ->
            deltaX += dragAmount.x
            deltaY += dragAmount.y
            change.consume()
        }
    )
}

@Composable
private fun DesignScreen(
    source: String,
    selectedScreenName: String,
    onSelectScreen: (String) -> Unit,
    onBackToCode: () -> Unit,
    onAddTextRequest: () -> Unit,
    onAddButtonRequest: () -> Unit,
    onAddControlRequest: (NawaControlKind) -> Unit,
    onPositionElementRequest: (NawaElement, Int, Int) -> Unit,
    onEditElementRequest: (NawaElement) -> Unit,
    onMoveElementRequest: (NawaElement, Int) -> Unit,
    onDeleteElementRequest: (NawaElement) -> Unit
) {
    val program = remember(source) { safeParseNawa(source) }
    val currentScreen = program.screens.firstOrNull { it.name == selectedScreenName }
        ?: program.screens.firstOrNull()
    val screenElements = currentScreen?.elements ?: program.elements
    val screenStates = currentScreen?.states ?: program.states
    val dragThresholdPx = with(LocalDensity.current) { 44.dp.toPx() }
    val density = LocalDensity.current.density
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).background(Color(0xFF173B35), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = Mint)
            }
            Column(Modifier.weight(1f)) {
                Text("مصمم الواجهة", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("${program.appName} · ${currentScreen?.name ?: program.screenName}", color = Muted, fontSize = 12.sp)
            }
        }
        Text("التغييرات تُكتب مباشرةً في src/main.nawa. اختر الشاشة التي تريد تعديلها. أضف العناصر وعدّلها واحذفها، واستخدم الأسهم أو اضغط مطولًا واسحب لأعلى/أسفل لنقل العنصر موضعًا واحدًا. معاينة التنقل ومصدّر Android يستخدمان المصدر نفسه.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        if (program.screens.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                program.screens.forEach { screen ->
                    Surface(
                        color = if (screen.name == currentScreen?.name) Color(0xFF173B35) else Panel,
                        shape = RoundedCornerShape(9.dp),
                        border = BorderStroke(1.dp, if (screen.name == currentScreen?.name) Color(0xFF276452) else Border),
                        modifier = Modifier.clickable { onSelectScreen(screen.name) }
                    ) { Text(screen.name, color = if (screen.name == currentScreen?.name) Mint else Ink, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 12.sp) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onAddTextRequest, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp)); Text("إضافة نص")
            }
            OutlinedButton(onClick = onAddButtonRequest, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp)); Text("إضافة زر")
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                NawaControlKind.INPUT to "حقل إدخال",
                NawaControlKind.SWITCH to "مفتاح",
                NawaControlKind.CHECKBOX to "مربع اختيار",
                NawaControlKind.SLIDER to "شريط قيمة"
            ).forEach { (kind, label) ->
                OutlinedButton(onClick = { onAddControlRequest(kind) }) { Text(label, fontSize = 11.sp) }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FBFD)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Border)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(program.appName, color = Color(0xFF142330), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(currentScreen?.name ?: program.screenName, color = Color(0xFF536577), fontSize = 12.sp)
                Divider(color = Color(0xFFE4EAF0))
                Text("مساحة التصميم · اضغط على العنصر لتعديل النص والموقع", color = Color(0xFF536577), fontSize = 11.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Box(
                        Modifier.width((screenElements.map { (it.x ?: 14) + 300 }.maxOrNull() ?: 340).coerceIn(340, 4200).dp)
                            .height((screenElements.mapIndexed { index, element -> (element.y ?: (14 + index * 56)) + 90 }.maxOrNull() ?: 340).coerceIn(340, 4200).dp)
                            .background(Color(0xFFF0F5F8), RoundedCornerShape(16.dp))
                            .border(BorderStroke(1.dp, Color(0xFFD9E4EB)), RoundedCornerShape(16.dp))
                    ) {
                    screenElements.forEachIndexed { index, element ->
                        val x = element.x ?: 14
                        val y = element.y ?: (14 + index * 56)
                        when (element) {
                            is NawaElement.Text -> Surface(
                                color = Color.White, shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFB9CBD8)),
                                modifier = Modifier.absoluteOffset(x.dp, y.dp).widthIn(max = 275.dp)
                                    .clickable { onEditElementRequest(element) }
                                    .longPressDragPosition(element, x, y, density, onPositionElementRequest)
                            ) { Text(NawaParser.interpolate(element.value, screenStates.associate { it.name to it.initialValue }), color = Color(0xFF263747), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) }
                            is NawaElement.Button -> Surface(
                                color = Color(0xFF177D62), shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.absoluteOffset(x.dp, y.dp).widthIn(max = 240.dp)
                                    .clickable { onEditElementRequest(element) }
                                    .longPressDragPosition(element, x, y, density, onPositionElementRequest)
                            ) { Text(element.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
                            is NawaElement.Control -> Surface(
                                color = Color(0xFFF7FBFD), shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF87B9AD)),
                                modifier = Modifier.absoluteOffset(x.dp, y.dp).widthIn(max = 260.dp)
                                    .clickable { onEditElementRequest(element) }
                                    .longPressDragPosition(element, x, y, density, onPositionElementRequest)
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(element.label, color = Color(0xFF263747), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(element.kind.keyword, color = Color(0xFF177D62), fontSize = 9.sp)
                                }
                            }
                        }
                    }
                    }
                }
                screenElements.forEachIndexed { index, element ->
                    when (element) {
                        is NawaElement.Text -> Row(
                            modifier = Modifier.fillMaxWidth().longPressReorder(
                                elementKey = element.sourceStart,
                                source = source,
                                screenName = currentScreen?.name.orEmpty(),
                                thresholdPx = dragThresholdPx,
                                onMove = { direction -> onMoveElementRequest(element, direction) }
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Filled.Code, contentDescription = null, tint = Color(0xFF177D62), modifier = Modifier.size(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(NawaParser.interpolate(element.value, screenStates.associate { it.name to it.initialValue }), color = Color(0xFF263747), fontSize = 14.sp, lineHeight = 20.sp)
                                Text("نص ${index + 1}", color = Color(0xFF7A8B99), fontSize = 9.sp)
                            }
                            IconButton(onClick = { onMoveElementRequest(element, -1) }, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                                Text("↑", color = if (index > 0) Color(0xFF177D62) else Color(0xFFB5C0C9), fontSize = 17.sp)
                            }
                            IconButton(onClick = { onMoveElementRequest(element, 1) }, enabled = index < screenElements.lastIndex, modifier = Modifier.size(28.dp)) {
                                Text("↓", color = if (index < screenElements.lastIndex) Color(0xFF177D62) else Color(0xFFB5C0C9), fontSize = 17.sp)
                            }
                            IconButton(onClick = { onEditElementRequest(element) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Edit, contentDescription = "تعديل النص", tint = Color(0xFF177D62), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onDeleteElementRequest(element) }, enabled = screenElements.size > 1, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف النص", tint = Color(0xFFB84C54), modifier = Modifier.size(16.dp))
                            }
                        }
                        is NawaElement.Button -> Surface(
                            color = Color(0xFFE4F8F0),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFB6E8D6)),
                            modifier = Modifier.fillMaxWidth().longPressReorder(
                                elementKey = element.sourceStart,
                                source = source,
                                screenName = currentScreen?.name.orEmpty(),
                                thresholdPx = dragThresholdPx,
                                onMove = { direction -> onMoveElementRequest(element, direction) }
                            )
                        ) {
                            Row(Modifier.padding(start = 12.dp, end = 5.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF177D62), modifier = Modifier.size(17.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(element.label, color = Color(0xFF185C4A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(if (element.isExecutable) "قابل للتنفيذ" else "إجراء غير مدعوم", color = if (element.isExecutable) Color(0xFF177D62) else Color(0xFFB05B32), fontSize = 9.sp)
                                }
                                IconButton(onClick = { onMoveElementRequest(element, -1) }, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                                    Text("↑", color = if (index > 0) Color(0xFF177D62) else Color(0xFFB5C0C9), fontSize = 17.sp)
                                }
                                IconButton(onClick = { onMoveElementRequest(element, 1) }, enabled = index < screenElements.lastIndex, modifier = Modifier.size(28.dp)) {
                                    Text("↓", color = if (index < screenElements.lastIndex) Color(0xFF177D62) else Color(0xFFB5C0C9), fontSize = 17.sp)
                                }
                                IconButton(onClick = { onEditElementRequest(element) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Edit, contentDescription = "تعديل الزر", tint = Color(0xFF177D62), modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onDeleteElementRequest(element) }, enabled = screenElements.size > 1, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف الزر", tint = Color(0xFFB84C54), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        is NawaElement.Control -> Surface(
                            color = Color(0xFFE9F4F1),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFB6DAD0)),
                            modifier = Modifier.fillMaxWidth().clickable { onEditElementRequest(element) }
                        ) {
                            Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(element.label, color = Color(0xFF185C4A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${element.kind.keyword} · state ${element.stateName}${if (element.x != null && element.y != null) " · (${element.x}, ${element.y})" else " · تدفق رأسي"}", color = Color(0xFF536577), fontSize = 10.sp)
                                }
                                IconButton(onClick = { onEditElementRequest(element) }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Filled.Edit, contentDescription = "تعديل ${element.kind.keyword}", tint = Color(0xFF177D62), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                if (screenElements.isEmpty()) Text("أضف أول عنصر من الأزرار بالأعلى.", color = Color(0xFF536577), fontSize = 13.sp)
            }
        }
        if (program.diagnostics.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF30291D)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ملاحظات على المصدر", color = Warning, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    program.diagnostics.take(5).forEach { Text("• $it", color = Color(0xFFE8D6B6), fontSize = 11.sp, lineHeight = 16.sp) }
                }
            }
        }
        OutlinedButton(onClick = onBackToCode, modifier = Modifier.fillMaxWidth()) { Text("فتح الكود مباشرة") }
    }
}

@Composable
private fun PreviewScreen(source: String, onBackToCode: () -> Unit) {
    val program = remember(source) { safeParseNawa(source) }
    val screens = program.screens.ifEmpty {
        listOf(com.nawa.studio.core.NawaScreen(program.screenName, program.states, program.elements, 0, source.length, program.diagnostics))
    }
    var currentScreenName by remember(source) { mutableStateOf(screens.firstOrNull()?.name ?: program.screenName) }
    var previewRunKey by remember(source) { mutableStateOf(0) }
    var runtimeTrace by remember(source) { mutableStateOf<List<NawaTraceEvent>>(emptyList()) }
    var runtimeWarning by remember(source) { mutableStateOf<String?>(null) }
    val valuesByScreen = remember(source, previewRunKey) {
        screens.associate { screen ->
            screen.name to mutableStateMapOf<String, NawaValue>().apply {
                putAll(screen.states.associate { it.name to it.initialValue })
            }
        }
    }
    val currentScreen = screens.firstOrNull { it.name == currentScreenName } ?: screens.firstOrNull()
    val values = currentScreen?.let { valuesByScreen[it.name] }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp).background(Color(0xFF173B35), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Mint)
            }
            Column(Modifier.weight(1f)) {
                Text("معاينة محلية", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("${program.appName} · ${currentScreen?.name ?: program.screenName}", color = Muted, fontSize = 12.sp)
            }
            Box(Modifier.background(Color(0xFF173B35), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 6.dp)) { Text("LOCAL", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                previewRunKey++
                currentScreenName = screens.firstOrNull()?.name ?: program.screenName
                runtimeTrace = emptyList()
                runtimeWarning = null
            }, modifier = Modifier.weight(1f)) { Text("إعادة التشغيل") }
            OutlinedButton(onClick = { runtimeTrace = emptyList(); runtimeWarning = null }, modifier = Modifier.weight(1f)) { Text("مسح سجل التنفيذ") }
        }
        if (!program.canPreview || currentScreen == null || values == null) {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("لا يمكن تشغيل المعاينة", color = Ink, fontWeight = FontWeight.Bold)
                    Text("أصلح أخطاء اللغة أو أضف عنصرًا مدعومًا إلى إحدى الشاشات.", color = Muted, fontSize = 13.sp)
                    Button(onClick = onBackToCode) { Text("العودة إلى الكود") }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FBFD)), shape = RoundedCornerShape(26.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp), horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(48.dp).background(Color(0xFFE4F8F0), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Code, contentDescription = null, tint = Color(0xFF177D62), modifier = Modifier.size(26.dp))
                    }
                    Text(program.appName, color = Color(0xFF142330), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(currentScreen.name, color = Color(0xFF536577), fontSize = 12.sp)
                    Divider(color = Color(0xFFE4EAF0))
                    val hasPositionedElements = currentScreen.elements.any { it.x != null && it.y != null }
                    if (hasPositionedElements) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Box(Modifier.width((currentScreen.elements.map { (it.x ?: 12) + 300 }.maxOrNull() ?: 440).coerceIn(340, 4200).dp).height((currentScreen.elements.mapIndexed { index, element -> (element.y ?: (12 + index * 64)) + 90 }.maxOrNull() ?: 440).coerceIn(440, 4200).dp).background(Color(0xFFF0F5F8), RoundedCornerShape(16.dp))) {
                            currentScreen.elements.forEachIndexed { index, element ->
                                val x = element.x ?: 12
                                val y = element.y ?: (12 + index * 64)
                                PreviewElement(element, values, onButtonClick = { button ->
                                    button.action?.let { action ->
                                        val result = NawaParser.executeTraced(action, values)
                                        runtimeTrace = (runtimeTrace + result.events).takeLast(24)
                                        runtimeWarning = result.warning
                                        result.navigationTarget?.let { target -> if (screens.any { it.name == target }) currentScreenName = target }
                                    }
                                }, modifier = Modifier.absoluteOffset(x.dp, y.dp).widthIn(max = 270.dp))
                            }
                        }
                        }
                    } else {
                        currentScreen.elements.forEach { element ->
                            PreviewElement(element, values, onButtonClick = { button ->
                                button.action?.let { action ->
                                    val result = NawaParser.executeTraced(action, values)
                                    runtimeTrace = (runtimeTrace + result.events).takeLast(24)
                                    runtimeWarning = result.warning
                                    result.navigationTarget?.let { target -> if (screens.any { it.name == target }) currentScreenName = target }
                                }
                            }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (currentScreen.name != screens.first().name) {
                        OutlinedButton(onClick = { currentScreenName = screens.first().name }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp)); Text("العودة إلى ${screens.first().name}")
                        }
                    }
                }
            }
        }
        if (runtimeTrace.isNotEmpty() || runtimeWarning != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                border = BorderStroke(1.dp, Border),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سجل التنفيذ / Debug Trace", color = Mint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    runtimeWarning?.let { Text(it, color = Warning, fontSize = 11.sp, lineHeight = 16.sp) }
                    runtimeTrace.takeLast(20).forEach { event ->
                        Column(Modifier.fillMaxWidth().background(PanelRaised, RoundedCornerShape(9.dp)).padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${event.step}. ${event.kind}", color = Color(0xFF9EDCCB), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            Text(event.detail, color = Ink, fontSize = 11.sp, lineHeight = 15.sp)
                            if (event.stateSnapshot.isNotEmpty()) {
                                Text(event.stateSnapshot.entries.joinToString(" · ") { "${it.key}=${it.value.display()}" }, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                            }
                        }
                    }
                }
            }
        }
        if (program.diagnostics.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF30291D)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Warning)
                        Text("ملاحظات المحرك", color = Warning, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    program.diagnostics.forEach { diagnostic ->
                        Text("• $diagnostic", color = Color(0xFFE8D6B6), fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
        Text("المعاينة تنفّذ NawaScript المدعومة محليًا، وتسجل تغييرات الحالة والشروط والحلقات والتنقل. لا تشغّل كود Kotlin/Java عشوائيًا.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun PreviewElement(
    element: NawaElement,
    values: MutableMap<String, NawaValue>,
    onButtonClick: (NawaElement.Button) -> Unit,
    modifier: Modifier = Modifier
) {
    when (element) {
        is NawaElement.Text -> Text(
            NawaParser.interpolate(element.value, values), color = Color(0xFF263747), fontSize = 15.sp,
            lineHeight = 22.sp, modifier = modifier
        )
        is NawaElement.Button -> Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = { onButtonClick(element) },
                enabled = element.isExecutable,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF177D62), contentColor = Color.White,
                    disabledContainerColor = Color(0xFFD7E0E7), disabledContentColor = Color(0xFF536577)
                )
            ) { Text(element.label) }
            if (!element.isExecutable) Text("هذا الزر بلا إجراء مدعوم؛ لن ينفّذ شيئًا.", color = Color(0xFF8A5A1D), fontSize = 11.sp)
        }
        is NawaElement.Control -> when (element.kind) {
            NawaControlKind.INPUT -> OutlinedTextField(
                value = (values[element.stateName] as? NawaValue.StringValue)?.value.orEmpty(),
                onValueChange = { values[element.stateName] = NawaValue.StringValue(it.take(2_000)) },
                label = { Text(element.label) }, modifier = modifier, singleLine = true
            )
            NawaControlKind.SWITCH -> Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(element.label, modifier = Modifier.weight(1f), color = Color(0xFF263747), fontSize = 13.sp)
                Switch(
                    checked = (values[element.stateName] as? NawaValue.BooleanValue)?.value ?: false,
                    onCheckedChange = { values[element.stateName] = NawaValue.BooleanValue(it) }
                )
            }
            NawaControlKind.CHECKBOX -> Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Checkbox(
                    checked = (values[element.stateName] as? NawaValue.BooleanValue)?.value ?: false,
                    onCheckedChange = { values[element.stateName] = NawaValue.BooleanValue(it) }
                )
                Text(element.label, color = Color(0xFF263747), fontSize = 13.sp)
            }
            NawaControlKind.SLIDER -> {
                val minimum = element.minimum ?: 0
                val maximum = element.maximum ?: 100
                val value = (values[element.stateName] as? NawaValue.NumberValue)?.value ?: minimum
                Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${element.label}: ${value.coerceIn(minimum, maximum)}", color = Color(0xFF263747), fontSize = 12.sp)
                    Slider(
                        value = value.toFloat().coerceIn(minimum.toFloat(), maximum.toFloat()),
                        onValueChange = { values[element.stateName] = NawaValue.NumberValue(it.toInt().coerceIn(minimum, maximum)) },
                        valueRange = minimum.toFloat()..maximum.toFloat()
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen(
    source: String,
    path: String,
    project: NawaProject,
    repository: ProjectRepository,
    onJumpToCode: () -> Unit
) {
    val program = remember(source, path) { safeParseNawa(source) }
    val workspaceFindings = remember(project.id, source, path) {
        NawaProjectAudit.scan(project.directory, if (path == "src/main.nawa") source else null)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("فحص المشروع", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val auditErrors = workspaceFindings.count { it.severity == NawaProjectFinding.Severity.ERROR }
                val auditWarnings = workspaceFindings.count { it.severity == NawaProjectFinding.Severity.WARNING }
                Text("فحص مساحة العمل كاملة", color = Mint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("${workspaceFindings.size} ملاحظة · $auditErrors خطأ · $auditWarnings تحذير", color = Muted, fontSize = 11.sp)
                if (workspaceFindings.isEmpty()) {
                    Text("لم يتم العثور على مشكلة ضمن حدود الفحص الساكن.", color = Ink, fontSize = 12.sp)
                } else {
                    workspaceFindings.take(60).forEach { finding ->
                        Column(Modifier.fillMaxWidth().background(PanelRaised, RoundedCornerShape(9.dp)).padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${finding.severity}${finding.line?.let { " · السطر $it" } ?: ""} · ${finding.path.ifBlank { "المشروع" }}", color = when (finding.severity) {
                                NawaProjectFinding.Severity.ERROR -> Color(0xFFFF9AA3)
                                NawaProjectFinding.Severity.WARNING -> Warning
                                NawaProjectFinding.Severity.INFO -> Mint
                            }, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(finding.message, color = Ink, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                    if (workspaceFindings.size > 60) Text("عُرضت أول 60 ملاحظة فقط.", color = Muted, fontSize = 10.sp)
                }
                Text("الفحص ساكن ولا يشغّل كود المستخدم أو سكربتات Gradle. البناء الفعلي يظل هو الحكم على صحة Kotlin/Java.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
        if (path != "src/main.nawa") {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ملف مصدر أصلي", color = Mint, fontWeight = FontWeight.Bold)
                    Text("فاحص NawaScript لا يحلل هذا الملف. تُفحص ملفات Kotlin/Java بواسطة Gradle عند تجهيز مشروع Android؛ راجع سجل البناء الخارجي لمعرفة أخطاء المترجم.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                    Text("الملف الحالي: $path", color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
            OutlinedButton(onClick = onJumpToCode, modifier = Modifier.fillMaxWidth()) { Text("العودة للمحرر") }
            return@Column
        }
        val errors = program.diagnostics.count { it.severity == com.nawa.studio.core.NawaDiagnostic.Severity.ERROR }
        val warnings = program.diagnostics.count { it.severity == com.nawa.studio.core.NawaDiagnostic.Severity.WARNING }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (errors == 0) "لم يتم رصد أخطاء في القواعد المدعومة" else "يوجد $errors خطأ", color = if (errors == 0) Mint else Warning, fontWeight = FontWeight.Bold)
                Text("تحذيرات: $warnings · عناصر: ${program.elements.size} · حالات: ${program.states.size}", color = Muted, fontSize = 12.sp)
                Text("هذا فحص ساكن للمجموعة المدعومة، وليس مترجمًا عامًا.", color = Muted, fontSize = 11.sp)
            }
        }
        if (program.diagnostics.isEmpty()) {
            Text("لا توجد ملاحظات.", color = Muted)
        } else {
            program.diagnostics.forEach { diagnostic ->
                Card(colors = CardDefaults.cardColors(containerColor = if (diagnostic.severity == com.nawa.studio.core.NawaDiagnostic.Severity.ERROR) Color(0xFF352327) else Color(0xFF30291D)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(13.dp)) {
                        Text("${diagnostic.severity} · السطر ${diagnostic.line}", color = if (diagnostic.severity == com.nawa.studio.core.NawaDiagnostic.Severity.ERROR) Color(0xFFFF9AA3) else Warning, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(diagnostic.message, color = Ink, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        OutlinedButton(onClick = onJumpToCode, modifier = Modifier.fillMaxWidth()) { Text("العودة للمحرر") }
    }
}

@Composable
private fun PathDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Ink, fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
        confirmButton = { Button(onClick = onConfirm) { Text("تنفيذ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
        containerColor = Panel
    )
}

private fun formatDate(timestamp: Long): String = runCatching {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
}.getOrDefault("—")

private fun isBinaryPath(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in setOf(
    "png", "jpg", "jpeg", "webp", "gif", "bmp", "tif", "tiff", "ico", "mp3", "m4a", "wav", "ogg", "flac", "mp4", "mkv", "webm", "mov", "pdf", "zip", "jar", "aar", "apk", "dex", "class", "so", "ttf", "otf", "woff", "woff2", "bin", "dat", "db", "sqlite", "keystore", "jks"
)

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()
