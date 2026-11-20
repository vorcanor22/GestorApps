package com.example.gestorapps

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.gestorapps.data.AppManager
import com.example.gestorapps.data.ManagedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GestorAppsApp(
                onOpenUsageSettings = {
                    try {
                        startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        )
                    } catch (e: Exception) {
                        startActivity(
                            Intent(Settings.ACTION_SETTINGS)
                        )
                    }
                }
            )
        }
    }
}

// --------------------------------------------------
// THEME
// --------------------------------------------------

private val Background = Color(0xFF101114)
private val SurfaceDark = Color(0xFF1B1B20)
private val CardDark = Color(0xFF24242A)
private val Accent = Color(0xFFBB86FC)
private val TextPrimary = Color(0xFFF1F0F5)
private val TextSecondary = Color(0xFFAAAAAF)

// --------------------------------------------------
// MAIN APP
// --------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestorAppsApp(
    onOpenUsageSettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(AppManager.hasUsagePermission(context))
    }

    var apps by remember {
        mutableStateOf<List<ManagedApp>>(emptyList())
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var selectedApp by remember {
        mutableStateOf<ManagedApp?>(null)
    }

    var sortBySize by remember {
        mutableStateOf(true)
    }

    var loadError by remember {
        mutableStateOf<String?>(null)
    }

    // Reload when returning from Android settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = AppManager.hasUsagePermission(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Load installed apps in background
    LaunchedEffect(hasPermission) {
        isLoading = true
        loadError = null

        try {
            apps = withContext(Dispatchers.IO) {
                AppManager.getInstalledApps(context)
            }
        } catch (e: Exception) {
            loadError = e.localizedMessage ?: "Error cargando aplicaciones"
        }

        isLoading = false
    }

    // Sorting
    val sortedApps = remember(apps, sortBySize) {
        if (sortBySize) {
            apps.sortedWith(
                compareByDescending<ManagedApp> {
                    it.totalSize ?: -1L
                }.thenBy {
                    it.name.lowercase()
                }
            )
        } else {
            apps.sortedBy {
                it.name.lowercase()
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Background,
            surface = SurfaceDark
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                // Header
                HeaderSection(
                    appCount = apps.size
                )

                // Permission panel
                if (!hasPermission) {
                    PermissionPanel(
                        onClick = onOpenUsageSettings
                    )
                }

                // Sorting bar
                SortBar(
                    sortBySize = sortBySize,
                    onSortChange = { sortBySize = it }
                )

                // Error
                loadError?.let {
                    Text(
                        text = it,
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Loading
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        color = Accent
                    )
                }

                // Apps list
                if (!isLoading && apps.isEmpty() && loadError == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No se encontraron aplicaciones",
                            color = TextSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = sortedApps,
                            key = { it.packageName }
                        ) { app ->
                            AppRow(
                                app = app,
                                onClick = {
                                    selectedApp = app
                                }
                            )
                        }
                    }
                }
            }

            // Details dialog
            selectedApp?.let { app ->
                AppDetailsDialog(
                    app = app,
                    onDismiss = {
                        selectedApp = null
                    }
                )
            }
        }
    }
}

// --------------------------------------------------
// HEADER
// --------------------------------------------------

@Composable
fun HeaderSection(
    appCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(20.dp)
    ) {
        Text(
            text = "GestorApps",
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "$appCount aplicaciones instaladas",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

// --------------------------------------------------
// PERMISSION
// --------------------------------------------------

@Composable
fun PermissionPanel(
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(
                CardDark,
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "Permiso de almacenamiento",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Activa el acceso a estadísticas de uso para intentar consultar el tamaño de las aplicaciones, sus datos y caché.",
            color = TextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Color.Black
            )
        ) {
            Text("Conceder permiso")
        }
    }
}

// --------------------------------------------------
// SORT BAR
// --------------------------------------------------

@Composable
fun SortBar(
    sortBySize: Boolean,
    onSortChange: (Boolean) -> Unit
){
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Ordenar por",
            color = TextSecondary,
            fontSize = 14.sp
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortButton(
                text = "Peso",
                selected = sortBySize,
                onClick = { onSortChange(true) }
            )

            SortButton(
                text = "Nombre",
                selected = !sortBySize,
                onClick = { onSortChange(false) }
            )
        }
    }
}

@Composable
fun SortButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
){
    Surface(
        shape = RoundedCornerShape(50.dp),
        color = if (selected) Accent else CardDark,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 9.dp
            )
        )
    }
}

// --------------------------------------------------
// APP ROW
// --------------------------------------------------

@Composable
fun AppRow(
    app: ManagedApp,
    onClick: () -> Unit
){
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                CardDark,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // App icon
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { imageView ->
                imageView.setImageDrawable(app.icon)
            },
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // App name + package
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = app.name,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = app.packageName,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Size
        Text(
            text = AppManager.formatSize(app.totalSize),
            color = Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// --------------------------------------------------
// APP DETAILS
// --------------------------------------------------

@Composable
fun AppDetailsDialog(
    app: ManagedApp,
    onDismiss: () -> Unit
){
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    update = { it.setImageDrawable(app.icon) },
                    modifier = Modifier.size(42.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = app.name,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                DetailItem(
                    "Paquete",
                    app.packageName
                )

                DetailItem(
                    "Versión",
                    app.versionName
                )

                DetailItem(
                    "Código de versión",
                    app.versionCode.toString()
                )

                DetailItem(
                    "Instalada",
                    formatDate(app.installDate)
                )

                DetailItem(
                    "Última actualización",
                    formatDate(app.lastUpdateDate)
                )

                HorizontalDivider(
                    color = Color.DarkGray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                DetailItem(
                    "Aplicación",
                    AppManager.formatSize(app.appSize)
                )

                DetailItem(
                    "Datos",
                    AppManager.formatSize(app.dataSize)
                )

                DetailItem(
                    "Caché",
                    AppManager.formatSize(app.cacheSize)
                )

                DetailItem(
                    "Tamaño total",
                    AppManager.formatSize(app.totalSize),
                    highlight = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    "Cerrar",
                    color = Accent
                )
            }
        }
    )
}

@Composable
fun DetailItem(
    label: String,
    value: String,
    highlight: Boolean = false
){
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp
        )

        Text(
            text = value,
            color = if (highlight) Accent else TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (highlight) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            }
        )
    }
}

// --------------------------------------------------
// DATE FORMAT
// --------------------------------------------------

fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Desconocida"

    return try {
        SimpleDateFormat(
            "dd/MM/yyyy HH:mm",
            Locale.getDefault()
        ).format(Date(timestamp))
    } catch (e: Exception) {
        "Desconocida"
    }
}
