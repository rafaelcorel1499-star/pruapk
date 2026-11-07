@file:OptIn(ExperimentalMaterial3Api::class)

package com.lapaola.facturas

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel

val DefaultPrimary = Color(0xFF7A4B2A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            AppTheme(vm) {
                val ctx = LocalContext.current
                val license = remember { LicenseStore(ctx) }
                var activated by remember { mutableStateOf(license.isActivated()) }
                if (activated) App(vm) else LockScreen(license) { activated = true }
            }
        }
    }
}

/** Color primario = barras y botones. Color secundario = letras e iconos sobre ellos. */
@Composable
fun AppTheme(vm: MainViewModel, content: @Composable () -> Unit) {
    val primary = parseHex(vm.primaryHex) ?: DefaultPrimary
    val secondary = parseHex(vm.secondaryHex) ?: Color.White
    val scheme = if (vm.darkMode) {
        darkColorScheme(
            primary = primary, onPrimary = secondary,
            primaryContainer = lerp(Color(0xFF1C1B1F), primary, 0.40f), onPrimaryContainer = Color.White,
            secondary = primary, secondaryContainer = lerp(Color(0xFF1C1B1F), primary, 0.40f),
            background = Color(0xFF121214), surface = Color(0xFF121214),
            surfaceVariant = Color(0xFF2A2A2E), surfaceContainerLowest = Color(0xFF222226)
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = secondary,
            primaryContainer = lerp(Color.White, primary, 0.18f), onPrimaryContainer = Color(0xFF1B1B1B),
            secondary = primary, secondaryContainer = lerp(Color.White, primary, 0.18f),
            background = Color(0xFFF8F8F8), surface = Color(0xFFF8F8F8),
            surfaceVariant = lerp(Color.White, primary, 0.10f), surfaceContainerLowest = Color.White
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = primary.toArgb()
            window.navigationBarColor = primary.toArgb()
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = primary.luminance() > 0.5f
            c.isAppearanceLightNavigationBars = primary.luminance() > 0.5f
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun App(vm: MainViewModel) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    BackHandler(enabled = vm.preview != null || vm.screen != Screen.Home) {
        if (vm.preview != null) vm.preview = null else vm.screen = Screen.Home
    }
    Scaffold(
        topBar = { AppHeader(vm) },
        bottomBar = { BottomBar(vm) },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val p = vm.preview
            if (p != null) PreviewScreen(vm, p) else when (vm.screen) {
                Screen.Home -> HomeScreen(vm)
                Screen.Invoices -> InvoicesScreen(vm)
                Screen.New -> NewInvoiceScreen(vm)
                Screen.Voice -> VoiceInvoiceScreen(vm)
                Screen.Products -> ProductsScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
fun AppHeader(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.primary, contentColor = cs.onPrimary) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val logo = vm.logo
            if (logo != null) {
                Surface(shape = RoundedCornerShape(10.dp), color = Color.White) {
                    Image(
                        bitmap = logo.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.padding(4.dp).height(40.dp).widthIn(max = 110.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                Icon(painterResource(R.drawable.ic_logo_invoice), null, Modifier.size(44.dp))
            }
            Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(40.dp).background(cs.onPrimary.copy(alpha = 0.5f)))
            Text(
                vm.businessName, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = { vm.toggleDark() }) {
                Icon(if (vm.darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Modo oscuro")
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    NavigationBarItem(
        selected = selected, onClick = onClick, icon = icon,
        label = { Text(label, fontSize = 10.sp, maxLines = 1) }, alwaysShowLabel = true,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = cs.primary, selectedTextColor = cs.onPrimary,
            unselectedIconColor = cs.onPrimary.copy(alpha = 0.75f), unselectedTextColor = cs.onPrimary.copy(alpha = 0.75f),
            indicatorColor = cs.onPrimary
        )
    )
}

@Composable
fun BottomBar(vm: MainViewModel) {
    val s = vm.screen
    val inv = vm.preview != null
    val cs = MaterialTheme.colorScheme
    NavigationBar(containerColor = cs.primary, contentColor = cs.onPrimary) {
        NavItem("Inicio", (s == Screen.Home || s == Screen.Voice) && !inv, { vm.go(Screen.Home) }) { Icon(Icons.Filled.Home, null) }
        NavItem("Facturas", s == Screen.Invoices, { vm.go(Screen.Invoices) }) { Icon(Icons.Filled.Description, null) }
        NavItem("Nueva Factura", s == Screen.New && !inv, { vm.go(Screen.New) }) {
            Box(Modifier.size(34.dp).background(cs.onPrimary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, null, tint = cs.primary)
            }
        }
        NavItem("Base de Datos", s == Screen.Products, { vm.go(Screen.Products) }) { Icon(Icons.Filled.Storage, null) }
        NavItem("Configuración", s == Screen.Settings, { vm.go(Screen.Settings) }) { Icon(Icons.Filled.Settings, null) }
    }
}

// ---------------- Inicio ----------------

@Composable
fun HomeScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BigButton("Crear Factura", Icons.Filled.Receipt) { vm.go(Screen.New) }
        BigButton("Factura por Voz", Icons.Filled.Mic) { vm.go(Screen.Voice) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Productos", Icons.Filled.Inventory2, Modifier.weight(1f)) { vm.go(Screen.Products) }
            Tile("Facturas y Ventas", Icons.Filled.Description, Modifier.weight(1f)) { vm.go(Screen.Invoices) }
            Tile("Configuración", Icons.Filled.Settings, Modifier.weight(1f)) { vm.go(Screen.Settings) }
        }
    }
}

@Composable
private fun BigButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().height(84.dp), shape = RoundedCornerShape(22.dp)) {
        Icon(icon, null, Modifier.size(38.dp))
        Box(Modifier.padding(horizontal = 14.dp).width(1.dp).height(44.dp).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)))
        Text(text, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Icon(Icons.Filled.KeyboardArrowRight, null)
    }
}

@Composable
private fun Tile(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.height(110.dp), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(text, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

// ---------------- Componentes compartidos ----------------

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun SectionTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** Menú desplegable del vendedor. Por defecto queda el último vendedor usado. */
@Composable
fun SellerPicker(vm: MainViewModel) {
    val employees by vm.employees.collectAsState()
    var open by remember { mutableStateOf(false) }
    Panel {
        SectionTitle(Icons.Filled.Badge, "Vendedor")
        if (employees.isEmpty()) {
            Text(
                "No hay empleados. Agrégalos en Base de Datos → Agregar Empleado.",
                color = Color.Gray, fontSize = 13.sp
            )
        } else {
            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                OutlinedTextField(
                    value = vm.currentSeller(), onValueChange = {}, readOnly = true,
                    label = { Text("¿Quién hizo la venta?") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    employees.forEach { e ->
                        DropdownMenuItem(text = { Text(e.name) }, onClick = { vm.seller = e.name; open = false })
                    }
                }
            }
        }
    }
}

/** Nombre con autocompletado de clientes anteriores + teléfono + dirección. */
@Composable
fun ClientForm(state: ClientState, suggestions: List<Invoice>) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = suggestions.filter {
        state.name.isNotBlank() && it.clientName.contains(state.name, ignoreCase = true) &&
            !it.clientName.equals(state.name, ignoreCase = true)
    }.take(5)
    Panel {
        SectionTitle(Icons.Filled.Person, "Datos del Cliente")
        ExposedDropdownMenuBox(expanded = expanded && filtered.isNotEmpty(), onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { state.name = it; expanded = true },
                label = { Text("Nombre del Cliente") },
                leadingIcon = { Icon(Icons.Filled.Person, null) },
                trailingIcon = {
                    if (state.name.isNotEmpty()) IconButton({ state.name = "" }) { Icon(Icons.Filled.Close, null) }
                },
                singleLine = true,
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded && filtered.isNotEmpty(), onDismissRequest = { expanded = false }) {
                filtered.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c.clientName + if (c.phone.isNotBlank()) "  ·  ${c.phone}" else "") },
                        onClick = { state.name = c.clientName; state.phone = c.phone; state.address = c.address; expanded = false }
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.phone, onValueChange = { state.phone = it },
            label = { Text("Teléfono") }, leadingIcon = { Icon(Icons.Filled.Phone, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.address, onValueChange = { state.address = it },
            label = { Text("Dirección") }, leadingIcon = { Icon(Icons.Filled.Home, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        var payOpen by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = payOpen, onExpandedChange = { payOpen = it }) {
            OutlinedTextField(
                value = state.payment, onValueChange = {}, readOnly = true,
                label = { Text("Método de pago") },
                leadingIcon = { Icon(Icons.Filled.Payments, null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(payOpen) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = payOpen, onDismissRequest = { payOpen = false }) {
                PAYMENT_METHODS.forEach { m ->
                    DropdownMenuItem(text = { Text(m) }, onClick = { state.payment = m; payOpen = false })
                }
            }
        }
    }
}
