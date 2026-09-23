package com.kebiao.app.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kebiao.app.R

/* =========================================================================
 * 课刻 · 图标系统（Material Symbols，全项目唯一一套）
 *
 * 【硬性禁止】不得用 emoji 充当功能图标。任何 .kt / .xml / .md 里都不许出现
 *    日历、闹钟、定位针、铃铛、齿轮这类 emoji 字符。
 *
 * 【硬性禁止】androidx.compose.material.icons 已从 Material3 1.4.0 移除，
 *    写 Icons.Filled.* 会直接编译失败。本文件是唯一引用点。
 *    不引入 material-icons-extended（数千图标全进 APK）。
 *
 * 落地方式：res/drawable/ic_<snake_case>.xml（Material Symbols 官方
 * VectorDrawable，960 视口），用 painterResource 渲染。
 * 选中态用 FILL=1 变体（后缀 _fill），只换 drawable，不换颜色。
 *
 * 尺寸三档：16 Inline / 20 Compact / 24 Standard。
 * 线宽不做自定义调整——Material Symbols 是字形填充式轮廓，改线宽会让
 * 16dp 与 24dp 的视觉重量失衡。需要强调时换 tint 或换 FILL 轴。
 * ========================================================================= */

object IconSize {
    /** 16dp：与文字同行的小标识。 */
    val Inline: Dp = 16.dp
    /** 20dp：紧凑行内控件与列表项尾随。 */
    val Compact: Dp = 20.dp
    /** 24dp：图标按钮、导航项、空状态、FAB（Material 3 标准）。 */
    val Standard: Dp = 24.dp
}

/** 全 App 图标清单。新增 UI 元素时先在此查找，不允许引入表外图标库。 */
object KebiaoIcons {

    // 导航
    @DrawableRes val CalendarToday = R.drawable.ic_calendar_today
    @DrawableRes val CalendarTodayFill = R.drawable.ic_calendar_today_fill
    @DrawableRes val CalendarViewWeek = R.drawable.ic_calendar_view_week
    @DrawableRes val CalendarViewWeekFill = R.drawable.ic_calendar_view_week_fill
    @DrawableRes val Settings = R.drawable.ic_settings
    @DrawableRes val SettingsFill = R.drawable.ic_settings_fill
    @DrawableRes val ArrowBack = R.drawable.ic_arrow_back
    @DrawableRes val MoreVert = R.drawable.ic_more_vert
    @DrawableRes val ChevronRight = R.drawable.ic_chevron_right

    // 课程卡
    @DrawableRes val Schedule = R.drawable.ic_schedule
    @DrawableRes val LocationOn = R.drawable.ic_location_on
    @DrawableRes val MeetingRoom = R.drawable.ic_meeting_room
    @DrawableRes val Person = R.drawable.ic_person
    @DrawableRes val School = R.drawable.ic_school
    @DrawableRes val Timelapse = R.drawable.ic_timelapse
    @DrawableRes val Tag = R.drawable.ic_tag
    @DrawableRes val Wifi = R.drawable.ic_wifi
    @DrawableRes val Help = R.drawable.ic_help
    @DrawableRes val EventRepeat = R.drawable.ic_event_repeat

    // 课程状态
    @DrawableRes val CheckCircle = R.drawable.ic_check_circle
    @DrawableRes val RadioButtonChecked = R.drawable.ic_radio_button_checked
    @DrawableRes val Upcoming = R.drawable.ic_upcoming
    @DrawableRes val EventBusy = R.drawable.ic_event_busy
    @DrawableRes val EventAvailable = R.drawable.ic_event_available
    @DrawableRes val Warning = R.drawable.ic_warning

    // 操作
    @DrawableRes val Refresh = R.drawable.ic_refresh
    @DrawableRes val SwapVert = R.drawable.ic_swap_vert
    @DrawableRes val FileDownload = R.drawable.ic_file_download
    @DrawableRes val FileUpload = R.drawable.ic_file_upload
    @DrawableRes val Add = R.drawable.ic_add
    @DrawableRes val Edit = R.drawable.ic_edit
    @DrawableRes val Delete = R.drawable.ic_delete
    @DrawableRes val Search = R.drawable.ic_search
    @DrawableRes val FilterList = R.drawable.ic_filter_list
    @DrawableRes val Share = R.drawable.ic_share
    @DrawableRes val Close = R.drawable.ic_close
    @DrawableRes val ExpandMore = R.drawable.ic_expand_more
    @DrawableRes val ExpandLess = R.drawable.ic_expand_less

    // 提醒与小组件
    @DrawableRes val NotificationsActive = R.drawable.ic_notifications_active
    @DrawableRes val NotificationsOff = R.drawable.ic_notifications_off
    @DrawableRes val Alarm = R.drawable.ic_alarm
    @DrawableRes val Widgets = R.drawable.ic_widgets

    // 时间与周次
    @DrawableRes val ChevronLeft = R.drawable.ic_chevron_left
    @DrawableRes val History = R.drawable.ic_history
    @DrawableRes val DateRange = R.drawable.ic_date_range
    @DrawableRes val Today = R.drawable.ic_today

    // 空状态与错误
    @DrawableRes val Inbox = R.drawable.ic_inbox
    @DrawableRes val CloudOff = R.drawable.ic_cloud_off
    @DrawableRes val Error = R.drawable.ic_error
    @DrawableRes val Lock = R.drawable.ic_lock
    @DrawableRes val WifiOff = R.drawable.ic_wifi_off
}

/** 统一图标入口。尺寸只能用 IconSize 的三档，tint 默认跟随内容色。 */
@Composable
fun KebiaoIcon(
    @DrawableRes res: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = IconSize.Standard,
    tint: Color = LocalContentColor.current
) {
    Icon(
        painter = painterResource(res),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint
    )
}
