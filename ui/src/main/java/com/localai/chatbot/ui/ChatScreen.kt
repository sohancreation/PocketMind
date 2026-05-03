package com.localai.chatbot.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.localai.chatbot.models.Message
import com.localai.chatbot.models.Role
import com.localai.chatbot.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.localai.chatbot.models.ModelInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognizedText = results?.get(0)
            if (!recognizedText.isNullOrEmpty()) {
                val prefix = if (inputText.text.isNotEmpty() && !inputText.text.endsWith(" ")) " " else ""
                inputText = TextFieldValue(inputText.text + prefix + recognizedText)
            }
        }
    }

    // Auto-scroll to latest message
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length, isTyping) {
        if (messages.isNotEmpty() || isTyping) {
            val targetIndex = (messages.size + if (isTyping) 1 else 0) - 1
            if (targetIndex >= 0) {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentChatId by viewModel.currentChatId.collectAsState()
    val allChats by viewModel.chats.collectAsState()
    val status by viewModel.status.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "Chat History",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                Button(
                    onClick = {
                        viewModel.createNewChat()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text("New Chat")
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(allChats, key = { it.id }) { chat ->
                        NavigationDrawerItem(
                            label = { 
                                Column {
                                    Text(chat.title, fontWeight = FontWeight.Bold)
                                    Text(
                                        chat.lastMessage ?: "No messages yet", 
                                        maxLines = 1, 
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                    Text(
                                        formatTime(chat.createdAt), 
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            },
                            selected = chat.id == currentChatId,
                            onClick = {
                                viewModel.loadChat(chat.id)
                                scope.launch { drawerState.close() }
                            },
                            badge = {
                                IconButton(onClick = { viewModel.deleteChat(chat.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                var showModelDialog by remember { mutableStateOf(false) }
                if (showModelDialog) {
                    ModelDownloaderDialog(
                        viewModel = viewModel,
                        onDismiss = { showModelDialog = false }
                    )
                }

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Manage Models") },
                    selected = false,
                    onClick = { showModelDialog = true },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Column {
                            Text("PocketMind AI")
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            },
            // Put the input in bottomBar so it naturally sits above the IME like WhatsApp/Messenger.
            bottomBar = {
                MessageInput(
                    inputText = inputText,
                    onTextChange = { inputText = it },
                    onSend = {
                        if (inputText.text.isNotBlank()) {
                            viewModel.sendMessage(inputText.text)
                            inputText = TextFieldValue("")
                        }
                    },
                    onSpeechInput = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        }
                        speechLauncher.launch(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                )
            }
        ) { paddingValues ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
                if (isTyping) {
                    item {
                        TypingIndicator()
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val isUser = message.role == Role.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (isUser) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }
    
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                color = bgColor,
                shape = shape,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (message.imageUrl != null) {
                        AsyncImage(
                            model = message.imageUrl,
                            contentDescription = "Attached Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (message.content.isNotBlank()) {
                        SelectionContainer {
                            MarkdownMessageList(message.content, textColor)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isUser) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.content))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Message",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = formatTime(message.timestamp),
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
fun MarkdownMessageList(text: String, textColor: Color) {
    val parts = text.split("```")
    Column {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                // Code block
                val lines = part.trim().lines()
                val language = lines.firstOrNull()?.trim() ?: ""
                val code = if (lines.size > 1) lines.drop(1).joinToString("\n") else part.trim()
                
                Surface(
                    color = Color(0xFF1E1E1E), // Dark code background similar to VS Code
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column {
                        if (language.isNotEmpty() && !language.contains(" ")) {
                             Text(
                                 text = language, 
                                 color = Color(0xFFD4D4D4), 
                                 style = MaterialTheme.typography.labelSmall, 
                                 modifier = Modifier
                                     .background(Color(0xFF2D2D2D))
                                     .fillMaxWidth()
                                     .padding(horizontal = 8.dp, vertical = 4.dp)
                             )
                        }
                        Text(
                            text = code,
                            color = Color(0xFFCE9178), // String/code color
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            } else {
                if (part.isNotBlank()) {
                    Text(
                        text = part.trim(),
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(400, delayMillis = 0), repeatMode = RepeatMode.Reverse),
        label = "alpha1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(400, delayMillis = 200), repeatMode = RepeatMode.Reverse),
        label = "alpha2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(400, delayMillis = 400), repeatMode = RepeatMode.Reverse),
        label = "alpha3"
    )

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            modifier = Modifier.widthIn(min = 60.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha1)))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha2)))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha3)))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInput(
    inputText: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onSpeechInput: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message...") },
            shape = RoundedCornerShape(24.dp),
            maxLines = 4,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        
        if (inputText.text.isBlank()) {
            IconButton(
                onClick = onSpeechInput,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Speech Input",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        } else {
            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.White
                )
            }
        }
    }
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun ModelDownloaderDialog(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val models by viewModel.availableModels.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI Models Manager") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SmolLM2 Mini is included for offline chat. Download other AI models for better performance and quality.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(models) { model ->
                    ModelItem(
                        model = model,
                        onDownload = { viewModel.downloadModel(model) }
                    )
                }
            }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ModelItem(
    model: ModelInfo,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(model.size, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                
                if (model.isDownloaded) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Downloaded", tint = Color(0xFF4CAF50))
                } else if (model.isDownloading) {
                    CircularProgressIndicator(
                        progress = model.progress,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Download")
                    }
                }
            }
            
            Text(
                model.description,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            if (model.isDownloading) {
                LinearProgressIndicator(
                    progress = model.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Text(
                    "${(model.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
