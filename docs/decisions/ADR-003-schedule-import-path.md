# ADR-003: 教务系统导入采用"App 内 WebView 中继登录 + JS 抽取"为主、JSON 文件导入为兜底

## Status
Accepted (2026-09-20)

## Background

这是本项目**最大的技术风险**（PRD 6.2 节导入功能 Confidence 标 100%，但该 100% 建立在"用户已实测可登录抓取"之上；对实现路径本身仍需选型）。

**目标系统实测事实：**

| 事实 | 证据 |
|------|------|
| 统一身份认证 `https://authserver.sdju.edu.cn/authserver/login` 可连通（HTTP 200） | 实测 curl |
| `https://jwgl.sdju.edu.cn/home` 未登录返回 HTTP 302（跳 SSO） | 实测 curl |
| 路径形态（`/authserver/login?service=...`）与 Apereo CAS 协议一致（`service` 参数 / TGC-TGT-ST / 票据重定向） | 学校官网 + CAS 协议规范 |
| 账号 = 学号；**首次登录需绑定手机号**；登录涉及**短信验证码**；提示"请勿连续点击发送验证码" | 2026 级新生信（`info.sdju.edu.cn/2026/0810/c6623a154020/page.htm`） |
| 课表位于"我的课表" **iframe** 内 | 用户实测 |
| 抓课表是**一次性低频**操作（一学期一次），非高频功能 | 任务约束 |

**核心洞察：本路径里真正的不确定性和复杂度都在"登录"，而不在"解析"。** 手机绑定、短信验证码、可能的图形验证码、可能的登录页前端密码加密（盐值 + AES）、TLS 信任链、Cookie/会话维持，全都属于"登录"；而"解析"的对象（一张课表 HTML 表格）是确定的。

候选路径：

- **路径 a：App 内直连抓取** —— App 自己 POST 登录表单、自管 Cookie、自行处理验证码。
- **路径 b：电脑脚本抓取 → 导出 JSON/CSV → App 导入文件**。
- **路径 c：两者都要（App 内为主 + 文件兜底）**。

## Decision

**选定路径 c，并明确主路径的实现方式为「WebView 中继登录 + 同源 JS 抽取」，即把"登录"交给真人 + 系统 WebView，把"取数"交给注入脚本，把"解析"交给 jsoup。**

三条理由（按权重）：

1. **把最大的不确定性移出代码。** 手机绑定 + 短信验证码意味着任何"App 内静默 POST 登录"的方案都会在登录环节撞墙；而"撞墙"的解决方式通常是申请 SMS 读取权限——这既触碰 PRD 11 节的"不采集隐私"红线，也正是竞品被吐槽的同类问题。WebView 方案让**真人完成登录**，App 不接触密码、不接触验证码。
2. **避免"App 复刻浏览器会话"这一最易错的部分。** 课表在同一个 WebView 上下文内，**同源可直读**：登录成功后在同一 WebView 中导航到课表页，用 `evaluateJavascript` 注入脚本读 DOM 并回传 JSON。这样连 Cookie 都不需要手工搬运，也不需要自己实现 HTTP 会话语义（`CASTGC` / `JSESSIONID` / `route` 粘滞 cookie）。
3. **兜底路径能立刻把"项目级风险"降级。** 若 WebView 被 UA 反制、或 iframe 跨域阻断、或用户就是不愿在手机登录，则退化为"电脑脚本 → JSON → App 导入"。**兜底不是第二套代码**：脚本产出的 JSON 与 WebView 产出的 JSON 汇入同一 `ImportMapper` 与同一 `WeekExpressionParser`，只是第二个数据入口。

同时**保留"手动编辑课程"为 P1**（PRD 场景 E / 开放问题 1）：`ClassSession.source = MANUAL` 是预留扩展点，导入与手编在 DB 层天然并存。

**强制技术约束（防踩坑，写进实现规格）：**

1. WebView 开 `javaScriptEnabled` / `domStorageEnabled`；**不注册 `@JavascriptInterface`** 向页面暴露原生能力（页面内容来自校园网，按不可信输入处理），只用 `evaluateJavascript` 单向取值。
2. 会话交给系统 `CookieManager`，**不手工拼接 Cookie 头**。
3. iframe 同源则 `contentDocument` 直读；跨子域则改为"在 WebView 内直接打开 iframe 的 `src` 使其成为主文档"（此点必须先实测，见 OD-005）。
4. UA 兼容只作用于 `userAgentString` / `shouldInterceptRequest` 的只读判断，**不重写请求**（重写即等于复刻浏览器，回到路径 a 的泥潭）。
5. 证书问题只在 `network_security_config.xml` 显式声明信任锚；**禁止 `onReceivedSslError { handler.proceed() }`**（等于关闭 TLS 校验，安全红线）。
6. **App 不读取密码字段**；若为"下次免登录"做凭据缓存（OD-007），只允许 Keystore 加密存储且默认可关闭。
7. 导入必须经**核对视图**（Preview 强制环节），并把差异写入 `import_verify_diff` 本地事件（PRD 12 节）。
8. 导入提交必须**单事务**，失败整批回滚（PRD 9.1「不产生半截数据」）。

## Consequences

**正面**

- 登录环节的所有人机校验（短信、图形验证码、密码前端加密、CAS 多跳重定向）**天然通过**，无需逆向任何前端 JS。
- 教务系统"登录流程"改版时，真人自适应，代码无需改动；**只有"课表页面 HTML 结构"改版才会触发代码改动**，风险面被压缩到一个 `ScheduleHtmlParser`。
- 不需要申请任何敏感权限（无 SMS、无存储监听），符合 PRD"不采集隐私"。
- 桌面脚本路径**本机今天即可实现并实跑**（Python 3.13.14 已在位），可先行产出真实 JSON 夹具，供解析器测试使用，从而在 Android 工具链就绪前锁定算法正确性。
- 兜底路径与主路径共用解析器，回归测试只需覆盖一套解析逻辑。

**负面 / 代价**

- 主路径依赖 WebView 能正常访问教务系统；若教务系统对 WebView 有强反制，主路径退化为纯文件导入（需用户在电脑上操作一次）。
- `Preview` 强制环节增加一步用户操作，但这是刻意的（换取"导入后能一眼核对"，直接对应 PRD 5.3 第 2 痛点"导入识别错 → 旷课"）。
- iframe 跨域读取存在不确定性（OD-005），需真实环境验证后才可定稿抽取脚本。

**明确不采纳的替代方案及理由**

- 纯路径 a（App 内静默直连）：为**一学期一次**的低频操作支付全部反爬成本，且在短信验证码处直接不可行；如强行实现需索取 SMS 权限，违反 PRD 隐私约束。
- 纯路径 b（只要文件导入）：可行且最稳，但牺牲"App 内一键完成"的体验；保留为主路径的兜底而非唯一路径。

## Related ADRs
ADR-001（导入产物必须落到三层实体模型）、ADR-007（OkHttp / jsoup / kotlinx-serialization 版本锚定）
