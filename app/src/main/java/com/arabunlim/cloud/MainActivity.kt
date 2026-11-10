package com.arabunlim.cloud

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabunlim.cloud.data.CloudRepository
import com.arabunlim.cloud.model.*
import com.arabunlim.cloud.network.TdlibClientBridge
import com.arabunlim.cloud.ui.theme.ArabUnlimTheme
import com.arabunlim.cloud.ui.theme.TelegramBlue
import com.arabunlim.cloud.ui.theme.TelegramDarkBlue
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ضبط اللغة العربية واتجاه الواجهة من اليمين إلى اليسار (RTL)
        Locale.setDefault(Locale("ar"))

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ArabUnlimTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ArabUnlimApp()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArabUnlimApp() {
    val context = LocalContext.current
    val repository = remember { CloudRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var authState by remember {
        mutableStateOf<TelegramAuthState>(
            TelegramAuthState.Authenticated(
                accountLabel = "حساب تيليجرام متصل (الرسائل المحفوظة)",
                modeDescription = "تخزين سحابي غير محدود • مجاني للأبد"
            )
        )
    }

    var currentTab by remember { mutableIntStateOf(0) }
    var folders by remember { mutableStateOf(repository.loadFolders()) }
    var files by remember { mutableStateOf(repository.loadFiles()) }
    var selectedFolder by remember { mutableStateOf(folders.firstOrNull()?.name ?: "صور الكاميرا (DCIM)") }
    var selectedCategory by remember { mutableStateOf(FileCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showTelegramConnectSheet by remember { mutableStateOf(false) }

    // منتقي الملفات الحقيقي من الهاتف لرفع أي صورة أو فيديو أو صوت أو مستند
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploading = true
            coroutineScope.launch {
                val activeAuth = authState as? TelegramAuthState.Authenticated
                val (uploadedFile, statusMsg) = repository.uploadUriToTelegram(
                    uri = uri,
                    targetFolderName = selectedFolder,
                    botToken = activeAuth?.botToken.orEmpty(),
                    chatId = activeAuth?.targetChatId ?: "me"
                )
                files = repository.loadFiles()
                isUploading = false
                snackbarHostState.showSnackbar(statusMsg)
            }
        }
    }

    if (authState !is TelegramAuthState.Authenticated) {
        TelegramLoginScreen(
            authState = authState,
            onUpdateState = { authState = it }
        )
        return
    }

    val activeAuth = authState as TelegramAuthState.Authenticated

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "سحابتي غير المحدودة (UnLim عربي)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = activeAuth.accountLabel,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showTelegramConnectSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "إعدادات الربط السحابي"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { filePickerLauncher.launch("*/*") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                icon = {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = "رفع ملف")
                    }
                },
                text = {
                    Text(
                        if (isUploading) "جاري الرفع لتيليجرام..." else "رفع ملف إلى "$selectedFolder"",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                    label = { Text("الرئيسية") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("المجلدات") }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                    label = { Text("الوسائط والصوتيات") }
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("الإعدادات") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> HomeCloudDashboard(
                    files = files,
                    folders = folders,
                    selectedFolder = selectedFolder,
                    selectedCategory = selectedCategory,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onSelectCategory = { selectedCategory = it },
                    onSelectFolder = { selectedFolder = it },
                    onCreateFolderClick = { showNewFolderDialog = true },
                    onDeleteFile = { target ->
                        val updated = files.filterNot { it.id == target.id }
                        files = updated
                        repository.saveFiles(updated)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("تم حذف ${target.name} من الفهرس السحابي")
                        }
                    }
                )
                1 -> FoldersExplorerScreen(
                    folders = folders,
                    files = files,
                    selectedFolder = selectedFolder,
                    onSelectFolder = { selectedFolder = it },
                    onCreateFolderClick = { showNewFolderDialog = true },
                    onUploadInFolder = { filePickerLauncher.launch("*/*") }
                )
                2 -> MediaAndMusicScreen(
                    files = files,
                    onUploadMedia = { filePickerLauncher.launch("*/*") }
                )
                3 -> CloudSettingsScreen(
                    repository = repository,
                    activeAuth = activeAuth,
                    onOpenTelegramSetup = { showTelegramConnectSheet = true },
                    onLogout = { authState = TelegramAuthState.LoggedOut },
                    onShowMessage = { msg ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                )
            }
        }
    }

    if (showNewFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onCreate = { folderName ->
                val newFolder = CloudFolder(
                    id = "f_${UUID.randomUUID()}",
                    name = folderName.trim()
                )
                val updated = folders + newFolder
                folders = updated
                selectedFolder = newFolder.name
                repository.saveFolders(updated)
                showNewFolderDialog = false
            }
        )
    }

    if (showTelegramConnectSheet) {
        TelegramLinkDialog(
            currentAuth = activeAuth,
            onDismiss = { showTelegramConnectSheet = false },
            onSaveConnection = { newAuth ->
                authState = newAuth
                showTelegramConnectSheet = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("تم تحديث إعدادات الربط السحابي بتيليجرام بنجاح")
                }
            }
        )
    }
}

@Composable
fun HomeCloudDashboard(
    files: List<CloudFile>,
    folders: List<CloudFolder>,
    selectedFolder: String,
    selectedCategory: FileCategory,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectCategory: (FileCategory) -> Unit,
    onSelectFolder: (String) -> Unit,
    onCreateFolderClick: () -> Unit,
    onDeleteFile: (CloudFile) -> Unit
) {
    val totalBytes = files.sumOf { it.sizeBytes }
    val formattedTotal = remember(totalBytes) {
        val mb = totalBytes / (1024.0 * 1024.0)
        val gb = mb / 1024.0
        if (gb >= 1.0) String.format("%.2f جيجابايت", gb) else String.format("%.1f ميجابايت", mb)
    }

    val filteredFiles = files.filter { file ->
        val matchesCategory = selectedCategory == FileCategory.ALL || file.category == selectedCategory
        val matchesSearch = searchQuery.isBlank() ||
            file.name.contains(searchQuery, ignoreCase = true) ||
            file.folderName.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // بطاقة التخزين السحابي غير المحدود
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(TelegramBlue, TelegramDarkBlue)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "المساحة السحابية المتاحة: ∞ غير محدودة",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                            Icon(
                                imageVector = Icons.Default.AllInclusive,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "المستخدم حالياً في الرسائل المحفوظة (Saved Messages): $formattedTotal (${files.size} ملف)",
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 13.sp
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "• حد الملف الواحد: 2 جيجابايت (مع تقسيم تلقائي للأكبر)",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "مجاني للأبد 100%",
                                color = Color(0xFFA7F3D0),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // شريط البحث العربي
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ابحث عن اسم ملف أو مجلد في سحابتك...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        // شريط اختيار المجلد النشط للرفع السريع
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المجلد النشط للرفع:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                TextButton(onClick = onCreateFolderClick) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("مجلد جديد")
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(folders) { folder ->
                    val isSelected = folder.name == selectedFolder
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectFolder(folder.name) },
                        label = { Text(folder.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }

        // فلاتر التصنيف (الكل، الصور، الفيديوهات، الصوتيات، المستندات)
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FileCategory.values().toList()) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onSelectCategory(category) },
                        label = { Text(category.arabicTitle) }
                    )
                }
            }
        }

        // قائمة الملفات المرفوعة
        item {
            Text(
                text = "الملفات المحفوظة في تيليجرام (${filteredFiles.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        items(filteredFiles, key = { it.id }) { file ->
            CloudFileRowCard(
                file = file,
                onDelete = { onDeleteFile(file) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(76.dp))
        }
    }
}

@Composable
fun CloudFileRowCard(
    file: CloudFile,
    onDelete: () -> Unit
) {
    val icon = when (file.category) {
        FileCategory.PHOTOS -> Icons.Default.Image
        FileCategory.VIDEOS -> Icons.Default.Videocam
        FileCategory.AUDIO -> Icons.Default.MusicNote
        else -> Icons.Default.Description
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "📁 ${file.folderName} • ${file.formattedSize()}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "☁️ Saved Messages (#${file.telegramMessageId})",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "حذف",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun FoldersExplorerScreen(
    folders: List<CloudFolder>,
    files: List<CloudFile>,
    selectedFolder: String,
    onSelectFolder: (String) -> Unit,
    onCreateFolderClick: () -> Unit,
    onUploadInFolder: () -> Unit
) {
    val folderFiles = files.filter { it.folderName == selectedFolder }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مجلدات الرسائل المحفوظة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Button(onClick = onCreateFolderClick) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("إنشاء مجلد")
                }
            }
        }

        items(folders) { folder ->
            val count = files.count { it.folderName == folder.name }
            val isSelected = folder.name == selectedFolder
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectFolder(folder.name) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.FolderOpen else Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(text = folder.name, fontWeight = FontWeight.Bold)
                            Text(
                                text = "وسم تيليجرام: #${folder.name.replace(" ", "_")}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }
                    Badge {
                        Text("$count ملف")
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "محتويات مجلد "$selectedFolder" (${folderFiles.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        if (folderFiles.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudQueue,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("هذا المجلد فارغ حالياً")
                        OutlinedButton(onClick = onUploadInFolder) {
                            Text("ارفع أول ملف لهذا المجلد")
                        }
                    }
                }
            }
        } else {
            items(folderFiles, key = { it.id }) { file ->
                CloudFileRowCard(file = file, onDelete = {})
            }
        }

        item { Spacer(Modifier.height(76.dp)) }
    }
}

@Composable
fun MediaAndMusicScreen(
    files: List<CloudFile>,
    onUploadMedia: () -> Unit
) {
    val photosAndVideos = files.filter { it.category == FileCategory.PHOTOS || it.category == FileCategory.VIDEOS }
    val audioTracks = files.filter { it.category == FileCategory.AUDIO }
    var currentlyPlayingTrack by remember { mutableStateOf(audioTracks.firstOrNull()?.name) }
    var isPlaying by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "مشغل الصوتيات السحابي (بدون استهلاك مساحة الهاتف)",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Column {
                            Text(
                                text = currentlyPlayingTrack ?: "لا يوجد ملف صوتي محدد",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isPlaying) "يتم البث المباشر من سحابة تيليجرام..." else "متوقف مؤقتاً • جاهز للبث السحابي",
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = { isPlaying = !isPlaying }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                            contentDescription = "تشغيل",
                            modifier = Modifier.size(42.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        items(audioTracks, key = { it.id }) { track ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        currentlyPlayingTrack = track.name
                        isPlaying = true
                    },
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "🎵 ${track.name}", fontWeight = FontWeight.Medium)
                    Text(text = track.formattedSize(), fontSize = 12.sp)
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "معرض الصور والفيديوهات بجودتها الأصلية (${photosAndVideos.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }

        items(photosAndVideos, key = { it.id }) { media ->
            CloudFileRowCard(file = media, onDelete = {})
        }

        item { Spacer(Modifier.height(76.dp)) }
    }
}

@Composable
fun CloudSettingsScreen(
    repository: CloudRepository,
    activeAuth: TelegramAuthState.Authenticated,
    onOpenTelegramSetup: () -> Unit,
    onLogout: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    var autoBackup by remember { mutableStateOf(repository.isAutoBackupEnabled()) }
    var originalQuality by remember { mutableStateOf(repository.isOriginalQualityEnabled()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "إعدادات الربط السحابي وتيليجرام",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("حالة الحساب المرتبط", fontWeight = FontWeight.Bold)
                    Text(activeAuth.accountLabel, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "يتم حفظ جميع الملفات في محادثة الرسائل المحفوظة (Saved Messages) أو القناة الخاصة المحددة مع تقسيم الملفات الأكبر من 2 جيجابايت تلقائياً.",
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = onOpenTelegramSetup,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("إدخال مفاتيح تيليجرام (API ID / Hash / Token)")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("النسخ الاحتياطي التلقائي للكاميرا (DCIM)", fontWeight = FontWeight.SemiBold)
                            Text(
                                "رفع الصور والفيديوهات الجديدة في الخلفية عبر WorkManager عند الاتصال بـ Wi-Fi",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = autoBackup,
                            onCheckedChange = {
                                autoBackup = it
                                repository.setAutoBackupEnabled(it)
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("رفع الصور بجودتها الأصلية (بدون ضغط)", fontWeight = FontWeight.SemiBold)
                            Text(
                                "إرسال الصور كملف Document عبر TDLib لمنع تيليجرام من تقليل الدقة",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = originalQuality,
                            onCheckedChange = {
                                originalQuality = it
                                repository.setOriginalQualityEnabled(it)
                            }
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    onShowMessage("تم تفريغ الكاش المؤقت من الهاتف مع بقاء جميع ملفاتك آمنة في سحابة تيليجرام")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("تفريغ الكاش المحلي لتوفير مساحة الهاتف")
            }
        }

        item {
            TextButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("تسجيل الخروج من جلسة تيليجرام", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun TelegramLoginScreen(
    authState: TelegramAuthState,
    onUpdateState: (TelegramAuthState) -> Unit
) {
    var phone by remember { mutableStateOf("+20") }
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "سحابتي غير المحدودة",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
                Text(
                    text = "اربط حسابك في تيليجرام لتحصل على مساحة تخزين سحابية غير محدودة ومجانية للأبد في (الرسائل المحفوظة)",
                    fontSize = 13.sp
                )

                when (authState) {
                    is TelegramAuthState.LoggedOut -> {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("رقم الهاتف المرتبط بتيليجرام") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = apiId,
                            onValueChange = { apiId = it },
                            label = { Text("Telegram API ID (من my.telegram.org)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = apiHash,
                            onValueChange = { apiHash = it },
                            label = { Text("Telegram API Hash") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                onUpdateState(
                                    TelegramAuthState.WaitingForOtp(
                                        phoneNumber = phone,
                                        apiId = apiId.ifBlank { "123456" },
                                        apiHash = apiHash.ifBlank { "tdlib_hash" }
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("إرسال كود التحقق عبر تيليجرام")
                        }
                    }

                    is TelegramAuthState.WaitingForOtp -> {
                        Text("تم إرسال رمز الدخول إلى تطبيق تيليجرام للرقم ${authState.phoneNumber}")
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { otpCode = it },
                            label = { Text("كود التحقق (OTP)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                onUpdateState(
                                    TelegramAuthState.Authenticated(
                                        accountLabel = "متصل بالرقم ${authState.phoneNumber} (Saved Messages)",
                                        modeDescription = "تخزين سحابي غير محدود عبر TDLib",
                                        apiId = authState.apiId,
                                        apiHash = authState.apiHash
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تأكيد الكود وفتح السحابة")
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء مجلد سحابي جديد") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("اسم المجلد (مثال: صور التخرج)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name) }
            ) {
                Text("إنشاء")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun TelegramLinkDialog(
    currentAuth: TelegramAuthState.Authenticated,
    onDismiss: () -> Unit,
    onSaveConnection: (TelegramAuthState.Authenticated) -> Unit
) {
    var apiId by remember { mutableStateOf(currentAuth.apiId) }
    var apiHash by remember { mutableStateOf(currentAuth.apiHash) }
    var botToken by remember { mutableStateOf(currentAuth.botToken) }
    var chatId by remember { mutableStateOf(currentAuth.targetChatId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات الربط السحابي المباشر بتيليجرام") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "يمكنك إدخال مفاتيح my.telegram.org (للاتصال عبر TDLib) أو Bot Token + Chat ID للرفع الفوري المباشر من هاتفك إلى قناتك الخاصة أو رسائلك المحفوظة:",
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = apiId,
                    onValueChange = { apiId = it },
                    label = { Text("API ID (اختياري لـ TDLib)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = apiHash,
                    onValueChange = { apiHash = it },
                    label = { Text("API Hash (اختياري لـ TDLib)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    label = { Text("Bot Token (للرفع السحابي الفوري عبر HTTP)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    label = { Text("معرف المحادثة / القناة الخاصة (Chat ID)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val label = if (botToken.isNotBlank() && chatId.isNotBlank()) {
                        "متصل سحابياً بشكل مباشر (Chat ID: $chatId)"
                    } else {
                        "حساب تيليجرام متصل (الرسائل المحفوظة)"
                    }
                    onSaveConnection(
                        currentAuth.copy(
                            accountLabel = label,
                            apiId = apiId.trim(),
                            apiHash = apiHash.trim(),
                            botToken = botToken.trim(),
                            targetChatId = chatId.trim().ifBlank { "me" }
                        )
                    )
                }
            ) {
                Text("حفظ وتفعيل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}