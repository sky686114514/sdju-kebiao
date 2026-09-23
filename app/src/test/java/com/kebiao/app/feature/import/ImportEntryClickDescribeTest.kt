package com.kebiao.app.feature.import

import kotlin.test.Test
import kotlin.test.assertEquals

/* =========================================================================
 * 自动点击结论 JSON -> 一句人话（[describeEntryClick]）的五条契约
 *
 * ## 为什么值得为"一句文案"写测试
 * 真机第四轮：登录成功后 App 停在门户首页，课表入口藏在左上角「菜单」抽屉里。
 * 用户此时能看到的**唯一进展说明**就是这一句话。它如果说谎（把"点开了菜单"
 * 说成"已点进课表"），用户就会一直等一个永远不会来的课表 —— 这是最贵的那类
 * 失效：不报错，只是悄悄给人错误预期。
 *
 * ## 钉死的三条
 *  1. `entry` / `menu` / `expand` 三态必须各说各的，且带上真实点中的元素文本；
 *  2. `none` 必须给出**手动路径**，绝不假装"已点进课表"；
 *  3. 空串与非法 JSON 一律说"无法解析"，**不猜**。
 *
 * 依赖说明：[describeEntryClick] 用 `org.json` 解析，而 JVM 单测里 android.jar
 * 的 org.json 是空壳（`isReturnDefaultValues` 下会静默返回 null），因此
 * `app/build.gradle.kts` 为 test 源集单独挂了真实的 org.json。这是**测试运行期**
 * 依赖，不是新测试框架，主工程的解析实现一行未改。
 * ========================================================================= */
class ImportEntryClickDescribeTest {

    @Test
    fun `点中课表入口要说清点的是哪个入口`() {
        assertEquals(
            "已自动点进「我的课表」，正在等课表渲染",
            describeEntryClick(
                """{"step":"entry","text":"我的课表","tag":"A","href":"/xk/kb"}"""
            )
        )
    }

    @Test
    fun `点开菜单要说清没看到课表入口`() {
        assertEquals(
            "没看到课表入口，已自动点开「菜单」",
            describeEntryClick(
                """{"step":"menu","text":"菜单","tag":"BUTTON","href":null}"""
            )
        )
    }

    @Test
    fun `点开分组要说清菜单里也没有课表`() {
        assertEquals(
            "菜单里也没有课表，已自动点开「选课」",
            describeEntryClick(
                """{"step":"expand","text":"选课","tag":"LI","href":null}"""
            )
        )
    }

    @Test
    fun `什么都没找到必须给出手动路径，绝不假装已点进课表`() {
        assertEquals(
            "页面上没找到课表入口，请手动点左上角菜单里的「我的课表」",
            describeEntryClick("""{"step":"none","text":null,"tag":null,"href":null}""")
        )
    }

    @Test
    fun `空串与非法 JSON 一律说无法解析，不猜`() {
        val expected = "自动进入课表：结果无法解析"
        assertEquals(expected, describeEntryClick(""))
        assertEquals(expected, describeEntryClick("not json"))
        assertEquals(expected, describeEntryClick("{}"))
    }

    /* ---- isPortalUrl：登录页绝不开点的那道闸 ----
     * 真机第四轮加固：自动点击首屏 2.5s 后就启动，那时用户还在 SSO 登录页。
     * 这道闸判错的代价是"去点登录页上的东西"，所以四种输入各钉一条。
     */

    @Test
    fun `isPortalUrl 对 null 判为不在门户，因此不开点`() {
        assertEquals(false, isPortalUrl(null))
    }

    @Test
    fun `isPortalUrl 对空串判为不在门户，因此不开点`() {
        assertEquals(false, isPortalUrl(""))
    }

    @Test
    fun `isPortalUrl 对 SSO 登录页判为不在门户，因此不开点`() {
        assertEquals(false, isPortalUrl("https://authserver.sdju.edu.cn/authserver/login"))
    }

    @Test
    fun `isPortalUrl 对教务门户判为已到门户，此时才允许自动点击`() {
        assertEquals(true, isPortalUrl("https://jwgl.sdju.edu.cn/home"))
    }
}
