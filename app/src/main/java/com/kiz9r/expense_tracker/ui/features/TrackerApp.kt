package com.kiz9r.expense_tracker.ui.features

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TrackerApp(vm: TrackerViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "dashboard"
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val tabs = listOf("dashboard" to Icons.Outlined.Home,"transactions" to Icons.Outlined.List,
        "statements" to Icons.Outlined.Description,"categories" to Icons.Outlined.Category,"settings" to Icons.Outlined.Settings)
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value=null } }
    Scaffold(
        topBar={TopAppBar(title={Text("SBI Expense Tracker")},navigationIcon={
            if(route !in tabs.map { it.first }) IconButton(onClick={nav.popBackStack()}) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back") }
        })},
        bottomBar={if(accounts.isNotEmpty()) NavigationBar {
            tabs.forEach { (name,icon) -> NavigationBarItem(selected=route==name,onClick={
                nav.navigate(name) { popUpTo(nav.graph.startDestinationId) { saveState=true }; launchSingleTop=true; restoreState=true }
            },icon={Icon(icon,null)},label={Text(name.replaceFirstChar { it.uppercase() })},modifier=Modifier.testTag("navigation."+name)) }
        }},
        floatingActionButton={if(accounts.isNotEmpty() && route in listOf("dashboard","transactions"))
            FloatingActionButton(onClick={nav.navigate("add")},modifier=Modifier.testTag("transactions.create")){Icon(Icons.Outlined.Add,"Add transaction")}},
        snackbarHost={SnackbarHost(snackbar)}
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Keep controls stationary as repository work begins/finishes.
            Box(Modifier.fillMaxWidth().height(4.dp)) { if(busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(!ready) Screen("startup") { Heading("Preparing your private ledger"); if(!busy) Button(onClick=vm::initialize){Text("Retry")} }
            else if(accounts.isEmpty()) AccountScreen(vm,true) {}
            else NavHost(navController=nav,startDestination="dashboard",modifier=Modifier.weight(1f)) {
                composable("dashboard") { DashboardScreen(vm) {nav.navigate("detail/$it")} }
                composable("transactions") { TransactionsScreen(vm) {nav.navigate("detail/$it")} }
                composable("add") { TransactionForm(vm,null) {nav.navigate("detail/$it"){popUpTo("add"){inclusive=true}}} }
                composable("edit/{id}") { TransactionForm(vm,it.arguments?.getString("id")) {nav.popBackStack()} }
                composable("detail/{id}") { DetailScreen(vm,requireNotNull(it.arguments?.getString("id")),{id->nav.navigate("edit/$id")},{nav.popBackStack()}) }
                composable("statements") { StatementsScreen(vm) {nav.navigate("statement/$it")} }
                composable("statement/{id}") { StatementDetailScreen(vm,requireNotNull(it.arguments?.getString("id"))) {nav.navigate("detail/$it")} }
                composable("categories") { CategoriesScreen(vm) }
                composable("settings") { SettingsScreen(vm) {nav.navigate(it)} }
                composable("accounts") { AccountScreen(vm,false) {nav.popBackStack()} }
                composable("review") { ReviewScreen(vm) {nav.navigate("add")} }
                composable("mandates") { MandatesScreen(vm) }
                composable("rules") { RulesScreen(vm) }
                composable("backup") { BackupScreen(vm) }
            }
        }
    }
}
