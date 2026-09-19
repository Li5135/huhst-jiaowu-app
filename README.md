# HUHST 

把湖南人文科技学院的**强智教务系统**做成原生 Android App。数据经学校 WebVPN 或校园网直连获取，
界面用 Jetpack Compose + Material 3 Expressive 从零实现。

> 学校没有官方 App；WebVPN 门户在手机上操作繁琐。这个项目把最常用的几件事
> ——看课表、查成绩——做成一屏就能完成。

---

## 下载安装

到 **[Releases](https://github.com/Li5135/huhst-jiaowu-app/releases/latest)** 下载最新的
`HUHST-x.y.z.apk`，传到手机上安装（需允许「安装未知来源应用」）。

当前版本：**v1.0.3**

> APK 只放在 Release 里，**不提交进仓库**。二进制文件进 Git 会让仓库迅速膨胀，
> 而且每次构建都产生一个新版本。仓库里只有源码。

---

## 功能

| 功能 | 状态 | 说明 |
|---|---|---|
| 两步登录 | ✅ | 统一身份认证 + 教务系统；账号密码加密保存，支持自动登录 |
| **课程表** | ✅ | 自动定位当前周、按课程着色、表头显示日期、跨节次连堂课、点击看详情 |
| **成绩查询** | ✅ | 按学期筛选；自动算平均分 / 学分绩点 / 已修学分；等级制成绩（优/良）同样支持 |
| 个人信息 | ✅ | 姓名 / 学号 / 学院 / 专业 / 班级 |
| 教学进度 | ⚠️ | **界面完成，数据未接通**——该功能的入口地址由 JS 动态加载，尚未定位到（见「已知限制」） |
| 设置 | ✅ | 自定义背景（相册选图）、周日起始（切换课表首列） |

界面为**浅色模式**，Purple 系 M3 Expressive 配色，动效统一用 `MotionScheme.standard()`。

> 仓库里没有截图：所有实机截图都含有本人的真实姓名、学号与成绩，不适合公开。
> 需要看图请自行构建运行。

---

## 构建

### 环境

| 项 | 要求 |
|---|---|
| JDK | 21（Android Studio 自带的 JBR 即可） |
| Android SDK | platform `android-37.0`、build-tools 37.0.0 |
| Gradle | 9.5.1（用仓库自带的 `gradlew` 即可） |

`local.properties` 里写好 `sdk.dir=<你的 SDK 路径>`，然后：

```bash
./gradlew assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # 产物 app/build/outputs/apk/release/app-release.apk
```

### 版本矩阵（踩过的坑，别乱动）

| 组件 | 版本 | 说明 |
|---|---|---|
| AGP | 9.1.0 | **必须 ≥ 9.1.0**：Compose 1.12 / core 1.19 / lifecycle 2.11 / navigation 2.10 / okhttp 5.5 的 AAR 元数据都要求它，并要求 `compileSdk 37` |
| Gradle | 9.5.1 | AGP 9.4.0 要求 ≥ 9.6.0 |
| compileSdk | 37 + `compileSdkMinor = 0` | 本机 platform 目录是 `android-37.0`；只写 `compileSdk = 37` 会去找不存在的 `android-37` |
| Kotlin | 2.4.20 | **AGP 9 内置 Kotlin 支持**，不要再应用 `org.jetbrains.kotlin.android`，否则报错 |
| material3 | **1.5.0-alpha28** | `MaterialExpressiveTheme` / `MotionScheme` 在 1.4.0 里是 `internal`，只有 1.5.0-alpha 线公开 |
| Compose BOM | 2026.09.00 | material3 版本被上面显式覆盖 |
| OkHttp | 5.5.0 | 注意 `HttpUrl.get()` 已移除，用 `toHttpUrlOrNull()` |
| 持久化 | DataStore + kotlinx.serialization | 刻意不用 Room，省掉 KSP 版本匹配 |
| DI | 手写 `AppContainer` | 刻意不用 Hilt，省掉注解处理器 |

### 签名

`release` 目前复用 debug 签名，目的是让 `assembleRelease` 开箱可用。
**正式对外分发前请换成自己的 keystore**（`app/build.gradle.kts` 的 `signingConfigs`）。

---

## 架构

```
ui/            Compose 界面：JiaowuRoot → 登录 / MainScaffold（三屏 + 详情 + 设置 + 关于）
ui/theme/      配色角色、形状、字体、MotionScheme.standard()
ui/vm/         AppViewModel（会话 + 设置 + 数据 + 同步，单例）
ui/components/ 空状态、加载态、区块标题、应用背景
ui/icons/      手写矢量图标（material-icons-core 缺的几个）
data/model/    CourseSession / TeachingProgress / Grade / UserProfile / AppSettings
data/local/    JiaowuStore —— DataStore 持久化（业务数据 / 设置 / 凭证 / Cookie 分开存）
data/net/      SharedCookieJar · WebVpn · QzPaths · QzClient · QzParser
data/repo/     JiaowuRepository —— 取数 → 解析 → 落盘
security/      CredentialVault —— Android Keystore AES/GCM
di/            AppContainer —— 手写依赖容器
```

### 关键设计

**登录用 WebView，取数用原生 HTTP。** CAS 带验证码与前端加密 JS，WebVPN 还会把页面上每个内联
事件改写成 `eval(vpn_rewrite_js(...))`——脱离注入脚本页面不可用。因此认证交给一次性 WebView，
成功后把 Cookie 灌进 OkHttp，之后全部走原生请求。

**双通道。** 校园网内直连 `http://10.1.1.149/jsxsd`（最快，也不占学校 VPN 并发）；
校外经 `http://vpn.huhst.edu.cn:9900/http/<宿主码>/jsxsd`。AUTO 模式先直连后 VPN。
宿主码登录后从门户首页自动发现并缓存，不硬编码。

**降级而非崩溃。** 解析器匹配不到就返回空，页面显示空状态而不是崩溃。
学校改版时只需要动 `QzPaths.kt` 与 `QzParser.kt` 两个文件——这是刻意把易变部分收敛到一处。

**课程配色单独一套。** 界面颜色一律走 `MaterialTheme.colorScheme` 的角色；
课表需要「不同课程不同颜色」，而配色方案是单色紫系，容器角色只有三个近邻色相，
所以课表另用一套 12 色分类色板（数据可视化色，与 UI 主题色分工不同）。

---

## 登录设计（两步）

学校的结构是**嵌套的**：统一身份认证（正方 CAS）与教务系统（强智 jsxsd）位于
**两台不同的内网主机**，是两套独立系统，**密码互不相同**（用户名同为学号）。
所以 App 必须登录两次，这不是设计冗余。

```
原生登录页（居中表单）
   │
   ├─ 第 1 步：统一身份认证   学号 + 密码A
   │     └─ 后台静默提交 → 拿到 WebVPN 会话 Cookie
   │
   ├─ 深链直达教务系统（跳过门户资源列表）
   │
   ├─ 第 2 步：教务系统       学号（自动带入）+ 密码B
   │     └─ 提交 → 进入 xsMain.htmlx
   │
   └─ 之后全部走原生 HTTP 取数
```

表现层是原生 Compose 表单；真正的提交由一个**不可见的 WebView** 完成——它把值填进真实表单、
再点真实的登录按钮，因此学校页面的密码加密 JS 与 WebVPN 的跳转链都原样生效，不需要复刻。

- CAS 页的提交按钮是 `type="button"` 的 `#dl`（靠 JS 处理器），必须**点它**而不是自己构造
  POST，否则密码加密那一步会被跳过。
- 自动化走不通时（验证码、页面改版）自动降级为「显示网页让用户手动登录」。

---

## 已校准的页面结构（实机抓取）

以下都是对着学校真实页面反推出来的，改版时按这张表改 `QzParser.kt`。

### 课表 `/jsxsd/xskb/xskb_list.do`

- 主表 `table#timetable`；表头 `星期一`…`星期日` 共 7 列
- 数据行是**大节**（`第一大节 (01,02小节)` … `第五大节`）
- 单元格内每个 `div.kbcontent` 是一节课，字段靠 `font[title]` 区分：

| font 的 title | 含义 | 示例 |
|---|---|---|
| （无 title 的第一个） | 课程名 | 概率论与数理统计 |
| `周次(节次)` | 周次 | `1-16(周)` / `2,4,6,8(周)` / `2-11(周)[01-02节]` |
| `教学楼` | 教学楼 | 【致远楼】 |
| `教室` | 教室 | 致远-120 |
| `教师` | 教师 | 龙承星副教授 |
| `班级` / `备注` | 班级 / 备注 | |

> ⚠️ 周次文本的 `[01-02节]` 后缀里的数字**必须先剥离**，否则会被当成第 1、2 周。

学期下拉框在**课表页**上（`select[name=xnxq01id]`）；`xsMain.htmlx` 只是 frameset，没有学期列表。

### 教学周历 / 当前周 `/jsxsd/framework/xsMain_new.htmlx?t1=1`

**课表页本身不含任何日期**。周次与日期的映射藏在「我的桌面」主框架的周次下拉里，
而且教务系统自己用 `selected` 标出了当前周：

```html
<select name="semester"><option>2026-2027-1</option></select>
<select name="week" id="week">
  <option value="2026-09-07">第一周</option>
  <option value="2026-09-14" selected>第二周</option>
</select>
```

取第一项的日期作为「第 1 周的周一」，之后就能随日期自行推算今天是第几周 ——
**不需要每次同步，App 隔几周再打开也算得对**。

### 成绩 `/jsxsd/kscj/cjcx_list`

按**表头文字**定位列（「课程名称」「学分」「成绩」「绩点」「课程性质」），
因此对列顺序不敏感，等级制成绩（优/良/合格）也能正确落位。

### 个人信息 `/jsxsd/grxx/xsxx`

### 登录后页面骨架

```
/jsxsd/framework/xsMain.htmlx                      ← frameset
  ├─ iframe  /jsxsd/grsz/grsz_xggrxx.do?type=dlym   ← 内嵌认证页，取不到属正常
  └─ iframe#Frame0  /jsxsd/framework/xsMain_new.htmlx?t1=1  ← 真正的主框架
```

---

## 安全与合规

- 仅读取**本人账号**的教务数据
- **不绕过** CAS、验证码、VPN 任何认证环节
- 账号密码经 Android Keystore 加密后存本机，密钥不出安全区
- 已关闭云备份与换机迁移（`allowBackup=false` + `data_extraction_rules`）
- 明文 HTTP **只对** `vpn.huhst.edu.cn` 与校内 `10.1.1.x` 放行，其余域名一律要求 HTTPS
- 自用项目，与学校官方无关

---

## 已知限制

1. **教学进度未接通**。菜单里的「教学进度查询」由 JS 动态加载，静态 HTML 里拿不到地址，
   代码里的候选路径实测全部返回占位页。拿到真实入口地址后替换 `QzPaths.PROGRESS_CANDIDATES`
   并确认 `QzParser.parseProgress` 的选择器即可。
2. **两处硬编码的学校信息**：内网教务地址 `10.1.1.149`、WebVPN 门户 `vpn.huhst.edu.cn:9900`
   （`QzPaths.kt` / `AppSettings` 默认值）。换学校需要改这两处。
3. **仅浅色模式**，未做深色主题。
4. `release` 复用 debug 签名，正式分发前需替换。

---

## 许可

MIT
