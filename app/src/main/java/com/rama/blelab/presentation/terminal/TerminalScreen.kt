package com.rama.blelab.presentation.terminal

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rama.blelab.data.repository.FormulaImporter
import com.rama.blelab.domain.model.ParsingFormula
import com.rama.blelab.domain.repository.BleMessage
import com.rama.blelab.domain.repository.ConnectionState
import com.rama.blelab.domain.repository.Macro
import com.rama.blelab.domain.repository.MessageType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    formulaImporter: FormulaImporter,
    onBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isHexMode by viewModel.isHexMode.collectAsState()
    val macros by viewModel.macros.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    var macroToEdit by remember { mutableStateOf<Macro?>(null) }
    var isAddingMacro by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    if (isAddingMacro) {
        MacroDialog(
            formulaImporter = formulaImporter,
            onDismiss = { isAddingMacro = false },
            onConfirm = { name, command, formulas -> 
                viewModel.addMacro(name, command, formulas)
                isAddingMacro = false
            }
        )
    }

    macroToEdit?.let { macro ->
        MacroDialog(
            macro = macro,
            formulaImporter = formulaImporter,
            onDismiss = { macroToEdit = null },
            onConfirm = { name, command, formulas ->
                viewModel.updateMacro(macro, name, command, formulas)
                macroToEdit = null
            },
            onDelete = {
                viewModel.removeMacro(macro)
                macroToEdit = null
            }
        )
    }

    // Auto-scroll to bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val deviceName = (connectionState as? ConnectionState.Connected)?.device?.name ?: "Unknown"
    val deviceAddress = (connectionState as? ConnectionState.Connected)?.device?.address ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(deviceName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF00E676), RoundedCornerShape(4.dp)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CONNECTED", fontSize = 10.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                        }
                        Text(deviceAddress, fontSize = 12.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs")
                    }
                    IconButton(onClick = { /* Settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8F9FA))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }
            }

            QuickCommands(
                macros = macros,
                onMacroClick = { viewModel.sendMessage(it.command) },
                onAddClick = { isAddingMacro = true },
                onLongClick = { macroToEdit = it }
            )
            
            InputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                isHex = isHexMode,
                onToggleHex = { viewModel.toggleHexMode() }
            )
        }
    }
}

@Composable
fun MessageBubble(message: BleMessage) {
    val isRX = message.type == MessageType.RX
    val alignment = if (isRX) Alignment.Start else Alignment.End
    val bubbleColor = if (isRX) Color(0xFFF1F1F1) else Color(0xFF1E88E5)
    val textColor = if (isRX) Color.Black else Color.White
    val timeFormat = SimpleDateFormat("HH:mm:ss a", Locale.getDefault())

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = alignment) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isRX) 0.dp else 16.dp,
                bottomEnd = if (isRX) 16.dp else 0.dp
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = message.content,
                    color = textColor,
                    fontSize = 15.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                
                if (message.parsedContent != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color.LightGray.copy(alpha = 0.5f)
                    )
                    
                    val parsedLines = message.parsedContent.split("\n")
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF00796B)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Meter Data",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00796B),
                                fontSize = 14.sp
                            )
                        }
                        
                        parsedLines.forEach { line ->
                            val parts = line.split(": ", limit = 2)
                            if (parts.size == 2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = parts[0],
                                        fontSize = 13.sp,
                                        color = Color.DarkGray
                                    )
                                    Text(
                                        text = parts[1],
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (parts[0].contains("Balance", true)) Color(0xFFD32F2F) else Color.Black
                                    )
                                }
                            } else {
                                Text(text = line, fontSize = 13.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFE0F2F1),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Source: ${message.parserName}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            color = Color(0xFF004D40)
                        )
                    }
                }
            }
        }
        Text(
            text = "${timeFormat.format(Date(message.timestamp))} • ${if (isRX) "RX" else "TX"}",
            fontSize = 10.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickCommands(
    macros: List<Macro>,
    onMacroClick: (Macro) -> Unit,
    onAddClick: () -> Unit,
    onLongClick: (Macro) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFF1F3F4), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Macro", tint = Color(0xFF1976D2))
            }
        }
        items(macros) { macro ->
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { onMacroClick(macro) },
                        onLongClick = { onLongClick(macro) }
                    ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray),
                color = if (macro.formulas.isNotEmpty()) Color(0xFFE3F2FD) else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (macro.formulas.isNotEmpty()) {
                        Icon(Icons.Default.Functions, null, modifier = Modifier.size(14.dp), tint = Color(0xFF1976D2))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = macro.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF455A64)
                    )
                }
            }
        }
    }
}

@Composable
fun MacroDialog(
    macro: Macro? = null,
    formulaImporter: FormulaImporter,
    onDismiss: () -> Unit,
    onConfirm: (String, String, List<ParsingFormula>) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(macro?.name ?: "") }
    var command by remember { mutableStateOf(macro?.command ?: "") }
    var formulas by remember { mutableStateOf(macro?.formulas ?: emptyList()) }
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val imported = formulaImporter.importJsonParserFromUri(
                        it,
                        context.contentResolver.getType(it)
                    )
                    formulas = imported.formulas
                    imported.commandHex?.let { importedCommand ->
                        command = importedCommand
                    }
                    if (name.isBlank()) {
                        imported.parserName?.let { importedName ->
                            name = importedName
                        }
                    }
                    Toast.makeText(
                        context,
                        "Imported ${imported.formulas.size} parser field(s)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (macro == null) "Add Quick Command" else "Edit Quick Command") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Button Name") },
                    placeholder = { Text("e.g. Temperature") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("BLE Command") },
                    placeholder = { Text("e.g. GET_TEMP") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Formula (Auto-Parse Response)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (formulas.isEmpty()) {
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("application/json", "text/json")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import JSON Parser", fontSize = 12.sp)
                    }
                } else {
                    formulas.forEach { formula ->
                        Surface(
                            color = Color(0xFFF1F3F4),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, tint = Color(0xFF00796B), contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(formula.name, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { formulas = formulas - formula }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { filePicker.launch(arrayOf("application/json", "text/json")) }) {
                            Text("Replace JSON")
                        }
                        TextButton(
                            onClick = { formulas = emptyList() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                        ) {
                            Text("Remove Parser")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, command, formulas) }) {
                Text(if (macro == null) "Add" else "Update")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputBar(
    text: String, 
    onTextChange: (String) -> Unit, 
    onSend: () -> Unit,
    isHex: Boolean,
    onToggleHex: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp, 0.dp, 0.dp, 8.dp))
                .clickable { onToggleHex() },
            color = Color(0xFFF1F3F4),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    text = if (isHex) "HEX" else "TXT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(if (isHex) "Enter hex (e.g. 01 0A)" else "Enter message") },
            shape = RoundedCornerShape(0.dp, 8.dp, 8.dp, 0.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color(0xFFF1F3F4),
                focusedContainerColor = Color(0xFFF1F3F4),
                unfocusedBorderColor = Color.LightGray,
                focusedBorderColor = Color(0xFF1976D2)
            ),
            singleLine = true
        )
        Spacer(modifier = Modifier.width(12.dp))
        FloatingActionButton(
            onClick = onSend,
            containerColor = Color(0xFF0056B3),
            contentColor = Color.White,
            shape = RoundedCornerShape(28.dp),
            elevation = FloatingActionButtonDefaults.elevation(0.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}
