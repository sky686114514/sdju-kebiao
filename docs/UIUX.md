# 课表 App UIUX 设计规范（Phase 1 设计调研与方向定义）

> 产品：上海电机学院课表 App（Kotlin + Jetpack Compose + Material 3）
> 用户画像：自动化专业 2026 级大一，自用个人项目
> 设计师：颜好看 ｜ 生成日期：2026-09-20
> 设计契约源文件：`docs/design-tokens.json`、`docs/design-tokens.kt`

---

## 0. 一句话结论

把课表做成一件每天愿意打开四次的东西：**冷色中性底 + 单一电控蓝强调色 + 8 色课程识别环 + 克制的功能性动效**。视觉上对标 Linear 的秩序感与 Notion Calendar 的日程语言，动效上对标 Things 3 的入退场节奏。不做毛玻璃、不做渐变主视觉、不做大标题加宣传语。

---

## 1. 设计方向总览

### 1.1 寄存器与平台轴

| 判断轴 | 取值 | 依据 |
|---|---|---|
| 设计寄存器 | **product（设计服务产品）** | 交付物是 app UI 与工具界面，不是营销页。标杆是"赢得熟悉感"：会用 Linear / Notion / Apple Calendar 的人坐下来就信任这个界面，不会在某个怪组件上停顿。 |
| 平台轴 | **android** | 原生 Android + Compose。适用 Material Design 3 规范：compact 宽度用底部 navigation bar、edge-to-edge + window insets、触控目标 48dp 起、sp 单位、Material 色彩角色、尊重系统返回手势与 Remove animations。 |

寄存器决定动作标杆。本项目的具体落点：

- **colorize**：Restrained 策略。中性色占 85% 以上面积，强调色只用于主操作、当前选中、状态指示，每屏 ≤ 2 处内容区强调色。
- **typeset**：一族系统字体 + 一套数字等宽字体，`sp` 固定阶梯，步进比 1.125 到 1.2 之间。
- **layout**：可预测网格。主界面用左对齐列表（时间轴 + 课程卡），不是居中 Hero。
- **animate**：状态切换 150 到 350ms，传递状态而非装饰。**但用户显式要求过渡动画，所以动效强度调到 7，代价是每一个动画都必须有明确的功能理由**。
- **bolder**：不做戏剧化，只做层级更清与密度更准。
- **quieter**：去掉装饰，不靠模糊与发光。

### 1.2 三轴刻度

```
DESIGN_VARIANCE  = 5   // 产品型工具要可预期。主界面左对齐时间轴 + 右侧课程流，是非对称的；但网格与间距严格对齐，不用 Masonry。
MOTION_INTENSITY = 7   // 用户显式要求。全部投在功能性动效：页面切换、学期刷新编排、列表入场、状态变化、空状态、下拉刷新、周次切换。不做装饰性动画。
VISUAL_DENSITY   = 5   // 两个页面两种密度：今日课程走呼吸感（卡片间距 12dp，卡内 16dp），周视图走仪表盘密度（格间距 4dp，无卡片盒子，仅 1dp 线分隔）。
```

三个刻度带来的具体约束：

- `Variance 5`：**禁止居中 Hero 式大标题 + 副标题 + 居中按钮**。主界面第一眼就是今天真实的四门课，不是一句宣传语。
- `Motion 7`：**必须有持续微动画**（进行中课程的状态点脉冲），但同一时刻动画元素 ≤ 3 个。
- `Density 5`：**今日课程用卡片，周视图不用卡片**。周视图用 `divide-y` 思路的 1dp 线分隔数据，避免 30 多个卡片盒子堆成视觉噪音。

### 1.3 对标品牌

| 对标 | 借鉴什么 | 不借鉴什么 |
|---|---|---|
| **Linear** | 中性底 + 单一强调色 + 严格 4dp 网格 + 键盘与焦点可见性；低饱和但有精确层级 | 深色优先与密集键盘快捷键（这是自用课表，触摸为主） |
| **Notion Calendar** | 日程的时间轴视觉语言、日期与区块分组的对齐方式、整日与定时条目的视觉区分 | 桌面端的宽屏多列布局 |
| **Things 3** | 列表项入退场的节奏（短促、退场比入场快）、克制的精致感、空状态的处理方式 | 大量的留白（移动端课表信息量大，留白要克制） |
| **Apple Calendar** | 月/周/日三级的切换方式、颜色标记的使用节制、深浅色一致的双套观感 | iOS 独有的 HIG 控件形态（本项目是 Android） |
| **TimeTree** | 日程与颜色标签的管理方式：颜色可命名、可排序、可换；整天条目与定时条目的配色规则不同 | 社交协作功能（个人自用不需要） |

### 1.4 整体风格方向与取舍

**选定：Material 3 底子上的"仪表盘精密风"（Instrumented Clarity）。**

风格库参考 `references/design-systems/ui-styles-library.md` 第 29 号 `Flat Design Mobile（触屏扁平）` 与第 30 号 `Material You (MD3) Mobile`，取 29 号的"粗字重差异建立层级"与 30 号的"tonal 分层 + 状态化"，剥掉 30 号的动态取色。

明确取舍与理由：

| 被排除的风格 | 为什么不用 |
|---|---|
| **Glassmorphism / Liquid Glass**（ui-styles-library 第 3、12 号） | `backdrop-filter` 类效果在 Android 中低端机上直接掉到 30fps 以下。这是每天开四次的工具，性能预算比一次性惊艳重要。另外毛玻璃卡片叠在课程色卡上会让 8 色识别度塌掉。 |
| **Aurora UI 紫色渐变**（第 10 号） | 直接命中项目红线（紫到粉渐变）。而且这类流动渐变背景会吞掉课程卡的颜色标识，对课表是功能性伤害。 |
| **Claymorphism**（第 9 号） | 教育行业里它对应的是 K12 与儿童产品（决策树里"儿童 / 教育轻量"分支）。用户是大一工科生，圆润 3D 蓬松与自动化专业的精密气质相反。 |
| **Dark Mode OLED 纯黑**（第 7 号） | 纯黑 `#000` 与纯白 `#fff` 直接使用会让文字在滚动时产生视疲劳拖影，且 OLED 纯黑下 1dp 边框几乎不可见，层级只能靠发光边缘，那是装饰性的。本项目深色底用 `#0E1116`，靠亮度递进分层。 |
| **Brutalism / Neubrutalism**（第 4、16 号） | 硬边硬色与课表需要的"快速扫描时间地点"冲突，且 2025 年已开始退潮。 |
| **Editorial Grid 杂志编辑风** | 这是 2026 年 AI 工具默认走的美学路线。除非产品真的是杂志，否则不默认走。课程表不是杂志。 |

---

## 2. 竞品 UI 调研

调研对象覆盖国内校园工具（WakeUp 课程表、超级课程表、课程格子）与日程类替代方案（Coursicle、TimeTree、TimeBlocks），参考：`references/design-systems/content-platform.md`（信息流骨架、空状态、禁 emoji 图标）与 `references/industries/enterprise.md`（信息密度与配色克制）。

### 2.1 竞品对比表

| 竞品 | 配色方式 | 课程卡片形态 | 周视图表格处理 | 空状态设计 | 可借鉴 / 要避开 |
|---|---|---|---|---|---|
| **WakeUp 课程表** | 用户自选主题皮肤（10 余款）+ 可自定义课程格颜色、边框色、文字色；有浅色/深色跟随系统 | 课程格三态：普通、非本周（更透明）、时间冲突（Android 用格子右下角三角形，iOS 用右上角数字） | 左侧固定时间轴显示节数 + 上下课时间（可隐藏具体时间只留节数）；课程格可点击弹出详情卡；冲突时详情卡堆叠、左右滑动切换 | 弱。依赖"添加课程后就有内容" | **借鉴**：课程格三态的表达（非本周降透明度、冲突显式标记）、时间轴只显示节数可切换的选项、深浅跟随系统。**避开**：让用户自定义边框色与文字色（配置项爆炸，且用户配出来的对比度往往不达标）；冲突用右下角三角形这种小角落标记（在 48dp 格子里几乎看不见）。 |
| **超级课程表 / 课程格子** | 高饱和多色课程块铺满整格，彩虹感强；国内同类工具的默认观感 | 整格高饱和大色块 + 白字，课程名竖排或截断 | 传统周一至周日 × 节次的固定表格，横向 7 列在手机上挤压严重 | 弱 | **借鉴**：整周课表"一眼看到分布"的目标。**避开**：整格铺高饱和色（12 门课直接变彩虹灾难，且白字压在浅黄、浅绿上对比度不达标）；横向 7 列硬塞在 360dp 宽度的手机上，课程名只能截断到 3 个字。 |
| **Coursicle** | 十余套手作主题色板（Cherry Blossom、Midnight、Evergreen 等），每套同时支持浅色与深色；可叠加飘落花瓣、落叶、雪、雨的动画效果 | 每门课自动分配一个可区分色，同一门课全周同色（"一色一课"原则）；也支持按类别着色（讲座 / 实验 / 工作 / 个人）与按优先级着色（暖色给高优先级、冷色给低优先级） | 周视图以整块色条为主，主打"扫一眼看出一周节奏" | 有明确的引导 | **借鉴**：**一色一课**（同一门课全周同色，形成肌肉记忆）、色板同时适配浅深两套、把课程色当"识别标记"而不是装饰。**避开**：花瓣雪雨这类装饰性全屏动画（用户要的是过渡动画，不是天气特效，且全屏持续动画直接违反性能预算）。 |
| **TimeTree** | 每本共享日历设一个主题色；整日/跨日条目用"深色底 + 白字"，有起止时间的条目用"浅色底 + 彩色文字"；颜色可命名、可排序 | 条目（chip）形态，颜色区分日历归属 | 月视图 + 列表视图切换，不以周网格为主 | 中等 | **借鉴**：**同一套色在不同语义下用不同明度**（整日条目深底白字、定时条目浅底彩字）。这个思路直接用在我们的课程状态上：进行中用 accent 实底白字 pill，即将开始用中性底深字 pill。**避开**：以月视图为主（课表的核心是周，不是月）。 |
| **TimeBlocks** | 12 色可选 + 颜色助手（自动匹配周边颜色）；颜色标签可命名管理；外部日历颜色兼容性差 | 条目形态 | 月视图 + 列表 | 中等 | **借鉴**：颜色标签可命名（我们已支持，色卡 8 色都有中文名）。**避开**：让用户在 12 色里手选（选择过载，且不保证课与课之间可区分）。我们用代码哈希自动分配。 |

### 2.2 从竞品提炼的四条设计约束

1. **一色一课，全周同色，自动分配。** 用户不该在 12 个颜色里做选择。用课程代码稳定哈希，同一门课在今日课程、周视图、Widget、通知里永远同色。
2. **课程色只做识别标记，不做大面积填充。** 竞品的彩虹灾难全部来自"整格铺色"。我们把颜色压到 8dp 圆点与约 10% 面积的低饱和 tint 底上。
3. **状态必须多重编码。** WakeUp 用"更透明"表示非本周，在小格子里太弱。我们用不透明度 + 虚线边框 + 文字标签三重编码，且离开颜色也能读懂。
4. **周视图不能横向硬塞 7 列在手机上。** 手机竖屏改为"逐日列 + 横向 Pager 切换周次"，或者把周网格做成可横向轻微拖动的紧凑网格。详见第 7 节页面设计。

---

## 3. 设计语言

### 3.1 配色基调

**中性底 + 单一强调色 + 8 色课程识别环。** 参考 `references/design-systems/color-palettes.md` 的 Restrained 策略与四层配比（中性 70 到 90%、强调 5 到 10%、语义 0 到 5%、效果 < 1%）。

#### 强调色：电控蓝 `#1D6FE8`

| 语义 | 浅色主题 | 深色主题 |
|---|---|---|
| accent（品牌与主操作） | `#1D6FE8` | `#6FA8FF` |
| on accent | `#FFFFFF` | `#06152B` |
| accent container | `#D6E6FD` | `#1D3050` |
| accent tint（进行中卡片底） | `#E7F0FE` | `#16243A` |
| accent hover / active | `#1A66D5` / `#175CBF` | `#8ABAFF` / `#A9CBFB` |

**为什么这个蓝适合"课程表"这个场景：**

1. **冷色底是高频工具的唯一正解。** 用户一天开四次，早八一次、午间一次、下午一次、睡前一次。冷色中性底与冷色强调色是唯一不会在第四周产生视觉疲劳的选择。暖底（奶油、米色、沙色）在第一次打开时显得温柔，第三十次打开时显得脏。
2. **蓝色在中文校园语境里没有负面联想。** 它对应知识、秩序、可靠，而不是"危险"（红）、"警告"（黄）、"促销"（橙）。
3. **色相 216 是"蓝"，不是"靛紫"。** 明确避开 Tailwind 默认那个一眼 AI 的靛色（`indigo-500`），同时确认本项目色彩方案里不存在任何紫色到粉色的渐变组合。项目红线段点名的紫色系通道全部未使用，仅课程色卡第 8 号是紫色纯色（无渐变），且与第 1 号蓝间隔两个色卡位以保持可分辨距离。
4. **冷底 + 暖点形成层级。** 8 色课程环里天然包含暖色（琥珀 `#9A6206`、橙红 `#B94A1A`）。在冷底上，暖色点会自然跳出，这正是"进行中"需要的注意力效果。如果底色也是暖的，就没有这个杠杆。
5. **对比度已校验：** `#1D6FE8` 对白底 4.67:1，白字压在 accent 上 4.67:1，两项均过 WCAG AA 的 4.5:1。

#### 课程识别色卡（8 色）与"如何避免 12 门课变成彩虹灾难"

**灾难是怎么产生的：** 竞品的做法是给每门课一段高饱和色，铺满整个课程格，再配上白字。12 门课并列时，屏幕上有 12 个面积占比 5% 到 8% 的高饱和色块，眼球无法建立主次，同时浅色块（浅黄、浅绿）上的白字对比度掉到 2:1 以下。

**我们的四条对策：**

| 对策 | 做法 | 效果 |
|---|---|---|
| **① 色相环同族化** | 8 色共享相近明度与中等彩度，只变色相，且色相刻意分离：216° / 188° / 152° / 72° / 38° / 18° / 330° / 268° | 8 门课并列时看起来像一套"系统色"，而不是随手抓来的彩虹。色相分离保证两门课不因色相相邻而混淆 |
| **② 面积压制到 10% 以下** | 课程色只出现在两个地方：课程名前的 8dp 圆点，以及约 10% 面积的低饱和 tint 底（`#E8EFFC` 这类，色卡每个色都配了一个 tint） | 屏幕 90% 面积是中性的，8 个色点像仪表盘的指示灯，可扫读而不吵 |
| **③ 文字永远中性** | 课程名用 `#171A21`（浅色）/ `#E8EBF1`（深色），不用课程色。只有深色主题下允许课程名用课程色的提亮版 | 对比度恒定达标（16.3:1），且不受课程色影响 |
| **④ 一色一课而非一格一色** | 课程代码稳定哈希取模 8。`053017P1-15` 中国近现代史纲要 永远是同一个色（琥珀），周一 1-2 节与周二 3-4 节是同一个色 | 一周后用户形成肌肉记忆："琥珀那块是近代史"。这是色彩编码真正有用的地方，也是竞品做到位的少数几件事之一 |

**8 色课程识别环（浅色主题，全部已校验对白底 ≥ 4.5:1）：**

| # | 名称 | 色相 | 色值 | tint 底 | 对比度 |
|---|---|---|---|---|---|
| 1 | 蓝 | 216° | `#1F63D6` | `#E8EFFC` | 5.53:1 |
| 2 | 青 | 188° | `#0B7C8C` | `#E2F1F3` | 4.94:1 |
| 3 | 绿 | 152° | `#0F7A48` | `#E2F2EA` | 5.41:1 |
| 4 | 橄榄 | 72° | `#6B7A0F` | `#EEF1DF` | 4.79:1 |
| 5 | 琥珀 | 38° | `#9A6206` | `#F8EEDD` | 5.12:1 |
| 6 | 橙红 | 18° | `#B94A1A` | `#FAE9E1` | 5.21:1 |
| 7 | 品红 | 330° | `#AE3A78` | `#F9E6F0` | 5.79:1 |
| 8 | 紫 | 268° | `#6E4FD8` | `#EEEAFB` | 5.57:1 |

深色主题的 8 色（提亮版，对 `#161A21` 全部 ≥ 6.4:1）：`#7CA6FF` / `#4FC0D0` / `#59C48E` / `#B3C24D` / `#E2B055` / `#F0885A` / `#F085BC` / `#A88CFA`。

> 说明：色卡第 8 号是紫色 `#6E4FD8`。这是**纯色**使用，不构成任何渐变，不违反项目红线。红外线禁止的是 Indigo 到 Pink 的渐变组合与"渐变 + 发光边框 + 毛玻璃"的三位一体。色相 268° 与 216° 之间隔了两个色卡位，两门课不会被误读成同一门。

#### 语义色

| 角色 | 浅色 | 深色 | 用途 |
|---|---|---|---|
| success | `#14855A` | `#4CC38A` | 课表更新成功、导入完成 |
| warn | `#A16207` | `#E0A63C` | 时间冲突提示、周次异常 |
| danger | `#C62828` | `#F2706A` | 教务系统登录失败、解析错误 |
| info | `#1D6FE8` | `#6FA8FF` | 线上课标记 |

> 注意：`--warn` 用的是 `#A16207` 这个偏深的金棕色，不是明黄 `#eab308`。原因是明黄在白底上对比度只有 1.9:1，作为文字完全不可用；它只能作为状态点，不能承载文字。

#### 每屏强调色预算

| 屏幕 | 内容区 accent 使用点 1 | 内容区 accent 使用点 2 | 是否超预算 |
|---|---|---|---|
| 今日课程（有课在进行中） | 进行中课程卡的 1.5dp accent 边框 + 状态 pill | 顶部"今天"标签 | 恰好 2 处 |
| 今日课程（无课在进行中） | 顶部"今天"标签 | 无 | 1 处 |
| 周视图 | 当天列的表头底纹 | 当前时间指示线 | 恰好 2 处 |
| 设置 | 选中项的 accent 圆点 | 无 | 1 处 |

底部 NavigationBar 的选中态属于导航 chrome，不计入内容区预算（Material 3 规范中"选中 Tab 用 primary"本身就是 accent 的合法用法）。

### 3.2 字体方案

**Android 上中文字体的现实：不打包。**

理由链：

1. 中文字体文件量级：Noto Sans SC 全量 ≥ 1MB，子集化到 3500 常用字后仍有 300KB 到 600KB。对一个 5MB 级别的课表 App 来说这是 10% 的体积换 0 功能收益。
2. 国产 Android 设备上，中文渲染的实际字体是**厂商定制字体**：小米 MiSans、华为 HarmonyOS Sans、OPPO Sans、vivo Sans，以及底层的 Noto Sans CJK / 思源黑体。这些字体的屏幕可读性都已针对手机优化，硬塞第三款中文字体只会让观感与系统割裂。
3. 强制指定第三方中文字体还会绕过系统的无障碍字号缩放与高对比度文字设置。

**因此中文字体 = `FontFamily.Default`（交给系统）。真正的品牌感放在数字上。**

**字体栈与降级顺序：**

```
数字与拉丁（时间、节次、教室编号、学时）
  1. Inter variable, latin-subset, 开启 tnum（tabular figures），约 30KB
  2. Inter 未打包时降级 -> FontFamily.Monospace（Roboto Mono / Droid Sans Mono）
  3. 等宽不可用时降级 -> FontFamily.Default
  4. tnum 不可用时降级 -> 默认数字宽度（时间轴对齐会有轻微偏移，可接受）

中文与正文
  1. FontFamily.Default -> 系统 Noto Sans CJK SC / MiSans / HarmonyOS Sans / OPPO Sans / vivo Sans
  2. 无需更多降级级，系统保证存在

字号缩放
  全部用 sp，不用 dp。系统 Font Scale 调到 200% 时布局必须不截断，因此所有容器高度用 wrapContentHeight，不用固定高。
```

**为什么数字单独走 Inter + tnum：** 这是课表 App 最关键的排版细节，也是竞品普遍做得很糙的地方。时间轴左侧要竖排 `08:10` `10:00` `12:30` `14:20` `15:55` `17:30`。如果数字是比例宽度（proportional），`1` 比 `0` 窄，那么六行时间会左右参差，时间轴看起来是歪的，读起来一直在重新定位。开启 `tnum` 后所有数字等宽，六行时间严格左对齐成一条竖线。这一条改进对"扫一眼看到今天几点上课"的价值，超过任何装饰性视觉。

**字号阶梯（`sp` / 行高 `sp` / 字距 / 字重意图值）：**

| 语义 | 字号 | 行高 | 字距 | 字重 | 用在哪 |
|---|---|---|---|---|---|
| display | 32 | 40 | -0.02em | 590 | 大数字：今天 4 门课、本周 18 课时 |
| titleLg | 22 | 28 | -0.01em | 590 | 页面主标题"今天"、"本周课表"、学期名 |
| titleMd | 18 | 24 | -0.005em | 590 | **课程名**（卡片主标题） |
| titleSm | 16 | 22 | 0 | 510 | 区块标题、状态 pill 文字 |
| body | 16 | 24 | 0 | 400 | 正文 |
| bodySm | 14 | 20 | 0 | 400 | **教室、教师、辅助说明** |
| label | 13 | 18 | 0.01em | 510 | **时间标签、chip、周次** |
| caption | 12 | 16 | 0.02em | 400 | 元数据、课程代码 |
| overline | 11 | 14 | **0.08em** | 590 | 仅 Widget 里的 `TODAY` 与全大写场景，主 App 不用 |

**字重落地说明：** 规范意图值是 400 / 510 / 590 三级（Read / Emphasize / Announce）。Android 系统字体只有离散字重档（Regular 400、Medium 500、SemiBold 600），`FontWeight(510)` 会被 snap 到 500、`FontWeight(590)` 到 600。所以落地值用 400 / 500 / 600，规范意图值保留在 token 里作为设计依据。Kotlin 文件中已用 `FontWeight.Medium` 与 `FontWeight.SemiBold` 实现。

**字距规则：** 正文 0；小字（11 到 13sp）0.01 到 0.02em；全大写必须 ≥ 0.06em；标题（≥ 22sp）负字距 -0.01 到 -0.02em。

**深色模式补偿（暗底亮字的感知重量在三轴同时下降，三轴都要补）：** 行高 +0.05（次级文字 20sp 到 21sp）、字距 +0.01em、次级与元数据文字字重由 400 升到 500。正文 16sp 保持 24sp 行高不变，避免暗色正文过度松散。

### 3.3 尺寸、间距、圆角、层级

- **间距：** 严格 4dp 网格，可用值只有 `0 / 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64`。禁用 `5 / 7 / 13 / 15 / 22 / 30`。语义角色：屏幕左右安全边距 20dp、卡片内边距 16dp、卡片间距 12dp、图标与同行文字 4dp、区块之间 24dp、大分区之间 32dp。
- **圆角：** sm 8dp（chip、状态标签）、md 12dp（卡片、输入框、周网格单元）、lg 16dp（BottomSheet 顶部、分组容器）、xl 20dp（对话框）、pill 999dp（状态 pill、FAB、进度条、周次切换器）。**卡片圆角上限 16dp**，≥ 24dp 属过度圆滑的 AI 特征，禁用。
- **层级（Hairline First）：** 默认卡片无阴影，靠 1dp 边框 + 底色差分。悬浮元素模糊 ≤ 8dp 且不加边框（避免"1dp 边框 + ≥16dp 模糊"同时出现在同一元素的幽灵卡片）。只有弹窗与 BottomSheet 允许 8dp 到 24dp 的阴影，且**只用阴影不加边框**。深色主题不用阴影表达层级，改用 `surface` → `surfaceContainer` → `surfaceContainerHigh` 的亮度递进。
- **模糊：** 全项目只允许 1 处功能性模糊：TopAppBar 在滚动经过内容时的背景模糊（blur 12dp）。不做装饰性毛玻璃。

### 3.4 明确不做的三件事

1. **不做装饰性毛玻璃卡片。** 它会让叠加其上的课程色点识别度下降，且在低端机上掉帧。
2. **不做渐变主视觉，尤其不做紫到粉。** 项目红线，且与精密工具的气质冲突。需要层次时用同色系深浅或底色差。
3. **不做 Hero 式首屏。** 主界面第一眼必须是今天真实的课程内容。`中国近现代史纲要 08:10-09:40 E教116 李彬彬` 这一行信息本身就是首屏的主角。

---

## 4. 图标系统

### 4.1 候选对比

| 候选 | 许可 | 图标数 | 网格与线宽 | Compose 接入 | 结论 |
|---|---|---|---|---|---|
| **Material Symbols** | Apache 2.0（含专利不主张条款） | 3000+ | 24dp 网格，字形固有线宽等效 2dp；可变轴 FILL / wght / GRAD / opsz | 从 material-symbols 仓库取 SVG，用 Android Studio 的 Vector Asset 导入为 `res/drawable/ic_*.xml`，`painterResource` 渲染 | **锁定** |
| Lucide | ISC | 1600+ | 严格 24dp 网格，2px 描边，圆角端点 | 需引入社区 Compose 包装（`lucide-compose` 类），非官方一等公民 | 不选 |
| Tabler Icons | MIT | 5900+ | 24dp 网格，2px 描边 | 同 Lucide，需自建 SVG 到 ImageVector 的转换链 | 不选 |
| Phosphor | MIT | 9000+ | 24dp 网格，6 种字重变体 | 需引入 `phosphor-compose`（第三方） | 不选 |

### 4.2 为什么锁定 Material Symbols

1. **与 Material 3 组件同族。** 这是产品型寄存器的核心判据。本项目用 M3 的 `NavigationBar`、`TopAppBar`、`FAB`、`Chip`、`BottomSheet`。如果图标来自 Lucide 或 Tabler，就会出现"图标是一套语言、组件是另一套语言"的错位，用户会隐约觉得界面不对劲但说不出哪里不对。Material Symbols 与 M3 组件是同一套设计规范下的产物。
2. **FILL 轴是选中态的唯一区分手段。** 底部导航未选中用 `FILL=0`（描边），选中用 `FILL=1`（实心）。这是 M3 规范的选中态表达，不需要换图标、不需要换颜色、不需要加下划线。
3. **覆盖了我们全部的场景词。** 包括校园与课程域的具体词：`calendar_view_week`（周视图）、`meeting_room`（教学楼）、`event_repeat`（单双周）、`event_busy`（今天没课）、`upcoming`（即将开始）、`timelapse`（学时）。Tabler 的覆盖更广，但广出的部分是专业数据领域，我们用不到。
4. **矢量 + 单色 tint + 三档尺寸天然可缩放。** 16dp / 20dp / 24dp 直接等比缩放，无失真。
5. **许可干净。** Apache 2.0 允许商用、允许修改，并含专利不主张条款。

**明确不做的事：** 不引入 `androidx.compose.material:material-icons-extended`。该 artifact 会把数千个图标全部编进 APK，体积代价巨大且已被官方标记为不再扩展。我们按需导入 SVG，APK 里只留用到的约 50 个 drawable。

### 4.3 Compose 接入方式

**第一步，获取 SVG。** 从 Material Symbols 官方仓库按名取 SVG。默认态取 `Outlined`，选中态取 `Filled`。

**第二步，导入为 drawable。** Android Studio 中 `res` 右键 → `New` → `Vector Asset` → `Local file (SVG, PSD)` → 命名 `ic_<snake_case>.xml`，选中态加 `_fill` 后缀。导入时 `Override` 默认尺寸设为 24dp。

**第三步，代码引用。**

```kotlin
// 默认态（未选中 / 普通）
KebiaoIcon(
    res = KebiaoIcons.CalendarToday,
    contentDescription = "今日课程",
    size = IconSize.Standard
)

// 选中态：FILL 轴切换，只换 drawable，不换颜色
NavigationBarItem(
    selected = isSelected,
    icon = {
        KebiaoIcon(
            res = if (isSelected) KebiaoIcons.CalendarTodayFill else KebiaoIcons.CalendarToday,
            contentDescription = null,
            size = IconSize.Standard,
            tint = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
    },
    label = { Text("今天") },
    onClick = { }
)

// 行内小图标：与文字同一行
Row(verticalAlignment = Alignment.CenterVertically) {
    KebiaoIcon(KebiaoIcons.Schedule, null, size = IconSize.Inline,
        tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(KebiaoSpacing.InlineIconGap))
    Text("08:10-09:40", style = AppType.time())
}
```

**可选的加速路径（不推荐但可行）：** 若想少导 50 个文件，可引入 Material Symbols 可变字体 `material_symbols_outlined.ttf` 到 `res/font/`，用 `FontVariation.Setting("FILL", 1f)` 切换选中态，用 `Text` 渲染字形码点。代价是：可变字体大约 3MB 到 8MB，且需要硬编码码点常量。对本项目这个体积不可接受，所以走 SVG 路线。

### 4.4 尺寸与线宽规范

| 尺寸 | 用在哪 | 等效线宽 |
|---|---|---|
| **16dp（Inline）** | 与文字同一行的小标识：时间行前的 `schedule`、教室前的 `location_on`、卡片内 chip 前置图标、状态 pill 内的图标 | 约 1.33dp |
| **20dp（Compact）** | 紧凑行内控件：列表项尾随的 `chevron_right`、Badge 内图标、筛选 chip 前置图标、TopAppBar 内的次级图标 | 约 1.67dp |
| **24dp（Standard）** | 图标按钮（`IconButton`）、`NavigationBar` item、`TopAppBar` action、空状态主图、`FAB` 内图标。这是 Material 3 的标准尺寸 | 2dp |

**线宽说明：** Material Symbols 是**字形填充式轮廓**（不是 stroke 描边），所以"描边宽度"以字形固有设计线宽为准，24dp 网格下等效 2dp，全字重与全尺寸下保持一致。**我们不对图标做自定义加粗或调线宽**，因为一旦调整，同一个图标在 16dp 与 24dp 下的视觉重量就会失衡。如果需要强调，用 `tint` 换色或换 `FILL` 轴，不动线宽。

**触控目标：** 图标本体 24dp 时，用 12dp 内边距把命中区补足到 48dp（Material 3 标准）。16dp 与 20dp 的图标如果可点击，命中区统一补到 48dp，不按视觉尺寸给命中区。

### 4.5 图标清单（完整，49 项）

**导航（9）**

| 业务语义 | Material Symbols 名 | drawable |
|---|---|---|
| 主界面 今日课程 | `calendar_today` | `ic_calendar_today` / `_fill` |
| 周视图 | `calendar_view_week` | `ic_calendar_view_week` / `_fill` |
| 设置 | `settings` | `ic_settings` / `_fill` |
| 返回 | `arrow_back` | `ic_arrow_back` |
| 更多 | `more_vert` | `ic_more_vert` |
| 列表项进入 | `chevron_right` | `ic_chevron_right` |

**课程卡（10）**

| 业务语义 | Material Symbols 名 |
|---|---|
| 节次与时间 | `schedule` |
| 上课地点 | `location_on` |
| 教学楼 | `meeting_room` |
| 任课教师 | `person` |
| 学院与专业 | `school` |
| 学时 | `timelapse` |
| 课程代码 | `tag` |
| 纯线上课（尔雅通识、线上教学） | `wifi` |
| 时间地点待定（军事技能） | `help` |
| 单双周 | `event_repeat` |

**课程状态（6）**

| 业务语义 | Material Symbols 名 |
|---|---|
| 已结束 | `check_circle` |
| 正在进行中 | `radio_button_checked` |
| 即将开始 | `upcoming` |
| 今天没课 | `event_busy` |
| 本周不上（单双周） | `event_available` |
| 时间冲突 | `warning` |

**操作（13）**

| 业务语义 | Material Symbols 名 |
|---|---|
| 更新课表 | `refresh` |
| 切换学期 | `swap_vert` |
| 导入课表 | `file_download` |
| 导出课表 | `file_upload` |
| 新建与添加 | `add` |
| 编辑课程 | `edit` |
| 删除课程 | `delete` |
| 搜索课程 | `search` |
| 筛选 | `filter_list` |
| 分享课表 | `share` |
| 关闭 | `close` |
| 展开 | `expand_more` |
| 收起 | `expand_less` |

**提醒与小组件（4）**

| 业务语义 | Material Symbols 名 |
|---|---|
| 上课提醒 | `notifications_active` |
| 提醒已关闭 | `notifications_off` |
| 提醒提前量 | `alarm` |
| 桌面小组件 | `widgets` |

**时间与周次（5）**

| 业务语义 | Material Symbols 名 |
|---|---|
| 上一周 | `chevron_left` |
| 下一周 | `chevron_right` |
| 学期列表与历史 | `history` |
| 学期起止 | `date_range` |
| 回到今天 | `today` |

**空状态与错误（5）**

| 业务语义 | Material Symbols 名 |
|---|---|
| 学期无课表 | `inbox` |
| 网络失败 | `cloud_off` |
| 导入失败与解析错误 | `error` |
| 教务系统未登录 | `lock` |
| 无网络连接 | `wifi_off` |

> **禁止事项：** 任何位置不得使用 emoji 作为功能图标。UI 文案、设计文档、Drawable 资源名、Kotlin 注释、字符串资源里都不得出现日历、闹钟、定位针、铃铛、齿轮这类 emoji 字符，一律改用本清单里的 Material Symbols 图标名。需要新图标时，先在本清单里查找，找不到就补进本清单，不允许从其他图标库取。

---

## 5. 过渡动画规范（本次设计的重点交付物）

> 这一节的所有数值都可直接落成 Compose 代码。Kotlin 侧统一入口是 `MotionTokens`，UI 层调用 `MotionTokens.Recipe.*`，不自己写时长与曲线。

### 5.1 全局约束（先立规矩，再谈场景）

| 约束 | 值 | 说明 |
|---|---|---|
| 时长阶梯 | `100 / 150 / 250 / 350 / 500` ms | 150ms 为全项目收敛基准值。500ms 是上限，超过需专项审批 |
| 缓动曲线 | 4 条，全部为三次贝塞尔 | `standard (0.2, 0, 0, 1)`、`emphasized (0.05, 0.7, 0.1, 1)`、`decelerate (0, 0, 0.2, 1)`、`accelerate (0.3, 0, 1, 1)` |
| 弹簧 | 全部 `dampingRatio = 1.0`（NoBouncy） | 刚度三档：`1500`（点按反馈）、`400`（容器变化与重排）、`200`（高度展开折叠）。**NoBouncy 在数学上不存在回弹** |
| **禁止** | 过冲型（overshoot）缓动：三次贝塞尔控制点 y 值超出 0 到 1 区间的曲线（下冲后回弹的玩具感曲线属于此类），以及任何 elastic 曲线 | 也禁用 `Spring.DampingRatioMediumBouncy` 与 `Spring.DampingRatioHighBouncy`。M3 默认的 `MotionScheme.expressive()` 带轻微回弹，本项目不用，直接用 `MotionTokens` |
| 退场比入场快 | 退场时长约为入场的 50% 到 75% | 例：列表项入场 180ms，旧列表退场 140ms |
| 允许动画的属性 | 只允许 `alpha`、`translationX / Y`、`scaleX / Y`、`rotationZ` | 全部走 `graphicsLayer`，不触发重组与重新布局 |
| **禁止动画的属性** | `width`、`height`、`top`、`left`、`margin`、`padding` | 需要高度变化时用 `animateContentSize()` 或 `Modifier.animateItem()`，且只在数据变化时跑一次 |
| 同时动画元素上限 | **≤ 3 个** | 这是 token-standard 的硬约束。见 5.9 的 stagger 重叠计算 |
| 滚动期间动画数 | **0 个** | 入场动画只在首屏静止时播一次，用 `rememberSaveable` 标记。滚动回看不再重播 |
| 帧预算 | 60Hz 单帧 16.6ms / 120Hz 单帧 8.3ms | 见 5.10 |

### 5.2 页面切换

#### A. 同级页面（底部导航：今日课程 ↔ 周视图 ↔ 设置）

用 Material 3 的 **fade-through**。同级页面不做水平位移，因为位移会暗示"层级方向"，而三个页面是平等的，硬给方向会让用户误判返回行为。

```kotlin
// NavHost 的 composable 内
AnimatedContent(
    targetState = currentTab,
    transitionSpec = {
        (fadeIn(tween(250, easing = Ease.Decelerate)) +
            scaleIn(tween(250, easing = Ease.Decelerate), initialScale = 0.94f))
            .togetherWith(fadeOut(tween(90, easing = Ease.Accelerate)))
            .using(SizeTransform(clip = false))   // 不做尺寸动画，避免开销
    },
    label = "tabFadeThrough"
) { tab -> TabContent(tab) }
```

| 参数 | 值 |
|---|---|
| 入场 | `fadeIn` + `scaleIn`，250ms，`decelerate` |
| 入场缩放 | `initialScale = 0.94f`（不是 0.8，避免"弹出感"） |
| 出场 | `fadeOut`，90ms，`accelerate` |
| sizeTransform | `null`（禁用尺寸变换） |

#### B. 层级页面（课程详情 / 学期管理 / 设置子页）

用 **shared-axis X（水平共享轴）**。进入时新页从右推入并轻微淡入，旧页向左让位。

```kotlin
transitionSpec = {
    if (targetState.isPushed) {
        (slideInHorizontally(tween(350, easing = Ease.Emphasized)) { it / 4 } +
            fadeIn(tween(350, easing = Ease.Decelerate)))
            .togetherWith(
                slideOutHorizontally(tween(350, easing = Ease.Emphasized)) { -it / 6 } +
                    fadeOut(tween(350, easing = Ease.Accelerate))
            )
    } else {   // 返回：enter / exit 互换，offset 符号取反
        (slideInHorizontally(tween(350, easing = Ease.Emphasized)) { -it / 6 } +
            fadeIn(tween(350, easing = Ease.Decelerate)))
            .togetherWith(
                slideOutHorizontally(tween(350, easing = Ease.Emphasized)) { it / 4 } +
                    fadeOut(tween(350, easing = Ease.Accelerate))
            )
    }
}
```

| 参数 | 值 |
|---|---|
| 时长 | 350ms，`emphasized` |
| 新页 offset | 从 `width / 4` 推入（不是整屏宽，避免"刷屏"感） |
| 旧页 offset | 向左 `width / 6` 让位 |
| **预测性返回** | Android 14+ 的返回手势需与 `initialOffsetX` 的进度关联，不能固定 350ms 硬播。用 `PredictiveBackHandler` + `Animatable` 把手指位移映射到 offset，松手后再交给 `emphasized` 收敛 |

### 5.3 内容更新：切换学期后当天课程列表整体刷新

这是本 App 最重要的一段编排，总预算 **500ms**。四个动作按时间线错开，不是同时播。

| 时间 | 目标 | 动画 | 参数 |
|---|---|---|---|
| `+0ms` | 旧课程列表 | 淡出 + 轻微上移 | `fadeOut(tween(140, accelerate))` + `slideOutVertically(tween(140, accelerate)) { -it / 24 }` |
| `+100ms` | 学期名称 Chip | 竖向翻页（旧的向上走，新的从下进） | `AnimatedContent`，`slideInVertically { it } + fadeIn`，两者 `tween(150, decelerate)` |
| `+140ms` | 列表容器 | 高度平滑变化 | `animateContentSize(spring(NoBouncy, StiffnessMediumLow))` |
| `+140ms` | 新课程列表 | stagger 入场 | 见 5.4 |

```kotlin
AnimatedContent(
    targetState = selectedSemester,
    transitionSpec = {
        (fadeIn(tween(150, easing = Ease.Decelerate)) +
            slideInVertically(tween(150, easing = Ease.Decelerate)) { it })
            .togetherWith(fadeOut(tween(140, easing = Ease.Accelerate)) +
                slideOutVertically(tween(140, easing = Ease.Accelerate)) { -it / 24 })
    },
    label = "semesterSwitch"
) { semester -> TodayCourseList(semester) }
```

**两条分支：**

- **本地已有该学期缓存**：跳过骨架屏，直接走 500ms 编排。用户感知是"课表当场换了一批"，最快路径。
- **切换触发网络刷新**：先显示 3 张骨架卡（shimmer 见 5.8），数据到达后 `Crossfade(tween(200, decelerate))` 从骨架切到真实内容，再对真实内容做 stagger 入场。总时长上限 500ms 加网络耗时，骨架期间标题与学期 Chip 保持可交互，不锁界面。

**为什么这样编排：** 让"旧内容先走、新内容后到"，而不是交叉淡入淡出。交叉淡入时两批课程卡会重叠在一起，12 门课的名字叠成一团，用户会读到一个不存在的课表。先清空再填充，语义上也是对的：学期换了，旧课表不成立了。

### 5.4 列表项入场（课程卡片逐个出现）

只在两个时机播：首次进入今日课程页、学期切换后。**不是每次滚动都播。**

```kotlin
LazyColumn {
    itemsIndexed(courses, key = { _, c -> c.courseCode }) { index, course ->
        val delay = MotionTokens.Recipe.staggerDelayFor(index)
        var played by rememberSaveable(course.courseCode) { mutableStateOf(false) }

        AnimatedVisibility(
            visible = true,
            enter = if (played) EnterTransition.None
                    else (fadeIn(tween(180, easing = Ease.Decelerate, delayMillis = delay)) +
                          slideInVertically(tween(180, easing = Ease.Decelerate, delayMillis = delay)) { it / 6 })
                        .also { played = true },
            label = "courseItemEnter"
        ) {
            CourseCard(course)
        }
    }
}
```

| 参数 | 值 |
|---|---|
| 单卡时长 | 180ms |
| 缓动 | `decelerate` |
| 位移 | `initialOffsetY = { it / 6 }`（约 19dp，不是整卡高度，避免"砸下来"） |
| **stagger 间隔** | **60ms** |
| **stagger 封顶** | `delayMillis = index.coerceAtMost(6) * 60`。第 7 项之后不再累加 |
| 播放次数 | 一次。`rememberSaveable` 标记，滚动回看不重播 |

**封顶与重叠计算（对应"同时动画元素 ≤ 3"的硬约束）：**

- 不封顶的后果：用户真实课表有 12 项（见第 7 节真实数据）。第 12 项的延迟是 `11 × 60 = 660ms`，已经超过 500ms 上限；加上 180ms 时长，最后一张卡要到 840ms 才出现，用户会觉得卡了。
- 封顶到 6 后：最大延迟 `6 × 60 = 360ms`，最后一张卡在 540ms 内完成。
- **同时动画数 = 180ms / 60ms = 3**。恰好等于上限，满足约束。
- 如果课程数超过 8 项，把 stagger 间隔降到 45ms，则同时动画数为 4，超过上限。所以**间隔只能升不能降**。若未来要支持更长的课表，改为"只对前 8 项做 stagger，其余一次性淡入"。

### 5.5 状态变化：从"即将开始"变为"正在进行中"

这是每天都会发生的情感时刻，也是本 App 最值得投入的动效。**全屏只允许 1 门课处于这个状态。**

**检测方式：** 对齐到下一分钟 tick，不用长轮询。

```kotlin
LaunchedEffect(Unit) {
    while (true) {
        val now = System.currentTimeMillis()
        delay(60_000 - now % 60_000)     // 对齐到下一分钟
        tickState.value = now
    }
}
```

**同时到点时的五重变化：**

| 变化项 | 动画 | 参数 |
|---|---|---|
| 卡片边框色 | `animateColorAsState` | `outline` → `primary`，300ms，`standard` |
| 卡片边框宽度 | `animateDpAsState` | `1dp` → `1.5dp`，300ms，`standard` |
| 卡片底色 | `animateColorAsState` | `surface` → `accentTint`（`#E7F0FE` / `#16243A`），300ms，`standard` |
| 状态 pill | `AnimatedVisibility` | `expandHorizontally(tween(200, emphasized))` + `fadeIn(tween(200, decelerate, delayMillis = 100))` |
| 状态点脉冲 | `rememberInfiniteTransition` | `animateFloat(0.6f ↔ 1.0f, infiniteRepeatable(tween(1600, standard), RepeatMode.Reverse))`，作用于外扩圆环的 `scale(1.0 → 1.35)` 与 `alpha(0.35 → 0.0)` |

```kotlin
// 卡片
val isNow = course.isInProgress
val borderColor by animateColorAsState(
    targetValue = if (isNow) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outline,
    animationSpec = MotionTokens.Recipe.stateChange(),
    label = "cardBorder"
)
val borderWidth by animateDpAsState(
    targetValue = if (isNow) 1.5.dp else 1.dp,
    animationSpec = MotionTokens.Recipe.stateChange(),
    label = "cardBorderWidth"
)
val cardBg by animateColorAsState(
    targetValue = if (isNow) LocalAccentTint.current
                  else MaterialTheme.colorScheme.surface,
    animationSpec = MotionTokens.Recipe.stateChange(),
    label = "cardBg"
)

// 状态点脉冲圆环（仅可见且 RESUMED 时运行）
val ring = rememberInfiniteTransition(label = "inProgressPulse")
val ringProgress by ring.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(MotionTokens.Rules.PulsePeriodMs, easing = MotionTokens.Ease.Standard),
        repeatMode = RepeatMode.Reverse
    ),
    label = "ringProgress"
)
```

**三条硬性限制：**

1. **单一性。** 状态提升到列表层，用 `derivedStateOf` 保证同时只有 1 门课 `isInProgress = true`。即使数据异常出现了两门时间重叠的课，也只高亮更早开始的那一门。
2. **生命周期。** 脉冲只在卡片可见且 App 处于 `RESUMED` 时运行。用 `LifecycleEventObserver` 或 `LocalLifecycleOwner.current.lifecycle.currentState` 判断。页面切到后台立即停止脉冲，避免后台无限动画耗电。
3. **不移动。** 整个状态变化**没有任何位置或尺寸变化**，只有颜色与脉冲。课程卡在列表中的位置不变，用户正在读的那一行不会跑掉。

**低强度预告（即将开始，距上课 15 分钟内）：** 时间行的图标由 `schedule` 换成 `alarm`，文字色由 `muted` 变 `fg`，`animateColorAsState(tween(200, standard))`。**不脉冲，不改边框，不改底色**，避免与正在进行中抢注意力。

### 5.6 空状态：从"有课"变"今天没课"

考虑到用户真实的课程分布（见 7.1），这个过渡在周五与周末会真实发生。

| 时间 | 目标 | 动画 |
|---|---|---|
| `+0ms` | 课程列表 | `fadeOut(tween(160, accelerate))` + 容器高度收拢 |
| `+160ms` | 空状态图标 | `scaleIn(tween(260, emphasized), initialScale = 0.88f)` + `fadeIn(tween(260, decelerate))` |
| `+160ms` | 空状态文案 | `slideInVertically(tween(260, decelerate)) { it / 8 }` + `fadeIn(tween(260, decelerate))` |

总时长 420ms。反向（没课变有课）时入场压到 150ms、内容 220ms。

**空状态内容（用真实数据，不用空洞文案）：**

```
[event_busy 图标，24dp，onSurfaceVariant]
今天没有课程
下节课：周四 08:10 大学物理B(1) · B105 · 袁艳红
[次要按钮：查看本周课表]
```

第二行的"下节课"是用真实数据算出来的（从当前时刻往后找最近的一节课，跨天也继续找）。周五下午出现的会是"下节课：周一 08:10 中国近现代史纲要 · E教116 · 李彬彬"。这条信息把空状态从"什么都没有"变成了"下一次行动是什么"，是空状态唯一有价值的内容。

**禁止：** 空状态只写"暂无数据"或"还没有课程"。这两种文案都没有给用户任何信息。

### 5.7 下拉刷新与周视图横向切换

#### 下拉刷新

用 Material 3 的 `PullToRefreshBox`，指示器：`CircularProgressIndicator`，`containerColor = surfaceContainerHigh`，`color = primary`。

| 阶段 | 处理 |
|---|---|
| 刷新中 | **不加额外动画**。M3 自带指示器已经足够，叠加动画会变成噪音 |
| 完成且有数据差异 | 差异项用 `Modifier.animateItem(placementSpec = spring(NoBouncy, StiffnessMediumLow))` 做位置迁移；新出现的课程卡用 `fadeIn(tween(180, decelerate)) + scaleIn(tween(180, decelerate), initialScale = 0.96f)` |
| 完成且无差异 | 顶部一次性提示"课表已是最新"，`fadeIn(tween(200, decelerate))`，1600ms 后 `fadeOut(tween(200, accelerate))` |
| 时长预算 | ≤ 400ms（不含网络等待） |
| 硬性要求 | 刷新期间列表仍可滚动，不阻塞交互 |

#### 周视图横向切换周次

```kotlin
val pagerState = rememberPagerState(initialPage = currentWeekIndex) { weekCount }

HorizontalPager(
    state = pagerState,
    pageSize = PageSize.Fill,
    flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1)   // 禁止一次甩过多周
    ),
    key = { it },
    modifier = Modifier.fillMaxSize()
) { weekIndex -> WeekGrid(weekIndex) }
```

| 环节 | 处理 |
|---|---|
| 拖动 | 1:1 跟手，无额外动画参数 |
| 松手吸附 | `spring(dampingRatio = 1.0, stiffness = 400)`（NoBouncy）。**不用 M3 默认的 `DampingRatioMediumBouncy`**，那会有一点回弹 |
| 飞甩限制 | `PagerSnapDistance.atMost(1)`，一次手势最多翻一周。课表是精确信息的检索，不是内容流，允许一次甩 5 周会让人失去方向 |
| 落位后内容 | 网格 `fadeIn(tween(160, decelerate))`。因为周网格的数据要重算（节次时间轴、单双周过滤），淡入掩盖重算时的白闪 |
| 网格内 stagger | **禁用**。网格单元 30 个以上，stagger 会糊成噪点 |
| 回到本周按钮 | `AnimatedVisibility`：进入 `scaleIn(tween(200, emphasized), initialScale = 0.8f) + fadeIn(tween(200))`，退出 `scaleIn(tween(120, accelerate), targetScale = 0.8f) + fadeOut(tween(120))`。**原地缩放出现，不用弹跳，不用位移** |
| 顶部"第 N 周"标签 | 随 Pager 偏移联动：`animateFloatAsState(pagerState.currentPage + pagerState.currentPageOffsetFraction)` 驱动标签的淡入淡出透明度 |

### 5.8 骨架屏与 BottomSheet

**骨架屏 shimmer（首屏与学期切换的加载占位）：**

```kotlin
val shimmer = rememberInfiniteTransition(label = "shimmer")
val progress by shimmer.animateFloat(
    initialValue = -1f, targetValue = 2f,
    animationSpec = infiniteRepeatable(
        animation = tween(1200, easing = MotionTokens.Ease.Linear),   // 唯一允许的线性缓动
        repeatMode = RepeatMode.Restart
    ),
    label = "shimmerProgress"
)
// progress 作用于 24% 宽度高光带的 translationX，Base = skeletonBase，Highlight = skeletonHighlight
```

| 参数 | 值 |
|---|---|
| 形状 | 3 张骨架卡，高度与真实课程卡一致（**112dp**），防止 CLS |
| 周期 | 1200ms，线性，`Restart` |
| 高光带宽度 | 24% |
| 停止条件 | 数据到达即销毁 `rememberInfiniteTransition` |

**BottomSheet（导入课表 / 课程详情展开）：**

| 动作 | 参数 |
|---|---|
| 进入 | `slideInVertically(tween(350, emphasized)) { it }` |
| 退出 | `slideOutVertically(tween(200, accelerate)) { it }` |
| 遮罩 | `animateFloatAsState(tween(300, standard))` 作用于 scrim 的 alpha，`scrim = 0x700C121C`（浅）/ `0x99000000`（深） |
| 圆角 | 顶部两角 `lg 16dp` |

### 5.9 动画重叠预算复核

| 场景 | 单元素时长 | 间隔 | 同时动画数 | 是否 ≤ 3 |
|---|---|---|---|---|
| 今日课程 stagger 入场 | 180ms | 60ms | 3 | 通过 |
| 学期切换旧列表退场 + 新列表入场 | 140ms / 180ms | 交错 | 退场结束后入场才开始，同时最多 3 | 通过 |
| 卡状态变进行中（颜色 + 宽度 + 底色 + pill） | 300ms | 同起点 | 4 个属性动画，但作用于**同一个元素**，算 1 个元素 | 通过 |
| 进行中脉冲 | 1600ms 周期 | 无限 | 1 | 通过 |
| 周 Pager 吸附 | spring | 1 个页面 | 1 | 通过 |
| 下拉刷新完成 | 180ms | 差异项分散 | 视差异数而定，超过 3 项时改为整块 `Crossfade(tween(180))` | 通过 |

### 5.10 性能预算

**目标：任何滚动路径下不掉帧，中端机稳定 60fps，旗舰机 120Hz 可用。**

| 措施 | 具体做法 |
|---|---|
| 只用 GPU 加速属性 | 全部走 `Modifier.graphicsLayer` 的 `translationX / Y`、`scaleX / Y`、`alpha`。这些属性跳过重组与重新布局，直接进绘制阶段 |
| 不用会触发 relayout 的属性 | 禁止动画 `width / height / top / left / margin`。需要高度变化时用 `animateContentSize()`，且只在数据变化时触发一次 |
| 列表项跳过重组 | 所有动画状态用 `remember` / `rememberSaveable` 缓存；`LazyColumn` 的 `items` 必须提供稳定 `key`（用课程代码），否则 `animateItem()` 与 stagger 会错位 |
| 减少状态读取范围 | 动画值用 lambda 版 `Modifier.offset { }` 或 `graphicsLayer { }`，把状态读取推迟到布局与绘制阶段，避免重组 |
| 不在滚动中播动画 | stagger 只在首屏静止时播一次并用 `rememberSaveable` 记录。这是本项目最重要的性能决策：滚动期间动画数恒为 0 |
| 不可见即停止 | 进行中脉冲在卡片离开可见区或 App 失焦时立即停止。骨架 shimmer 在数据到达时销毁 transition |
| 无 `will-change` 类滥用 | 不给整个列表开 `graphicsLayer` 硬件层，只给正在动画的单个元素开 |
| 真机验证 | 用 Perfetto 抓帧时间，重点看三个场景：首次进入今日课程（stagger 播完）、切换学期（500ms 编排）、周视图快速翻 10 周。若任一场景出现单帧 > 32ms，砍掉该场景的 stagger |

### 5.11 无障碍与降级（reduced motion）

**系统关闭动画时（Android 的 `ANIMATOR_DURATION_SCALE == 0`，用户可能在开发者选项或无障碍设置里关掉）：**

| 行为 | 降级处理 |
|---|---|
| 页面切换 | 直接切到终态，无 fade 无 scale |
| 学期切换编排 | 跳过 stagger 与退场，直接显示新内容 |
| 列表项入场 | 一次性全部显示，无 stagger 无位移 |
| 进行中脉冲 | **完全关闭**（持续动画是前庭敏感用户的主要不适来源） |
| 空状态过渡 | 直接显示，保留最终态 |
| 下拉刷新与周播放 | Pager 拖动保留（这是直接操作，不是自动动画），吸附改为瞬时 |
| 颜色过渡 | 保留 ≤ 100ms 的极短过渡，或直接跳变 |

判定方式：读 `Settings.Global.ANIMATOR_DURATION_SCALE`，为 0 即认为用户要求减少动画。Kotlin 侧已提供 `KebiaoAccessibility.isReducedMotion(context)`。

**低内存设备降级（`ActivityManager.isLowRamDevice() == true`）：**

- 关闭进行中状态点脉冲
- 关闭列表 stagger（改为一次性淡入）
- 所有 `spring` 替换为 `tween(200, decelerate)`
- 关闭骨架 shimmer 的持续扫光，改为静态占位块

**对比度与触控（审计阶段会专项复查，设计阶段已按此约束取值）：**

- 正文 ≥ 4.5:1，UI 组件与图标 ≥ 3:1
- 触控目标 ≥ 48dp，元素间距 ≥ 8dp
- 所有可聚焦元素有 3dp `focusRing`（浅色 `rgba(29,111,232,0.32)`，深色 `rgba(111,168,255,0.38)`）
- 课程卡整体合并为一个可聚焦节点，TalkBack 播报"中国近现代史纲要，08:10 到 09:40，E教116，李彬彬，正在进行中"五要素
- 状态不只靠颜色：进行中同时有边框色、状态 pill、脉冲圆环三重编码；本周不上同时有降到 55% 不透明度、虚线边框、文字标签三重编码

---

## 6. Design Token 概览（浅色与深色两套）

完整定义见 `docs/design-tokens.json`（原始值）与 `docs/design-tokens.kt`（Compose 直接用）。

### 6.1 层级与前景（A1-identity 与 B-slot）

| Token | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `bg` | `#F4F6FA` | `#0E1116` | 页面底。**不是纯白也不是纯黑**，带极低彩度的冷调 |
| `surface` | `#FFFFFF` | `#161A21` | 卡片底 |
| `surfaceContainer` | `#EDF0F6` | `#1C2129` | 分组容器、骨架底 |
| `surfaceContainerHigh` | `#E6EBF5` | `#242A34` | 选中 chip、次级 pill |
| `fg` | `#171A21` | `#E8EBF1` | 主文字。近黑带蓝调，不是纯黑 |
| `fg2` | `#2C313A` | `#C7CDD8` | 次级前景 |
| `muted` | `#59616E` | `#A2ABB8` | 副文本（对 surface 6.1:1 / 8.4:1） |
| `meta` | `#7C8695` | `#6E7887` | 元数据与装饰图标。**仅用于装饰，不承载关键文本**（对白底 3.8:1，低于 4.5，需 4.5 时改用 muted） |
| `border` | `#E2E6EE` | `#2A313C` | 默认边框 |
| `borderSoft` | `#EFF2F7` | `rgba(255,255,255,0.06)` | 列表内部分隔 |

### 6.2 一套完整的语义色

浅色与深色都定义了：`success / successContainer / warn / warnContainer / danger / dangerContainer / info / infoContainer / focusRing / scrim / skeletonBase / skeletonHighlight`。具体值见 JSON 的 `color.semanticLight` 与 `color.semanticDark`。

### 6.3 深色主题的层级表达

深色主题不用阴影表达层级，改用亮度递进，四级：

```
bg                     #0E1116
surface                #161A21
surfaceContainer       #1C2129
surfaceContainerHigh   #242A34
```

文字四级：`#E8EBF1` → `#C7CDD8` → `#A2ABB8` → `#6E7887`。
边框两级：`#2A313C` → `rgba(255,255,255,0.06)`。

### 6.4 Token 分层说明

| 层 | 本项目的 token | 谁决定值 |
|---|---|---|
| **A1-identity** | `bg` `surface` `fg` `muted` `accent` `border` `font` 族 | 品牌（本项目即设计决策） |
| **A1-structure** | 字号阶梯、`4dp` 间距网格、圆角阶梯、容器宽度 | 品牌 |
| **A2** | `success / warn / danger / info`、`accentHover / Active`、动效时长、`font-mono` 数字族 | 品牌（有默认值） |
| **B-slot** | `fg2` `meta` `surfaceContainer*` `borderSoft` `accentTint` | 品牌声明 |
| **C-extension** | 8 色课程识别环 `courseLight / courseDark`（含各色 tint） | 本项目专属 |

课程识别环是 C-extension：它是课表这个产品独有的扩展，不属于通用语义色。

### 6.5 前端的消费方式

```kotlin
// 1. 包主题
KebiaoTheme {
    AppNavHost()
}

// 2. 颜色只能从 MaterialTheme 取，或者对 8 色课程环用 CoursePalette
val accent = MaterialTheme.colorScheme.primary
val courseColor = CoursePalette.forCode("053017P1-15", dark = isSystemInDarkTheme())
Text("中国近现代史纲要", color = MaterialTheme.colorScheme.onSurface)
Box(Modifier.size(8.dp).background(courseColor.value, CircleShape))

// 3. 动效只能用 MotionTokens
AnimatedContent(targetState = tab, transitionSpec = {
    MotionTokens.Recipe.fadeThroughEnter() togetherWith MotionTokens.Recipe.fadeThroughExit()
})

// 4. 间距只能用 KebiaoSpacing
Spacer(Modifier.height(KebiaoSpacing.CardGap))

// 5. 图标只能用 KebiaoIcons
KebiaoIcon(KebiaoIcons.LocationOn, "上课地点", size = IconSize.Inline)
```

**前端禁止事项：** 不写裸 hex（唯一例外 `#fff` `#000`）；不写自己的 `tween(300)` 或 `CubicBezierEasing`；不写 `8.dp` 这种散落间距（从 `KebiaoSpacing` 取）；不引入第二套图标库；不写固定高度容器（会破坏 Font Scale 200% 的适配）。

---

## 7. 页面清单与布局说明

### 7.1 真实数据基线（设计必须承载的内容）

来自教务系统抓取（`output/kebiao.html`，抓取时间 2026-09-20 17:21）。**所有设计稿必须用这些真实课程，不得使用"示例课程"之类占位文案。**

| 课程名 | 学时 | 时间与地点 | 教师 |
|---|---|---|---|
| 中国近现代史纲要 | 48 | 周一 1-2 节 双周 E教116；周二 3-4 节 3-16周 B407 | 李彬彬 |
| 大学物理B(1) | 64 | 周四 1-2 节 3-16周 B105；周一 3-4 节 3-16周 B103 | 袁艳红 |
| 高等数学B(1) | 48 | 周一 7-8 节 3-16周 B102；周三 5-6 节 **3-15 单周** E教114 | 周钢 |
| 大学物理实验B(1) | 16 | 周二 1-2 节，**9-16 周每周换实验室**（204 / 202 / 309 / 305 / 308） | 陈芳芳 |
| 新中国史 | 16 | 周五 1-2 节 2-8 周 A教116 | 尹朝春 |
| 乒乓球(初级) | 32 | 周三 3-4 节 2-16 周 体育馆乒乓球馆 | 李婷 |
| 大学英语听说(1) | 32 | 周一 5-6 节 2-16 周 D教408 第六语音室 | 徐鸿雁 |
| 大学英语(1) | 32 | 周二 5-6 节 2-16 周 C教407 | 徐鸿雁 |
| 工程制图与CAD B | 32 | 周四 5-6 节 2-16 周 A教301 | 刘昭 |
| 自动化专业导论与职业生涯规划 | 16 | 周二 7-8 节，**2/3-4/5/6-7/8 周**不同教师（陈国初 / 蒋璐峥 / 于妍） | 多位 |
| 大学生心理与保健 | 32 | 周三 7-8 节，2-8 周 D教208，**9-16 周 线上教学1** | 王海明 |
| 国家安全教育 | 16 | 周一 10-11 节 9-16 周 D教103 | 潘帅豪 |
| 军事理论 | 36 | 周二 10-11 节 2周 C教114，3-8周 B103 | 陈琦 |
| 形势与政策(1) | 4 | 周二 10-11 节 15-16 周 B207 | 赵冰 |
| 航空与航天（尔雅通识） | 备注 | 5-16 周 **纯线上**，无实体教室 | 王圆圆 |
| 军事技能 | 备注 | **无固定时间地点**，以学院通知为准 | 房晶 |

**必须被设计承载的六类特殊数据：**

| 特殊数据 | 设计处理 |
|---|---|
| 大一满课节奏（周一至周三满课，周四下午起空，周末无课） | 今日课程页的高度随课程数变化（`animateContentSize`）；周视图用色块的疏密直接呈现这个节奏；空状态在周五下午与周末真实触发 |
| **单双周课**（高等数学B(1) 周三 3-15 单周） | 非当前周的课不隐藏，而是以 55% 不透明度 + 虚线边框 + "本周不上" pill 呈现。用户需要知道"这门课存在但本周不来" |
| **同一门课不同周次换教室**（大学物理实验B(1) 6 个实验室轮换） | 课程卡的地点行显示本周实际地点，并给一个"查看全部地点"的展开入口（`expand_more`）。展开时用 `animateContentSize(spring(Gentle))` |
| 同一门课不同周次换教师（自动化专业导论 4 位教师） | 教师行显示本周实际教师，周次范围用小字 caption 标注 |
| **纯线上课**（尔雅通识、心理与保健 9-16 周线上） | 地点行显示 `wifi` 图标 + "线上教学1"，用 `info` 语义色 pill。不用地点图标 |
| **无固定时间地点**（军事技能） | 单独归入"待定"分组，用 `help` 图标 + "以学院通知为准"。不塞进时间轴，也不显示假的时间 |

### 7.2 页面一：今日课程（主界面，核心）

**路由：** `/today`（底部导航第 1 项）

**首屏第一眼必须是今天真实的课程数据，不是标题也不是宣传语。**

```
┌──────────────────────────────────────────────┐
│  今天 · 第 1 教学周            [2026-2027-1 ▾]│  ← TopAppBar：左标题，右学期 Chip
│  9月20日 周日                        [更新]   │
├──────────────────────────────────────────────┤
│  今天没有课程                                 │  ← 空状态时的真实结果
│  下节课：周一 08:10 中国近现代史纲要           │
│          E教116 · 李彬彬                      │
│  [查看本周课表]                               │
├──────────────────────────────────────────────┤
│  ── 有课时（以周二为例）──────────────────     │
│                                              │
│  ● 中国近现代史纲要           [本周不上]      │  ← 双周课，本周不上：虚线框 + 55% 不透明
│    08:10-09:40 · 1-2 节                      │
│    [location_on] A教104 · 李彬彬              │
│                                              │
│  ● 大学物理实验B(1)                          │  ← 同一门课换教室
│    08:10-09:40 · 1-2 节                      │
│    [location_on] 物理教学中心 · 陈芳芳  [详情▾]│
│                                              │
│  ● 大学英语(1)              [已结束]          │  ← 已结束：中性 pill + 文字降到 muted
│    12:30-14:00 · 5-6 节                      │
│    [location_on] C教407 · 徐鸿雁              │
│                                              │
│  ● 自动化专业导论与职业生涯规划   [进行中]     │  ← 进行中：accent 边框 + tint 底 + 脉冲点
│    14:20-15:50 · 7-8 节                      │
│    [location_on] B203 · 蒋璐峥                │
│                                              │
│  ● 军事理论               [18 分钟后]         │
│    17:30-19:05 · 10-11 节                    │
│    [location_on] B103 · 陈琦                  │
│                                              │
│  ── 待定 ────────────────────────────        │  ← 无固定时间地点的课单独分组
│  [help] 军事技能  以学院通知为准 · 房晶        │
│  [wifi] 航空与航天（尔雅通识） 5-16周 线上 · 王圆圆│
│                                              │
├──────────────────────────────────────────────┤
│  [今天]      [周视图]        [设置]           │  ← NavigationBar，FILL 轴切选中态
└──────────────────────────────────────────────┘
```

**布局：** `Scaffold`（TopAppBar + NavigationBar）内 `LazyColumn`，`ScreenGutter = 20dp` 左右内边距，卡片间距 `12dp`。

**课程卡结构（112dp）：**

| 区域 | 内容 | 样式 |
|---|---|---|
| 左 | 8dp 课程色圆点（`CoursePalette.forCode`） | 圆点垂直对齐课程名首行 |
| 主体上行 | 课程名 | `titleMd` 18/24 w600，色 `onSurface` |
| 主体中行 | `schedule` 图标 + `08:10-09:40 · 1-2 节` | `label` 13/18 w500，数字走 numeric 字体栈 |
| 主体下行 | `location_on` 图标 + 地点 + `·` + 教师 | `bodySm` 14/20 w400，色 `muted` |
| 右 | 状态 pill（进行中 / 已结束 / 18 分钟后 / 本周不上 / 线上） | `titleSmall` 16/22 w500，pill 圆角 |

**卡片状态与视觉：**

| 状态 | 边框 | 底色 | 文字 | pill |
|---|---|---|---|---|
| default | 1dp `border` | `surface` | `onSurface` / `muted` | 无（或"线上"） |
| inProgress | **1.5dp `accent`** | **`accentTint`** | 不变 | `accent` 实底白字"进行中" + 脉冲点 |
| finished | 1dp `borderSoft` | `surface` | 降到 55% 不透明度 | 中性底 `muted` 字"已结束" |
| upcoming（15 分钟内） | 1dp `border` | `surface` | 时间行图标换 `alarm`，色 `onSurface` | 中性底"18 分钟后" |
| notThisWeek | **1dp 虚线 `borderSoft`** | `surface` | 降到 55% 不透明度 | 中性底 `muted` 字"本周不上" |
| loading | 骨架块 | `skeletonBase` | 无 | 无 |
| error | 1dp `danger` | `dangerContainer` | `danger` | "解析失败" |
| empty（整页） | 无 | 无 | 见 5.6 的空状态 | 无 |

**交互：**
- 点卡片 → push 到课程详情（shared-axis X，350ms）
- 点状态 pill 右侧的 `expand_more` → 卡内展开"全部时间地点"（`animateContentSize(spring(Gentle))`）
- 点右上角 `refresh` → 重新抓取教务系统
- 点学期 Chip → push 到学期管理页
- 下拉 → 刷新课表

**响应式：** `>= 600dp` 宽度（平板、横屏）改为双列网格，卡片宽度自适应。

### 7.3 页面二：周视图（完整课表）

**路由：** `/week`（底部导航第 2 项）

**手机竖屏的关键决策：不横向硬塞 7 列。** 360dp 宽度减去 64dp 时间轴和边距，剩约 276dp 分 7 列，每列 39dp，课程名只能显示 2 个字。竞品普遍踩这个坑。改为**逐日列 + 横向 Pager 按周切换**：一屏显示"周一至周三"三列（每列约 92dp），左右可再滑到"周四至周日"。

```
┌──────────────────────────────────────────────┐
│  [chevron_left]  第 1 教学周  [chevron_right] │  ← 周切换，pill 形周次器
│  2026-09-14 至 2026-09-20         [today]     │  ← 回到本周按钮，仅在非本周时出现
├──────────────────────────────────────────────┤
│                                              │
│      周一      周二      周三                 │  ← 当天列表头用 accent 底纹
│ ────────────────────────────────────────     │
│ 1-2  │ 中国近现 │ 大学物理 │          │       │  ← 时间轴 64dp 固定，节次 + 起止时间
│ 08:10│ 代史纲要 │ 实验B(1) │   无课   │       │
│ 09:40│ E教116  │ 实验室   │          │       │
│ ────────────────────────────────────────     │  ← 1dp borderSoft 分隔，不用卡片盒子
│ 3-4  │ 大学物理 │ 中国近现 │ 乒乓球   │       │
│ 10:00│ B(1)     │ 代史纲要 │ (初级)   │       │
│ 11:30│ B103     │ B407     │ 乒乓球馆 │       │
│ ────────────────────────────────────────     │
│ 5-6  │ 大学英语 │ 大学英语 │ 高等数学 │       │
│ 12:30│ 听说(1)  │ (1)      │ B(1) 单周│       │  ← 单周课标"单周"
│ 14:00│ D教408   │ C教407   │ E教114   │       │
│ ────────────────────────────────────────     │
│ 7-8  │ 高等数学 │ 自动化导 │ 大学生心 │       │
│ 14:20│ B(1)     │ 论      │ 理与保健 │       │
│ 15:50│ B102     │ B203     │ D教208   │       │
│ ────────────────────────────────────────     │
│      │          │  ◀ 左右滑动查看周四至周日 ▶ │
└──────────────────────────────────────────────┘
```

**布局：** 左侧 64dp 固定时间轴（不在 Pager 内，不随滑动移动），右侧 `HorizontalPager` 承载 7 天可滑动的网格。节次行用 `divide-y` 思路的 1dp `borderSoft` 分隔，**不用卡片**（Density 5 的约束：周视图是仪表盘模式）。

**网格单元（无卡片，只有底色和文字）：**

| 状态 | 处理 |
|---|---|
| 有课 | 课程色 tint 底（约 10% 面积）+ 课程名（`label` 13/18 w500，`onSurface`）+ 地点（`caption` 12/16，`muted`）。左上角 4dp 圆形色点 |
| 无课 | 透明，不显示"无课"字样（除周一 9-10 节这个特定空档，按数据决定） |
| 本周不上 | 虚线边框 + 55% 不透明度 + "本周不上" 小字 |
| 时间冲突（同一时段两门课） | 上下分栏显示两门课，各占一半高度；右上角显示 `warning` 图标（不用小三角，那个看不见） |
| 当天 | 列头底色用 `accentContainer` |
| 当前时刻 | 一条 2dp `accent` 横线穿过当前时间位置，左端一个小圆点。仅在显示当前周时出现 |

**交互：**
- 左右滑 → Pager 切换整周（吸附 `spring(NoBouncy, 400)`，`PagerSnapDistance.atMost(1)`）
- 点网格单元 → push 到课程详情
- 点周次器左右箭头 → `animateScrollToPage`，`tween(300, emphasized)`
- `today` 按钮 → 跳回当前周，按钮自身用 `scaleIn/scaleOut`
- 长按网格 → 进入编辑模式（MVP 可延后）

**周视图明确不做的事：** 不做 stagger 入场（30 多个格子会糊），不做卡片阴影，不做整格高饱和大色块。

### 7.4 页面三：多学期管理

**路由：** `/semesters`（从今日课程页的学期 Chip 推入）

**功能：** 列出所有学期、切换当前学期、删除学期、新建学期（从教务系统导入）。

```
┌──────────────────────────────────────────────┐
│  [arrow_back]  学期管理                       │
├──────────────────────────────────────────────┤
│  [add] 从教务系统导入新学期                    │  ← 主操作，accent 文字按钮
├──────────────────────────────────────────────┤
│  ● 2026-2027 学年第 1 学期         [check]    │  ← 当前学期，accent 圆点 + check 图标
│    2026-09-14 起 · 16 教学周 · 16 门课         │
│ ────────────────────────────────────────     │
│  ○ 2025-2026 学年第 2 学期                     │  ← 非当前：空心圆点
│    2026-02-23 起 · 16 教学周 · 14 门课         │
│ ────────────────────────────────────────     │
│  ○ 2025-2026 学年第 1 学期                     │
│    2025-09-15 起 · 16 教学周 · 15 门课         │
├──────────────────────────────────────────────┤
│  [history] 学期数据来源：教务系统               │
│  最近同步 2026-09-20 17:21                     │
└──────────────────────────────────────────────┘
```

**布局：** 列表用 `divide-y` 思路的 1dp `border`（这张列表是"选择器"，不需要卡片盒子）。当前学期行不加边框、只加 8dp 的 `accent` 圆点与尾随 `check` 图标。

**交互与动画：** 点某一学期 → 立即返回今日课程页（`popEnter/popExit`，350ms），返回后今日课程页的列表做 5.3 的 500ms 刷新编排。**学期切换的过渡是这个页面存在的理由**，所以这里的动画是全 App 最用心的。

**空状态（首次使用，还没有任何学期）：** `inbox` 图标 + "还没有导入课表" + "用学号登录教务系统，把你的课表拉过来" + 主按钮"从教务系统导入"。这不是空洞占位，它告诉用户下一步具体做什么。

### 7.5 页面四：设置

**路由：** `/settings`（底部导航第 3 项）

**分组（用 1dp `border` 分组，不用卡片）：**

| 分组 | 项目 |
|---|---|
| 课表显示 | 显示非本周课程（开关）；显示节次时间（开关，对应 WakeUp 的"只显示节数"选项）；每周起始日（周一 / 周日） |
| 提醒 | 上课提醒（开关）；提前量（5 / 10 / 15 / 30 分钟，单选）；免打扰时段 |
| 桌面小组件 | 添加小组件（跳系统选择器）；小组件显示范围（今天 / 本周） |
| 数据 | 从教务系统导入；导出课表；清除本地缓存 |
| 关于 | 版本号；数据来源说明；教务系统登录状态 |

**布局：** `divide-y` 分组 + 组内 48dp 行高。开关组项用 M3 的 `Switch`。

**深色模式开关：** **不提供**。跟随系统（Material 3 规范与 Android 平台惯例）。用户在系统层面控制，App 不重复发明。

**交互：** 组项点击 push 到子页（350ms shared-axis）；开关点击用 M3 原生 ripple（100ms）加 `spring(Snappy)` 的滑块位移。无需自定义动画。

### 7.6 桌面小组件（Widget）

**尺寸：** 4x2（宽扁，显示今天剩下的课）与 4x4（方形，显示整周网格）两种。

**4x2 布局：**

```
┌────────────────────────────────┐
│ TODAY · 第 1 教学周             │  ← overline 11sp w600 tracking 0.08em
│ 大学物理实验B(1)     進行中      │  ← 课程名 titleMd（Widget 限于尺寸用 16sp）
│ 08:10-09:40 · 物理教学中心       │  ← label 13sp，数字走 numeric
│ ─────────────────────────────  │
│ 大学英语(1)          12:30       │
│ 自动化专业导论       14:20       │
└────────────────────────────────┘
```

**Widget 的约束：**

- 尺寸用 `RemoteViews` 的行高与字号，不用 Compose（除非用 Glance，交架构师定）
- 背景用 `surface`（浅色）/ `surface`（深色），跟随系统深浅
- 课程色只出现在每行左侧 4dp 圆形色点，不铺底
- **Widget 内不做动画**（RemoteViews 的动画能力有限且耗电，且桌面上的持续动画会被用户关掉）
- 空状态：`event_busy` 图标 + "今天没有课程"，尺寸缩到最小布局
- 点击整块 Widget → 打开 App 的今日课程页；点击某一行 → 深链到该课程详情

**图标：** Widget 内可以用 `widgets` / `schedule` / `location_on` 几个 Material Symbols 图标的简化版（Widget 内 16dp）。同样禁止 emoji。

### 7.7 上课提醒通知

**通知形态：** 标准 Android 通知，不走自定义布局（保证在各厂商系统的通知样式一致）。

```
[icon] 课表                        15 分钟后
       大学物理实验B(1)
       08:10-09:40 · 物理教学中心 · 陈芳芳
       [查看课表]  [稍后提醒]
```

| 项 | 规范 |
|---|---|
| 通知渠道 | "上课提醒"，渠道重要性 `HIGH`（默认有提示音） |
| 提前量 | 用户设置（默认 15 分钟） |
| 图标 | 小图标用 `alarm` 的单色版（Android 通知小图标必须是单色白描，系统会自动 tint） |
| 免打扰 | 尊重系统免打扰；App 内也提供免打扰时段设置 |
| 深链 | 点击通知 → 打开今日课程页并高亮该课程（高亮用一次 300ms 的 `accentTint` 底色过渡，不闪烁） |
| 无动画 | 通知本身不做自定义动画 |

---

## 8. 页面与状态覆盖清单

| 页面 | Loading | Empty | Error | Populated | Edge |
|---|---|---|---|---|---|
| 今日课程 | 3 张骨架卡 + shimmer | `event_busy` + 下节课提示 + 查看本周按钮 | 教务系统登录失效 / 网络失败，含"重试"与"用上次数据" | 课程卡列表（含 8 种卡状态） | 单日 6 门课（周一）；课后晚 19:05；跨天（凌晨 0 点后显示"今天"与"明天"的区分） |
| 周视图 | 网格骨架 | 整学期无课表 → `inbox` + 导入引导 | 周数据解析失败 | 7 天网格 | 同格两门课（冲突）；单双周叠加换教室（大学物理实验B(1)）|
| 学期管理 | 列表骨架 | `inbox` + 从教务系统导入引导 | 导入失败 + 失败原因 | 学期列表 | 只有一个学期（当前学期也不显示可切换）；20 个历史学期（虚拟滚动） |
| 设置 | 无（本地读取） | 无 | 无 | 分组列表 | 教务系统未登录（导入项禁用 + 说明） |
| 课程详情 | 无 | 无 | 无 | 时间地点教师学时列表 | 同一门课 6 个不同实验室（大学物理实验B(1)）；4 位教师轮换（自动化专业导论）；无固定时间地点（军事技能） |
| Widget | 无 | 今天没课 | 数据未同步 | 今日课程或整周网格 | 课名超长（自动化专业导论与职业生涯规划，13 字）；无课且已过最后一节 |

---

## 9. 行业知识库选择理由

知识库 `references/industries/` 下只有 5 个文件：`ai-native`、`content-platform`、`ecommerce`、`enterprise`、`saas-b2b`。**没有教育或校园类文件。**

**选定参考：`references/industries/content-platform.md`**

选择理由（这是本次判断的完整依据，不是随手挑的）：

| 维度 | 分析 |
|---|---|
| 本产品的界面骨架是什么 | 今日课程本质是一个**按时间排序的信息流**，每条是一个卡片（课程），带封面式主标题、摘要式元信息（时间地点教师）、状态标签。这与 content-platform 规范里的"内容卡片：封面图 + 标题 + 摘要 + 标签 + 作者/时间"结构同构，只是把封面图换成课程色点，把"作者/时间"换成"教师/时间" |
| 本产品的核心交互是什么 | **列表 + 下拉刷新 + 骨架屏 + 渐显**。这正是 content-platform 的"Feed 流：卡片瀑布流 + 无限滚动 + 下拉刷新"与"内容加载：骨架屏 + 渐显动画"两条规范。周视图是"分组列表"（按天分组），也是同一族 |
| 哪条规范直接救了我们 | "**不要千篇一律的内容卡片（区分不同内容类型的视觉表达）**"。这直接推导出我们的 8 种课程卡状态与"待定"分组，而不是 12 张一模一样只有文字不同的卡。"**不要忽视空状态设计**"直接推导出 5.6 的下节课提示与 7.4 的导入引导 |
| 为什么不是 enterprise | enterprise 规范的核心是权限分级、审批流转、数据表格、组织架构树。本产品是单人自用，无权限、无审批、无表格、无组织树。它的"信息密度高、操作路径短"这条我们采纳了（周视图的仪表盘密度、操作三步内到达），但主体规范不适用 |
| 为什么不是 saas-b2b | saas-b2b 的核心是注册转化、定价页、信任背书、团队协作。本产品无注册、无付费、无团队。不适用 |
| 为什么不是 ai-native | 本产品 MVP 不含任何 AI 生成或对话能力。不适用 |
| 为什么不是 ecommerce | 无商品、无购物车、无结账流程。只是"卡片列表"这个骨架形态有共性，但 ecommerce 的价格、库存、紧迫感全部无关 |

**从 content-platform 采纳的具体条款：**

- 内容卡片结构（标题 + 摘要 + 标签）→ 课程卡结构（课程名 + 时间地点教师 + 状态 pill）
- 内容加载用骨架屏 + 渐显 → 5.8 的骨架屏与 5.4 的 stagger 入场
- 阅读舒适（留白、层次）→ 今日课程页的卡片间距与行高
- 空状态必须设计 → 5.6 与 7.4
- 不用 emoji 做功能图标 → 第 4 节的图标库锁定
- **不采纳**："评论实时更新 + 折叠子评论 + @提及"（无社交）、"编辑器沉浸式写作"（无内容创作）、"SEO Agent"（无 Web 端）

**从 ui-styles-library 补充：** 第 29 号 `Flat Design Mobile（触屏扁平）` 与第 30 号 `Material You (MD3) Mobile`。取 29 号的"粗字重差异建立层级"，取 30 号的"tonal 分层 + 状态化 + ripple"。剥掉 30 号的动态取色（会破坏 8 色课程环的可分辨距离）。

**从 color-palettes 补充：** Restrained 策略与四层配比。明确不采用"教育靛橙"第 8 套（`#4F46E5` 靛色 + `#EA580C` 橙），因为 `#4F46E5` 与红线点名的 Tailwind 默认靛色同族，且橙色 CTA 在课程表里没有转化任务可拉。

---

## 10. 交付物索引与后续动作

### 10.1 本次交付

| 文件 | 内容 |
|---|---|
| `docs/UIUX.md`（本文件） | 设计方向、对标品牌、竞品调研、配色与字体决策、图标库锁定与清单、完整动画规范、页面清单与布局、状态覆盖、行业知识库选择理由 |
| `docs/design-tokens.json` | 四层 Token 原始定义：颜色（浅深两套 + 8 色课程环）、字体、间距、圆角、层级、动效（含 11 个场景配方）、图标、组件、无障碍 |
| `docs/design-tokens.kt` | Compose 可直接 import 的实现：`KebiaoColorScheme`、`CoursePalette`、`KebiaoTypography`、`AppType`、`KebiaoShapes`、`KebiaoSpacing`、`KebiaoElevation`、`MotionTokens`（含全部 Recipe）、`KebiaoIcons` 与 `KebiaoIcon`、`IconSize`、`KebiaoAccessibility`、`KebiaoTheme` |

### 10.2 前端落地时的三步

1. **接入主题。** 把 `design-tokens.kt` 放进 `ui/theme/` 包，包一层 `KebiaoTheme { }`。
2. **导入图标。** 按 4.5 的清单从 Material Symbols 取 SVG，用 Vector Asset 导入为 `res/drawable/ic_*.xml`（约 49 个，选中态加 `_fill` 后缀）。可选导入 `res/font/inter_variable.ttf`（拉丁子集约 30KB），未导入时自动降级到等宽字体。
3. **照 Recipe 写动画。** 所有转场调用 `MotionTokens.Recipe.*`，不要自己写 `tween(300)`。不确定某个场景用哪条时，先在本文件第 5 节找场景，找不到再补进 Recipe。

### 10.3 与架构文档的对齐（已核对 `docs/ARCHITECTURE.md`）

核对结论：**架构侧与设计侧的三项关键锁定完全一致，无需返工。**

| 项 | 设计侧（本文件） | 架构侧（ARCHITECTURE.md） | 状态 |
|---|---|---|---|
| 图标库 | Material Symbols（Apache 2.0），从 Google Fonts 取 VectorDrawable XML 放 `res/drawable/`，`painterResource` 引用 | 第 10.4 节：锁定 Material Symbols，同一下载与引用方式，并说明 `androidx.compose.material.icons` 已从 Material3 最新 release 移除，使用它会直接编译失败 | 一致 |
| Widget 技术方案 | 第 7.6 节：Widget 内不做动画，课程色只在每行左侧 4dp 圆点 | 第 9.1 节：Glance 1.2.0（非 RemoteViews），并明确 Glance 走独立 RemoteViews 渲染路径，不支持任意 Compose 组件与复杂动画 | 一致 |
| 图标统一引用点 | 第 4.3 节：`KebiaoIcons` 对象集中声明全部 `@DrawableRes`，配合 `KebiaoIcon` 统一渲染 | 第 10 节目录结构：`ui/icons/KebiaoIcons.kt` 作为统一引用点 | 一致 |

**需要架构侧注意的两点差异：**

1. **图标数量。** 架构文档估计约 10 到 15 个图标；本设计清单是 49 项（含选中态 `_fill` 变体）。差异原因是设计覆盖了 4 个页面加 Widget 加通知的全部状态（含 7 种课程状态、5 种空状态与错误态、13 个操作图标）。建议按两档落地：**MVP 必做 18 个**（`calendar_today` / `calendar_view_week` / `settings` 及其 fill 变体 6 个、`schedule` / `location_on` / `person` / `meeting_room` / `refresh` / `swap_vert` / `add` / `notifications_active` / `alarm` / `radio_button_checked` / `event_busy` / `wifi` / `help` / `error` / `inbox` / `warning`，其中 `chevron_left` 与 `chevron_right` 复用同一个 drawable），其余 31 个按页面实现进度逐步补齐。清单是完整目标，不是一次交付量。
2. **Glance 需要一套精简 token。** Glance 不能用 `MaterialTheme`（走的是独立渲染路径），颜色需要显式传 `ColorProvider` 或 `Color` 值。所以 Widget 需要一份精简的颜色与字号常量表。它应当从 `design-tokens.json` 派生，不另立真相源。

### 10.4 Widget 专用精简 token（Glance 1.2.0）

只包含 Widget 真正会用的项。值与 `design-tokens.json` 完全一致，**不新增颜色**。

| 用途 | 浅色 | 深色 |
|---|---|---|
| Widget 容器底 | `#FFFFFF` | `#161A21` |
| 课程名文字 | `#171A21` | `#E8EBF1` |
| 时间与地点文字 | `#59616E` | `#A2ABB8` |
| 行分隔线 | `#EFF2F7` | `rgba(255,255,255,0.06)` |
| 进行中状态标记（圆点与文字） | `#1D6FE8` | `#6FA8FF` |
| 已结束文字（降透明度） | `#7C8695` | `#6E7887` |
| 空状态图标与文字 | `#59616E` | `#A2ABB8` |
| 课程色圆点 | 8 色课程环取 `CoursePalette.forCode(code)` 的 `value` | 同左，取深色表 |

Widget 字号（Glance 用 `TextStyle` 的 `fontSize` 与 `fontWeight`）：课程名 16sp w600、时间 13sp w500（等宽数字）、地点 13sp w400、标题 overline 11sp w600 字距 0.08em 全大写。

**Glance 实现约束（与主界面动画预算不同）：** Widget 内不做任何动画（Glance 不支持，且桌面上的持续动画会被用户关闭）。进行中状态的表达退化为"圆点用 accent 色 + 文字前加'进行中'"的静态标记，不脉冲。

### 10.5 设计侧遗留项（Phase 2 可展开）

- 课程详情页的完整布局（含 6 个实验室轮换的展开态）
- 首次使用引导（登录教务系统 → 导入 → 落地的三步流程）
- 深色主题下 8 色课程环在低亮度屏幕上的实测校验（真机验证）
- 横屏与折叠屏的布局变体
