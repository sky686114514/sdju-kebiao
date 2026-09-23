package com.kebiao.app.feature.import

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.kebiao.app.ui.components.weekdayZh
import com.kebiao.app.ui.theme.KebiaoSpacing
import java.time.DayOfWeek
import java.time.LocalTime

/* =========================================================================
 * 手动编辑课程对话框（P1 兜底，Spec §7 / AC-40 / AC-41）
 *
 * 与导入课程并存不冲突：手动录入的 L3 落 source = 1，重新导入时不静默覆盖。
 *
 * 周次表达式直接让用户写原始串（"2-16 双周" / "2,3-4,5,8,6-7"），
 * 由 domain 的 WeekExpressionParser 解析 —— 解析失败必须显式报错，
 * 因此这里只做「非空」这一层浅校验，真正的合法性由 ViewModel 侧承接。
 * ========================================================================= */

private val TIME_PATTERN = Regex("""^\d{1,2}:\d{2}$""")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditDialog(
    initial: ManualSessionInput? = null,
    onDismiss: () -> Unit,
    onConfirm: (ManualSessionInput) -> Unit
) {
    var courseName by remember { mutableStateOf(initial?.courseName.orEmpty()) }
    var weekday by remember { mutableStateOf(initial?.weekday) }
    var periodStart by remember { mutableStateOf(initial?.periodStart?.toString().orEmpty()) }
    var periodEnd by remember { mutableStateOf(initial?.periodEnd?.toString().orEmpty()) }
    var startTime by remember { mutableStateOf(initial?.startTime?.toString().orEmpty()) }
    var endTime by remember { mutableStateOf(initial?.endTime?.toString().orEmpty()) }
    var room by remember { mutableStateOf(initial?.room.orEmpty()) }
    var teacher by remember { mutableStateOf(initial?.teacher.orEmpty()) }
    var weeksRaw by remember { mutableStateOf(initial?.weeksRaw.orEmpty()) }

    val startTimeInvalid = startTime.isNotEmpty() && !TIME_PATTERN.matches(startTime)
    val endTimeInvalid = endTime.isNotEmpty() && !TIME_PATTERN.matches(endTime)
    val canSubmit = courseName.isNotBlank() &&
        weeksRaw.isNotBlank() &&
        !startTimeInvalid &&
        !endTimeInvalid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增课程" else "编辑课程") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(KebiaoSpacing.X3)
            ) {
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("课程名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("星期（可留空：时间地点待定）", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(KebiaoSpacing.X1)) {
                    (1..7).forEach { value ->
                        val day = DayOfWeek.of(value)
                        FilterChip(
                            selected = weekday == day,
                            onClick = { weekday = if (weekday == day) null else day },
                            label = { Text(weekdayZh(day)) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(KebiaoSpacing.X2)) {
                    OutlinedTextField(
                        value = periodStart,
                        onValueChange = { periodStart = it.filter(Char::isDigit) },
                        label = { Text("起节") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = periodEnd,
                        onValueChange = { periodEnd = it.filter(Char::isDigit) },
                        label = { Text("止节") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(KebiaoSpacing.X2)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("开始 HH:mm") },
                        singleLine = true,
                        isError = startTimeInvalid,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("结束 HH:mm") },
                        singleLine = true,
                        isError = endTimeInvalid,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("地点（线上课可留空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weeksRaw,
                    onValueChange = { weeksRaw = it },
                    label = { Text("周次") },
                    supportingText = { Text("支持 2-16 双周 / 3-15 单周 / 2,3-4,5,8,6-7") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    onConfirm(
                        ManualSessionInput(
                            courseName = courseName.trim(),
                            weekday = weekday,
                            periodStart = periodStart.toIntOrNull(),
                            periodEnd = periodEnd.toIntOrNull(),
                            startTime = startTime.toLocalTimeOrNull(),
                            endTime = endTime.toLocalTimeOrNull(),
                            room = room.trim().ifBlank { null },
                            teacher = teacher.trim().ifBlank { null },
                            weeksRaw = weeksRaw.trim()
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun String.toLocalTimeOrNull(): LocalTime? =
    if (TIME_PATTERN.matches(this)) runCatching { LocalTime.parse(this) }.getOrNull() else null
