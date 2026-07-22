package com.example.healthmonitor

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WifiOff

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.core.content.ContextCompat

import com.google.android.gms.location.*

import kotlinx.coroutines.launch


// ── App State ─────────────────────────────────────────────────────
object AppState {
    var operatorName     = mutableStateOf("GHOST-6")
    var criticalCount    = mutableStateOf(0)
    var alertCount       = mutableStateOf(0)
    var connectionStatus = mutableStateOf("CONNECTING")
    var casualties       = mutableStateOf(listOf<CasualtyItem>())
}

val currentScreen = mutableStateOf("Dashboard")


// ── MainActivity ──────────────────────────────────────────────────
class MainActivity : ComponentActivity() {

    private var webViewRef: WebView? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (granted) startLocationUpdates()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.createChannel(this)

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            var isLoggedIn by remember { mutableStateOf(ApiService.isLoggedIn()) }

            if (isLoggedIn) {
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    WebSocketManager.connect(context)

                    // Load soldiers
                    val soldiers = ApiService.getSoldiers()
                    SoldierState.soldiers.clear()
                    SoldierState.soldiers.addAll(soldiers)

                    // Load squads ← ADD THIS
                    val squads = ApiService.getSquads()
                    SquadState.squads.clear()
                    SquadState.squads.addAll(squads.map { it.second })

                    // Load alerts
                    val (critical, _, total) = ApiService.getAlertSummary()
                    AppState.criticalCount.value = critical
                    AppState.alertCount.value = total

                    val alerts = ApiService.getAlerts()
                    AlertState.alerts.clear()
                    AlertState.alerts.addAll(alerts)
                }

                Dashboard(onWebViewReady = { wv -> webViewRef = wv })

            } else {
                LoginScreen(onLoginSuccess = {
                    isLoggedIn = true
                })
            }
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location: Location = result.lastLocation ?: return
                val lat = location.latitude
                val lng = location.longitude
                runOnUiThread {
                    webViewRef?.evaluateJavascript(
                        "updatePosition('P1', $lat, $lng);", null
                    )
                }
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        WebSocketManager.disconnect()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}


// ── Dashboard ─────────────────────────────────────────────────────
@Composable
fun Dashboard(onWebViewReady: (WebView) -> Unit) {

    val context = LocalContext.current

    LaunchedEffect(SoldierState.soldiers.toList()) {
        SoldierState.soldiers.forEach { soldier ->
            AlertState.evaluateRules(soldier) { alert ->
                NotificationHelper.sendAlertNotification(context, alert)
            }
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = Color(0xFF041124)
            ) {
                SidePanel()
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07111F))
        ) {
            TopBar(onEditOperator = {})

            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    when (currentScreen.value) {

                        "Alerts" -> {
                            AlertsScreen()
                        }

                        "Soldiers" -> {
                            SoldiersScreen()
                        }

                        "Configure Suit" -> {
                            ConfigureSuitScreen()
                        }

                        "Dashboard" -> {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            ) {
                                StatusSummaryBar()
                                Spacer(Modifier.height(12.dp))
                                BattlefieldMap(onWebViewReady)
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(Modifier.weight(1.6f)) { PriorityCasualtiesPanel() }
                                    Box(Modifier.weight(1f)) { RecentAlertsPanel() }
                                }
                            }
                        }

                        "Live Map" -> {
                            LiveMapScreen()
                        }

                        else -> {
                            Text("Coming soon", color = Color(0xFF6B7F99))
                        }
                    }
                }
            }
        }
    }
}


// ── Top Bar ───────────────────────────────────────────────────────
@Composable
fun TopBar(onEditOperator: () -> Unit) {

    var time by remember { mutableStateOf("") }

    val criticalCount    by AppState.criticalCount
    val alertCount       by AppState.alertCount
    val operatorName     by AppState.operatorName
    val connectionStatus by AppState.connectionStatus

    LaunchedEffect(Unit) {
        while (true) {
            val cal = java.util.Calendar.getInstance()
            time = String.format(
                java.util.Locale.getDefault(),
                "%02d:%02d:%02d",
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
                cal.get(java.util.Calendar.SECOND)
            )
            kotlinx.coroutines.delay(1000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color(0xFF081B33))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        // Left: Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "TRIAGE AI",
                color = Color(0xFF6B7F99),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(text = "  /  ", color = Color(0xFF6B7F99), fontSize = 13.sp)
            Text(
                text = currentScreen.value,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Right: indicators
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            // Connection status (LIVE / CONNECTING / OFFLINE)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            when (connectionStatus) {
                                "LIVE"       -> Color(0xFF00E676)
                                "CONNECTING" -> Color(0xFFFFD600)
                                else         -> Color(0xFFFF1744)
                            },
                            CircleShape
                        )
                )
                Text(
                    text = connectionStatus,
                    color = when (connectionStatus) {
                        "LIVE"       -> Color(0xFF00E676)
                        "CONNECTING" -> Color(0xFFFFD600)
                        else         -> Color(0xFFFF1744)
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(Modifier.width(1.dp).height(20.dp).background(Color(0xFF1A3A5C)))

            // Critical badge
            Row(
                modifier = Modifier
                    .background(
                        color = if (criticalCount > 0)
                            Color(0xFFFF1744).copy(alpha = 0.15f)
                        else
                            Color(0xFF1A3A5C),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(
                            if (criticalCount > 0) Color(0xFFFF1744)
                            else Color(0xFF6B7F99),
                            CircleShape
                        )
                )
                Text(
                    text = "$criticalCount CRITICAL",
                    color = if (criticalCount > 0) Color(0xFFFF1744) else Color(0xFF6B7F99),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(Modifier.width(1.dp).height(20.dp).background(Color(0xFF1A3A5C)))

            // Bell
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Alerts",
                    tint = Color(0xFF6B7F99),
                    modifier = Modifier.size(20.dp)
                )
                if (alertCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(Color(0xFFFF1744), CircleShape)
                            .offset(x = 4.dp, y = (-4).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$alertCount",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box(Modifier.width(1.dp).height(20.dp).background(Color(0xFF1A3A5C)))

            // Clock
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Time",
                    tint = Color(0xFF6B7F99),
                    modifier = Modifier.size(14.dp)
                )
                Text(text = time, color = Color(0xFF6B7F99), fontSize = 12.sp)
            }

            Box(Modifier.width(1.dp).height(20.dp).background(Color(0xFF1A3A5C)))

            // Operator
            Row(
                modifier = Modifier
                    .background(Color(0xFF0D2137), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable { onEditOperator() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "OPR · $operatorName",
                    color = Color(0xFF6B7F99),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = Color(0xFF6B7F99),
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}


// ── Battlefield Map ───────────────────────────────────────────────
@Composable
fun BattlefieldMap(onWebViewReady: (WebView) -> Unit) {

    var localWebView by remember { mutableStateOf<WebView?>(null) }
    val mapUpdate by LiveMapState.pendingMapUpdate

    LaunchedEffect(mapUpdate) {
        mapUpdate?.let { update ->
            localWebView?.evaluateJavascript(
                "updatePosition('${update.soldierId}', ${update.lat}, ${update.lng});", null
            )
            localWebView?.evaluateJavascript(
                "updateStatus('${update.soldierId}', '${update.status}');", null
            )
            LiveMapState.pendingMapUpdate.value = null
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.loadsImagesAutomatically = true
                settings.mixedContentMode =
                    android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                webChromeClient = android.webkit.WebChromeClient()
                webViewClient = WebViewClient()

                loadUrl("file:///android_asset/map.html")

                localWebView = this
                onWebViewReady(this)
            }
        }
    )
}


// ── Status Summary Bar ────────────────────────────────────────────
@Composable
fun StatusSummaryBar() {
    val soldiers = SoldierState.soldiers

    val active   = soldiers.count { it.status != "offline" }
    val stable   = soldiers.count { it.status == "stable" }
    val serious  = soldiers.count { it.status == "serious" }
    val critical = soldiers.count { it.status == "critical" }
    val offline  = soldiers.count { it.status == "offline" }
    val total    = soldiers.size.coerceAtLeast(1)

    val items = listOf(
        StatusSummaryItem("ACTIVE",   active,   Color(0xFF00C2FF), Icons.Default.Group),
        StatusSummaryItem("STABLE",   stable,   Color(0xFF00FF88), Icons.Default.TrendingUp),
        StatusSummaryItem("SERIOUS",  serious,  Color(0xFFFFC533), Icons.Default.ShowChart),
        StatusSummaryItem("CRITICAL", critical, Color(0xFFFF445A), Icons.Default.Favorite),
        StatusSummaryItem("OFFLINE",  offline,  Color(0xFF6B7F99), Icons.Default.WifiOff),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Box(modifier = Modifier.weight(1f)) {
                StatusSummaryCard(item, total)
            }
        }
    }
}


// ── Single Status Card ────────────────────────────────────────────
@Composable
fun StatusSummaryCard(item: StatusSummaryItem, total: Int = 7) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF081B33)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.label,
                    color = Color(0xFF6B7F99),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(item.color.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = item.color,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = "${item.count}",
                color = item.color,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 36.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(item.color.copy(alpha = 0.25f), RoundedCornerShape(1.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(item.count.toFloat() / total.toFloat())
                        .background(item.color, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}


// ── Priority Casualties Panel ─────────────────────────────────────
@Composable
fun PriorityCasualtiesPanel() {

    // Derive priority casualties directly from live soldier state.
    // AppState.casualties is not wired to the backend, so we build the
    // display list here from SoldierState rather than overwriting badge
    // counts with stale/empty data via a LaunchedEffect.
    val criticalSoldiers = SoldierState.soldiers.filter { it.status == "critical" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF081B33)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "PRIORITY CASUALTIES",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Spacer(Modifier.height(16.dp))

            if (criticalSoldiers.isEmpty()) {
                Text("No critical casualties", color = Color(0xFF6B7F99), fontSize = 13.sp)
            } else {
                criticalSoldiers.forEach { soldier ->
                    val casualty = CasualtyItem(
                        name     = "${soldier.rankTitle} ${soldier.name}",
                        subtitle = "${soldier.serial} · ${soldier.squad}",
                        status   = soldier.status,
                        percent  = 100 - soldier.battery   // battery consumed = risk proxy
                    )
                    CasualtyRow(casualty)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}


// ── Single Casualty Row ───────────────────────────────────────────
@Composable
fun CasualtyRow(item: CasualtyItem) {
    val dotColor = when (item.status) {
        "stable"   -> Color(0xFF00E676)
        "serious"  -> Color(0xFFFFD600)
        "critical" -> Color(0xFFFF1744)
        else       -> Color(0xFF757575)
    }
    val percentColor = when {
        item.percent >= 80 -> Color(0xFFFF1744)
        item.percent >= 40 -> Color(0xFFFFD600)
        else               -> Color(0xFF00E676)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(14.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = item.subtitle, color = Color(0xFF6B7F99), fontSize = 12.sp)
        }
        Text(text = "${item.percent}%", color = percentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}


// ── Recent Alerts Panel ───────────────────────────────────────────
@Composable
fun RecentAlertsPanel() {
    val recentAlerts = AlertState.alerts.take(4)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF081B33)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "RECENT ALERTS",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Spacer(Modifier.height(16.dp))

            if (recentAlerts.isEmpty()) {
                Text("No alerts", color = Color(0xFF6B7F99), fontSize = 13.sp)
            } else {
                recentAlerts.forEach { alert ->
                    RecentAlertRow(alert)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun RecentAlertRow(item: AppAlert) {
    val dotColor = when (item.severity) {
        "critical" -> Color(0xFFFF1744)
        "warning"  -> Color(0xFFFFD600)
        else       -> Color(0xFF00E676)
    }
    val titleColor = when (item.severity) {
        "critical" -> Color(0xFFFF5252)
        "warning"  -> Color(0xFFFFD600)
        else       -> Color(0xFF00E676)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(text = item.title, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = "${item.soldierName} (${item.soldierSerial})", color = Color(0xFF6B7F99), fontSize = 12.sp)
        }
    }
}


// ── Single Alert Row (for old AlertItem type) ─────────────────────
@Composable
fun AlertRow(item: AlertItem) {
    val dotColor = when (item.severity) {
        "critical" -> Color(0xFFFF1744)
        "serious"  -> Color(0xFFFFD600)
        else       -> Color(0xFF00E676)
    }
    val titleColor = when (item.severity) {
        "critical" -> Color(0xFFFF5252)
        "serious"  -> Color(0xFFFFD600)
        else       -> Color(0xFF00E676)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(text = item.title, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = item.subtitle, color = Color(0xFF6B7F99), fontSize = 12.sp)
        }
    }
}


// ── Side Panel ────────────────────────────────────────────────────
@Composable
fun SidePanel() {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(Color(0xFF041124))
            .padding(16.dp)
    ) {
        Text("TRIAGE AI", color = Color(0xFF00FF88), fontSize = 26.sp)
        Text("COMMAND CENTER", color = Color(0xFF6B7F99))

        Spacer(Modifier.height(30.dp))

        listOf(
            "Dashboard", "Live Map", "Soldiers", "Alerts",
            "Casualty Queue", "Configure Suit", "Pair New Suit",
            "Medical Records", "AI Analytics", "Reports", "Settings"
        ).forEach { item ->
            Text(
                text = item,
                color = if (currentScreen.value == item) Color(0xFF00E676) else Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { currentScreen.value = item }
                    .padding(16.dp)
            )
        }
    }
}


// ── Data Classes ──────────────────────────────────────────────────
data class CasualtyItem(
    val name: String,
    val subtitle: String,
    val status: String,
    val percent: Int
)

data class AlertItem(
    val title: String,
    val subtitle: String,
    val severity: String
)

data class StatusSummaryItem(
    val label: String,
    val count: Int,
    val color: Color,
    val icon: ImageVector
)