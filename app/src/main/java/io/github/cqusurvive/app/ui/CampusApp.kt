package io.github.cqusurvive.app.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.cqusurvive.app.MainActivity
import io.github.cqusurvive.app.OfficialLoginActivity
import io.github.cqusurvive.app.data.official.CquWebClient
import io.github.cqusurvive.app.data.official.OfficialCampusRepository
import io.github.cqusurvive.app.data.official.WebAuthState
import io.github.cqusurvive.app.domain.CampusSnapshot
import io.github.cqusurvive.app.widget.TimetableWidgetPinning
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val SHOW_COURSE_REPUTATION = false
private const val SHOW_WIDGET_PIN_SHORTCUTS = false
private const val SHOW_VACANT_CLASSROOM = false
private const val SHOW_CAMPUS_CARD_AND_LIBRARY = false

private data class Destination(val label: String, val glyph: String)
private val destinations = listOf(
    Destination("首页", "⌂"),
    Destination("课表", "▦"),
    Destination("成绩", "A+"),
    Destination("更多", "•••"),
)

@Composable
fun CampusApp(
    viewModel: CampusViewModel = viewModel(),
    requestedDestination: Int = MainActivity.HOME_DESTINATION,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val webClient = remember { CquWebClient(context) }
    val authState by webClient.authState.collectAsStateWithLifecycle()
    var selected by rememberSaveable {
        mutableIntStateOf(requestedDestination.coerceIn(0, destinations.lastIndex))
    }
    var sessionConnected by rememberSaveable { mutableStateOf(false) }
    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            sessionConnected = false
            webClient.startLogin()
        }
    }

    DisposableEffect(webClient) { onDispose { webClient.destroy() } }
    LaunchedEffect(webClient) { webClient.startLogin() }
    LaunchedEffect(requestedDestination) {
        selected = requestedDestination.coerceIn(0, destinations.lastIndex)
    }
    LaunchedEffect(authState) {
        if (authState is WebAuthState.Authenticated && !sessionConnected) {
            sessionConnected = true
            viewModel.connect(OfficialCampusRepository(webClient))
            selected = requestedDestination.coerceIn(0, destinations.lastIndex)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Text(destination.glyph, fontWeight = FontWeight.Bold) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { outerPadding ->
        Column(Modifier.fillMaxSize().padding(outerPadding)) {
            AppHeader(
                title = destinations[selected].label,
                refreshing = isRefreshing,
                onRefresh = {
                    if (authState is WebAuthState.Authenticated) {
                        sessionConnected = true
                        viewModel.connect(OfficialCampusRepository(webClient))
                    } else {
                        loginLauncher.launch(Intent(context, OfficialLoginActivity::class.java))
                    }
                },
            )
            if ((state as? CampusUiState.Ready)?.refreshing == true) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when (val value = state) {
                CampusUiState.Loading -> Centered { CircularProgressIndicator() }
                is CampusUiState.Failed -> Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::refresh) { Text("重试") }
                    }
                }
                is CampusUiState.Ready -> when (selected) {
                    0 -> HomeScreen(value.snapshot)
                    1 -> TimetableScreen(value.snapshot)
                    2 -> GradesScreen(value.snapshot)
                    else -> MoreScreen(
                        data = value.snapshot,
                        onConnect = {
                            loginLauncher.launch(Intent(context, OfficialLoginActivity::class.java))
                        },
                        onUseDemo = {
                            sessionConnected = false
                            webClient.logout()
                            viewModel.useDemoData()
                        },
                        onFirstDayChange = viewModel::setFirstDay,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppHeader(title: String, refreshing: Boolean, onRefresh: () -> Unit) {
    Surface(shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefresh, enabled = !refreshing) {
                Text(if (refreshing) "刷新中…" else "刷新")
            }
        }
    }
}

@Composable
private fun HomeScreen(data: CampusSnapshot) {
    val upcoming = data.exams.minByOrNull { it.date }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("你好，${data.profile.displayName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${data.profile.studentIdMasked} · ${data.profile.department}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (data.isDemo) item { DemoNotice() }
        item {
            InfoCard("本学期", data.term.name, "当前第 ${data.term.currentWeek} 周")
        }
        item {
            SectionTitle("常用服务")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureCard("今日课程", "${todayMeetings(data).size} 门", Modifier.weight(1f))
                FeatureCard("待考科目", "${data.exams.size} 门", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureCard("已出成绩", "${data.grades.size} 门", Modifier.weight(1f))
                if (SHOW_COURSE_REPUTATION) {
                    FeatureCard("课程口碑", "${data.ratings.size} 条", Modifier.weight(1f))
                }
            }
        }
        item {
            SectionTitle("最近考试")
            Spacer(Modifier.height(8.dp))
            if (upcoming == null) {
                Text("暂无考试安排")
            } else {
                InfoCard(
                    upcoming.courseName,
                    "${upcoming.date}  ${upcoming.start}–${upcoming.end}",
                    "${upcoming.location} · 座位 ${upcoming.seat}",
                )
            }
        }
        item {
            Text(
                "数据仅供查询参考，请以学校官方系统通知为准。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GradesScreen(data: CampusSnapshot) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (data.gradesStale && data.grades.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("正在使用上次成绩", fontWeight = FontWeight.Bold)
                        data.gradesUpdatedAtEpochMillis?.let {
                            Text(formatGradeUpdateTime(it), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureCard("已修学分", "%.1f".format(data.grades.sumOf { it.credit }), Modifier.weight(1f))
                FeatureCard(
                    "官方综合绩点（GPA）",
                    data.officialGpa?.let(::formatOfficialGpa) ?: "—",
                    Modifier.weight(1f),
                )
            }
        }
        item { SectionTitle("我的成绩") }
        items(data.grades, key = { "${it.termName}-${it.courseName}" }) { grade ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(grade.courseName, fontWeight = FontWeight.SemiBold)
                        Text("${grade.termName} · ${grade.nature} · ${grade.credit} 学分", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(grade.score, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (SHOW_COURSE_REPUTATION) {
            item {
                Spacer(Modifier.height(6.dp))
                SectionTitle("课程口碑（聚合数据）")
                Text("仅展示达到最低样本量的匿名汇总结果，不展示个人评教记录。", style = MaterialTheme.typography.bodySmall)
            }
            items(data.ratings, key = { "${it.courseName}-${it.teacher}" }) { rating ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rating.courseName, fontWeight = FontWeight.SemiBold)
                            Text("${rating.teacher} · ${rating.sampleSize} 份样本 · ${rating.updatedTerm}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("%.1f".format(rating.average), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(
    data: CampusSnapshot,
    onConnect: () -> Unit,
    onUseDemo: () -> Unit,
    onFirstDayChange: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val widgetPinningSupported = remember(context) {
        SHOW_WIDGET_PIN_SHORTCUTS && TimetableWidgetPinning.isSupported(context)
    }
    var showDateEditor by remember(data.term.id) { mutableStateOf(false) }
    var dateInput by remember(data.term.id, data.term.firstDay) {
        mutableStateOf(data.term.firstDay?.toString().orEmpty())
    }
    val parsedDate = runCatching { LocalDate.parse(dateInput.trim()) }.getOrNull()
    if (showDateEditor) {
        AlertDialog(
            onDismissRequest = { showDateEditor = false },
            title = { Text("设置课表开学日期") },
            text = {
                Column {
                    Text("请输入本学期第一教学周星期一的日期。")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = dateInput,
                        onValueChange = { dateInput = it },
                        label = { Text("日期（YYYY-MM-DD）") },
                        singleLine = true,
                        isError = dateInput.isNotBlank() && parsedDate == null,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = parsedDate != null,
                    onClick = {
                        parsedDate?.let(onFirstDayChange)
                        showDateEditor = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showDateEditor = false }) { Text("取消") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (data.isDemo) {
            item { DemoNotice() }
            item { Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("连接重庆大学官方教务") } }
        } else {
            item { InfoCard("已连接官方教务", "${data.profile.studentIdMasked} · ${data.profile.department}", "凭据仅保存在学校登录 WebView 中") }
            item { TextButton(onClick = onUseDemo, modifier = Modifier.fillMaxWidth()) { Text("退出并清除官方会话") } }
            item { SectionTitle("课表设置") }
            item {
                InfoCard(
                    "开学日期",
                    data.term.firstDay?.toString() ?: "尚未设置",
                    "设置第一教学周星期一，用于计算当前周次和周视图日期。",
                )
            }
            item {
                Button(
                    onClick = { showDateEditor = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("设置开学日期") }
            }
        }
        if (SHOW_WIDGET_PIN_SHORTCUTS) {
            item { SectionTitle("桌面小组件") }
            item {
                WidgetPinRow(
                    name = "今日课程",
                    enabled = widgetPinningSupported,
                    onAdd = { TimetableWidgetPinning.requestTodayCourses(context) },
                )
            }
            item {
                WidgetPinRow(
                    name = "下一节课",
                    enabled = widgetPinningSupported,
                    onAdd = { TimetableWidgetPinning.requestNextCourse(context) },
                )
            }
        }
        item { InfoCard("考试安排", "当前共 ${data.exams.size} 项", if (data.isDemo) "接入官方服务后可查看考场与座位" else "考试信息以学校最终通知为准") }
        if (SHOW_VACANT_CLASSROOM) {
            item { InfoCard("空教室", "等待接口验证", "将按校区、楼栋、周次和节次筛选") }
        }
        if (SHOW_CAMPUS_CARD_AND_LIBRARY) {
            item { InfoCard("校园卡与图书馆", "规划中", "各模块独立授权，可按需开启") }
        }
        item { HorizontalDivider() }
        item {
            SectionTitle("隐私与安全")
            Spacer(Modifier.height(8.dp))
            Text("• 不在源码或日志中记录密码、Cookie、Token\n• 默认禁止明文 HTTP\n• 敏感数据不参与云备份\n• 登录优先使用学校官方页面和系统凭据存储\n• 不绕过验证码、访问控制或频率限制")
        }
        item {
            SectionTitle("独立项目声明")
            Spacer(Modifier.height(8.dp))
            Text("本应用不是重庆大学官方应用，也不代表原 321CQU 团队。所有查询结果以学校官方系统为准。")
            Spacer(Modifier.height(8.dp))
            Text(
                "课程口碑功能暂未开放。如需查询历年成绩分布，请使用 321CQU 查看。此类历史聚合数据不属于重庆大学官方教学评价结果，其来源、完整性和服务可用性请以 321CQU 的说明为准。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WidgetPinRow(name: String, enabled: Boolean, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, modifier = Modifier.weight(1f))
        Button(onClick = onAdd, enabled = enabled) { Text("添加") }
    }
}

@Composable
private fun DemoNotice() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("当前为离线演示模式", fontWeight = FontWeight.Bold)
            Text("尚未连接教务系统，页面中的姓名、课程、成绩均为虚构数据。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoCard(title: String, line1: String, line2: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(line1)
            Text(line2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeatureCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private fun todayMeetings(data: CampusSnapshot) = data.meetings.filter {
    it.day == DayOfWeek.from(LocalDate.now()) && data.term.currentWeek in it.weeks
}

private fun formatOfficialGpa(value: Double): String =
    "%.3f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')

private fun formatGradeUpdateTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 更新"))
