package com.kiz9r.expense_tracker.ui.features

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import android.net.Uri
import com.google.gson.Gson
import com.kiz9r.expense_tracker.data.HistoryFilter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
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
    val analytics: com.kiz9r.expense_tracker.analytics.AnalyticsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory=vm.analyticsFactory)
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "dashboard"
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val largeNavigationText=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.5f
    val tabs = listOf("dashboard" to Icons.Outlined.Home,"transactions" to Icons.AutoMirrored.Outlined.List,
        "plan" to Icons.Outlined.AccountBalanceWallet,"insights" to Icons.Outlined.Insights,"settings" to Icons.Outlined.Settings)
    fun topLevel(destination: String) {
        if(!ready || accounts.isEmpty()) return
        nav.navigate(destination) {
            popUpTo("dashboard") { inclusive=false }
            launchSingleTop=true
        }
    }
    fun navigate(destination: String) {
        if(destination in tabs.map {it.first}) topLevel(destination)
        else nav.navigate(destination) {launchSingleTop=true}
    }
    fun drill(filter: HistoryFilter) {
        nav.navigate("history/"+Uri.encode(Gson().toJson(filter.copy(page=0)))) {launchSingleTop=true}
    }
    BackHandler(enabled=ready && accounts.isNotEmpty() && route in tabs.map{it.first} && route!="dashboard") {topLevel("dashboard")}
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value=null } }
    Scaffold(
        topBar={TopAppBar(title={Text(if(route=="dashboard") "SBI / PERSONAL" else if(route.startsWith("history")) "Filtered transactions" else route.substringBefore("/").replaceFirstChar {it.uppercase()},style=MaterialTheme.typography.titleMedium)},navigationIcon={
            if(route !in tabs.map { it.first }) IconButton(onClick={nav.popBackStack()}) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back") }
        })},
        bottomBar={if(accounts.isNotEmpty()) NavigationBar {
            tabs.forEach { (name,icon) -> NavigationBarItem(selected=route==name,onClick={
                topLevel(name)
            },icon={Icon(icon,if(name=="dashboard") "Home" else name.replaceFirstChar {it.uppercase()})},alwaysShowLabel=!largeNavigationText,label={if(!largeNavigationText) Text(if(name=="dashboard") "Home" else name.replaceFirstChar { it.uppercase() },maxLines=2,style=MaterialTheme.typography.labelSmall)},modifier=Modifier.testTag("navigation."+name)) }
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
                composable("dashboard") { OverviewScreen(vm,analytics,false,::navigate,::drill) {nav.navigate("detail/$it")} }
                composable("insights") { OverviewScreen(vm,analytics,true,::navigate,::drill) {nav.navigate("detail/$it")} }
                composable("transactions") { TransactionsScreen(vm) {nav.navigate("detail/$it")} }
                composable("history/{selection}") { historyEntry ->
                    val initial=requireNotNull(historyEntry.arguments?.getString("selection"))
                    var selection by rememberSaveable {mutableStateOf(initial)}
                    val filter=remember(selection) {Gson().fromJson(selection,HistoryFilter::class.java)}
                    TransactionsScreen(vm,filter,{selection=Gson().toJson(it)}) {nav.navigate("detail/$it")}
                }
                composable("add") { TransactionForm(vm,null) {nav.navigate("detail/$it"){popUpTo("add"){inclusive=true}}} }
                composable("edit/{id}") { TransactionForm(vm,it.arguments?.getString("id")) {nav.popBackStack()} }
                composable("detail/{id}") { DetailScreen(vm,requireNotNull(it.arguments?.getString("id")),{id->nav.navigate("edit/$id")},{nav.popBackStack()}) }
                composable("plan") { BudgetsScreen(vm) {nav.navigate("budget/$it")} }
                composable("budget/{id}") { BudgetDetailScreen(vm,requireNotNull(it.arguments?.getString("id"))) {nav.navigate("detail/$it")} }
                composable("balances") { BalancesScreen(vm,::navigate) }
                composable("statements") { StatementsScreen(vm) {nav.navigate("statement/$it")} }
                composable("statement/{id}") { StatementDetailScreen(vm,requireNotNull(it.arguments?.getString("id"))) {nav.navigate("detail/$it")} }
                composable("categories") { CategoryManager(vm) {category-> drill(HistoryFilter(category=category))} }
                composable("settings") { SettingsScreen(vm,::navigate) }
                composable("accounts") { AccountScreen(vm,false) {nav.popBackStack()} }
                composable("review") { ReviewScreen(vm) {nav.navigate("add")} }
                composable("mandates") { MandatesScreen(vm) }
                composable("rules") { RulesScreen(vm) }
                composable("backup") { BackupScreen(vm) }
            }
        }
    }
}
