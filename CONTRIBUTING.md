# 贡献指南

课刻是一个自用性质的个人项目，代码量不大，但纪律写得很硬。下面几条是"违反即退回"的，改代码前先看一遍。

---

## 提 issue

按顺序写清楚，能省掉一轮来回：

1. **现象**：看到了什么
2. **预期**：应该看到什么
3. **复现步骤**：从第几步开始不对
4. **环境**：手机型号、Android 版本、App 版本（`versionName`）、是否刚换过学期
5. **导入相关问题额外附**：导入页的诊断文本（App 里有复制入口）

**附诊断文本前请先自己检查一遍**，把学号、姓名、Cookie、会话信息抹掉再贴。诊断文本里可能带教务系统返回的个人信息。

如果是"教务系统改版导致导入失败"，附一份页面结构转储（`ImportStructureDump.kt` 的产出）比描述现象有用得多。

---

## 跑起来

前置：JDK 17、Android SDK（platform android-37、build-tools 36.0.0）。

```bash
# 1) 建 local.properties，指向本机 SDK（这个文件不进仓库）
echo "sdk.dir=/absolute/path/to/Android/Sdk" > local.properties

# 2) 先确认 wrapper 能用
./gradlew --version

# 3) 出 debug APK
./gradlew :app:assembleDebug

# 4) 跑纯 JVM 单测
./gradlew :app:testDebugUnitTest
```

环境问题与代码问题分开排查，踩坑清单见 `docs/BUILD-ENV.md`。

---

## 代码纪律

**单文件不超过 300 行。** 超了就拆。这条不是为了好看：导入链路里所有脚本文件都是因为贴着这条线才拆开的（`docs/ARCHITECTURE.md` 第 11 节）。

**禁止用 emoji 当功能图标。** 图标一律走 Material Symbols 的 VectorDrawable XML，放在 `res/drawable/`，在 Compose 里用 `painterResource()` 引用，并且只经 `ui/icons/KebiaoIcons.kt` 这一个出口。不引入第二套图标库。

**禁止硬编码颜色。** 唯一色值来源是 `ui/theme/Color.kt`，组件与页面一律通过 `MaterialTheme.colorScheme`、`LocalCoursePalette`、`LocalAccentTint`、`LocalSemanticColors` 取色。只有 Token 定义文件本身允许出现字面色值。

**动效只能走 `MotionTokens`。** 不要自己写时长和曲线（`ui/theme/Motion.kt` 头部注释是契约）：

- 时长阶梯只有 100 / 150 / 250 / 350 / 500 ms
- 只允许 4 条三次贝塞尔缓动，全部不含过冲
- 弹簧 `dampingRatio` 恒为 1.0，禁止回弹
- 只动画 `alpha` / `translationX,Y` / `scaleX,Y` / `rotationZ`，禁止动画尺寸与边距
- 同时动画元素不超过 3 个，滚动期间动画数为 0

**依赖方向只向下。** `feature` -> `domain` -> `data`。`domain` 不得 import 任何 `android.*` / `androidx.*`；`feature` 不得直接碰 Room 的 Dao 或 Entity；Room Entity 到领域模型的映射在 `data/repository` 内完成。

**改导入脚本时，成功载荷的 schema 不得改。** 它是抽取脚本与 `data/import/ScheduleJsonCodec.kt` 之间的契约。教务改版时改的是脚本常量，不是 schema。

---

## 提交前

```bash
./gradlew :app:testDebugUnitTest
```

- 新增的纯逻辑（排课求值、周次解析、格式化）放在 `domain` 或 `data/import/parser`，并补 JVM 单测，这两个目录是纯 Kotlin 的，测起来不需要设备
- 不要提交 `local.properties`、构建产物、以及任何含真实课表数据的抓取样本
- 注释写"为什么"，不写"做了什么"。这个仓库里最有价值的注释都是踩坑记录
