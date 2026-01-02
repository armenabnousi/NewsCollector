package com.example.newscollector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlin.collections.forEach
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NewsCollectorApp()
                }
            }
        }
    }
}

@Composable
fun NewsCollectorApp(viewModel: NewsViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainTabsScreen(viewModel, onOpenSettings = { navController.navigate("settings") })
        }
        composable("settings") {
            SettingsScreen(viewModel, onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabsScreen(viewModel: NewsViewModel, onOpenSettings: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val error by viewModel.errorMessage.collectAsState()

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorMessage.value = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState)},
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.refreshNews() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("News Collector") },
                    navigationIcon = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("Sources", modifier = Modifier.padding(16.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("News", modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (selectedTab == 0) SourcesTab(viewModel) else NewsTab(viewModel)
        }
    }
}

@Composable
fun SourcesTab(viewModel: NewsViewModel) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var isRss by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf("10") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Add New Source", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Source Name") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            modifier = Modifier.fillMaxWidth()
        )
        TextField(
            value = limit,
            onValueChange = { limit = it.filter { char ->
                char.isDigit()
            }},
            label = { Text("Max News Items") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isRss, onCheckedChange = { isRss = it })
            Text("RSS Feed")
        }
        Button(onClick = {
            viewModel.addSource(name, url, isRss, limit.toIntOrNull() ?: 10)
            name = ""; url = ""
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Save Source")
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        LazyColumn {
            items(viewModel.sources) { source ->
                ListItem(
                    headlineContent = { Text("${source.name}  (${source.limit})")},
                    supportingContent = { Text(source.url) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeSource(source) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun NewsTab(viewModel: NewsViewModel) {
    val newsItems by viewModel.unifiedNewsList.collectAsState()
    val refreshing by viewModel.isRefreshing.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Button(
            onClick = { viewModel.refreshNews() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !refreshing
        ) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text(if (refreshing) "Refreshing..." else "Refresh News")
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(newsItems) { unifiedNews ->
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)) {
                    // Inside your NewsCard Composable
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = unifiedNews.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = unifiedNews.mainContent,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Displaying ALL sources
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            unifiedNews.sources.forEach { source ->
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = source.name,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: NewsViewModel, onBack: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val savedToken by viewModel.openRouterToken.collectAsState(initial = "")
    var tokenInput by remember {
        mutableStateOf("")
    }
    LaunchedEffect(savedToken) {
        if (savedToken != null && tokenInput.isEmpty()) {
            tokenInput = savedToken!!
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .padding(16.dp)) {
            Text("Models", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors
                    (containerColor = MaterialTheme
                    .colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Active Model:", style =
                        MaterialTheme.typography.labelLarge)
                    Text(
                        text = viewModel.savedDisplayName.value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = tokenInput,
                onValueChange = {
                    tokenInput = it
                    viewModel.updateApiToken(it)
                                },
                label = { Text("OpenRouter Bearer Token") },
                placeholder = {Text("sk-or-v1-... (enter token here)")},
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PartialPasswordVisualTransformation(visibleChars = 4),
                isError = tokenInput.isBlank(),
                supportingText = {
                    if (tokenInput.isBlank()) {
                        Text(
                            text = "Token is required to fetch news",
                            color = MaterialTheme.colorScheme.error)
                    } else if (tokenInput.length < 10) {
                        Text(text = "Token seems too short", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.fetchModels() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isFetchingModels.value
            ) {
                Text(if (viewModel.isFetchingModels.value) "Fetching..." else "Refresh Models")
            }

            Spacer(modifier = Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                val selectedText = viewModel.selectedModel.value?.let {
                    "${it.name} (${it.pricing?.prompt ?: "nan"}, ${it.pricing?.completion ?: "nan"})"
                } ?: "Select a Model"

                OutlinedTextField(
                    value = selectedText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Available Models") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    // Inside SettingsScreen function in MainActivity.kt
                    viewModel.availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text("${model.name} (${model.pricing?.prompt ?: "nan"}, ${model.pricing?.completion ?: "nan"})") },
                            onClick = {
                                // Change this line to use the new save function:
                                viewModel.selectAndSaveModel(model)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        NewsCollectorApp()
    }
}

class PartialPasswordVisualTransformation(private val visibleChars: Int = 4) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val mask = "•"
        val length = text.text.length

        val transformed = if (length <= visibleChars) {
            // If the text is shorter than 4 chars, show it all
            text.text
        } else {
            // Mask everything except the last 'visibleChars'
            mask.repeat(length - visibleChars) + text.text.takeLast(visibleChars)
        }

        return TransformedText(
            AnnotatedString(transformed),
            OffsetMapping.Identity // Keeps the cursor mapping 1:1
        )
    }
}