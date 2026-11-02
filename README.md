# SplashCleaner Android｜开屏广告自动跳过工具

开屏净化（SplashCleaner）是一个全新开发的开源 Android 开屏广告自动跳过工具。它通过 Android Accessibility Service（无障碍服务）识别并点击“跳过广告”或安全的关闭控件，支持 Android 6–16、常见国内广告 SDK、应用内弹窗广告规则以及小米商城 / MIUI / HyperOS 专项适配。

English: An open-source Android splash-ad skipper. It uses Accessibility Service to detect and close splash ads locally, without root, VPN, traffic interception, or the Internet permission.

本仓库只包含 SplashCleaner 独立源码、构建脚本、测试、规则生成工具和本项目 APK；不包含原版李跳跳 APK、反编译代码或原仓库提交历史。

搜索关键词：李跳跳、LiTiaotiao、开屏广告跳过、安卓广告自动关闭、Android ad skipper、splash ad blocker、Accessibility Service、小米商城广告、MIUI、HyperOS。

[![Android 6+](https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android)](https://developer.android.com/)
[![Java](https://img.shields.io/badge/Java-11-E76F00?logo=openjdk)](app/src/main/java)
[![No Internet Permission](https://img.shields.io/badge/INTERNET%20permission-none-success)](app/src/main/AndroidManifest.xml)

> 说明：它通过 Android 无障碍服务尽快点击可见的“跳过”控件，不会拦截广告网络请求，也不能保证广告第一帧完全不显示。

## 下载最新版 APK

- [SplashCleaner 3.1.1 小米商城修复版](dist/SplashCleaner-3.1.1-xiaomi-fix-debug.apk)
- SHA-256：`ccb4b8d2f8c77bd69a46a530af9181c960fba08163571e1ddae74f0ee398c00a`

这是便于测试的 Debug 签名 APK。公开分发或二次开发时，请配置自己的 Release 签名。

## 这次升级包含什么

- 切换到新应用后立刻开始扫描，默认只工作 10 秒，之后自动停止。
- 同时识别简体中文、繁体中文、英文跳过文字和倒计时文字。
- 覆盖穿山甲、快手、广点通、百度、Sigmob、Tanx 等常见广告 SDK 的通用控件 ID 特征。
- 优先执行无障碍 `ACTION_CLICK`；控件明确但不可点击时，可使用受限的触控补点。
- 普通“关闭”按钮必须同时具备广告上下文才会点击。
- “下载、安装、支付、同意、授权、确认、更新”等高风险按钮直接拒绝。
- “跳过视频、片头、片尾、教程、剧情”等非广告跳过操作直接拒绝。
- 系统界面、权限界面、安装器、桌面永不自动点击。
- 银行、支付、政务类应用只接受明确的“跳过”文字，不使用 ID 猜测。
- 支持按应用加入忽略名单，避免不需要的自动操作。
- 支持应用运行期间的包级弹窗广告关闭规则；规则严格绑定应用包名哈希。
- 新增小米商城 `com.xiaomi.shop` 启动广告专用结构规则：识别无文字、无资源 ID 的关闭图标，只在应用启动扫描窗口内直接点击，不执行返回或坐标盲点。
- 不声明 `INTERNET` 权限，识别、规则和记录全部留在本机。
- 使用 Android 16（API 36）编译，最低支持 Android 6.0（API 23）。

## 使用方法

1. 安装生成的 APK。
2. 打开“开屏净化”，点击“开启无障碍服务”。
3. 在系统列表里找到“开屏净化自动跳过服务”并开启。
4. 返回应用，状态显示“自动跳过已就绪”后即可正常打开其他应用。

Android 13 及以上若开关呈灰色，需要先进入系统的本应用“应用信息”页，通过右上角菜单选择“允许受限设置”。不同厂商的菜单名称可能略有差异。

## 构建

构建需要 JDK 17 和 Android SDK 36：

```bash
./gradlew clean test lint assembleDebug
```

可安装的调试包位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

正式分发前应配置自己的 release 签名。应用包名是 `com.wgh.splashcleaner`。

## 规则维护

通用开屏规则在 [`app/src/main/assets/skip_rules.json`](app/src/main/assets/skip_rules.json)。可以维护：

- `exactTexts`：明确的跳过/关闭广告文字；
- `textPatterns`：带倒计时的完整文字正则；
- `strongIdTokens`：广告 SDK 的高置信度控件 ID 片段；
- `genericCloseIdTokens`：必须配合广告上下文的普通关闭 ID；
- `adContextTokens`：用于确认当前容器属于广告的特征；
- `dangerousActionTokens`：无论其他分数多高都禁止点击的操作文字。
- `excludedSkipContextTokens`：明确排除的视频、教程、剧情等非广告场景。

### 外部弹窗规则

内置弹窗规则同步自 [743859910/LiTiaoTiao_Custom_Rules](https://github.com/743859910/LiTiaoTiao_Custom_Rules) 的 `master` 提交 `19f2709ed2c63271cba6f18976bda4340e6b3984`（2025-12-31）。上游原始文件包含 328 组应用条目、795 条弹窗规则，其中 4 个内层 JSON 带尾随逗号；本仓库不收录该原始规则文件。

原规则不只有广告，还包含权限、更新、评分、青少年模式等操作。新版 APK 不会原样执行它们，而是通过 [`tools/update_popup_rules.py`](tools/update_popup_rules.py) 生成 [`app/src/main/assets/popup_ad_rules.json`](app/src/main/assets/popup_ad_rules.json)：

- 修复 4 个可确定的尾随逗号语法问题；
- 只保留触发条件带广告证据、动作明确为关闭/跳过的规则；
- 排除全局返回、安装、下载、支付、授权等操作；
- 最终导入 70 组包名哈希、87 条高置信度弹窗广告关闭规则；
- 包级规则只使用无障碍直接点击，不使用坐标补点；
- 银行、支付、政务等敏感应用不执行外部弹窗规则。

同步命令示例：

```bash
python3 tools/update_popup_rules.py \
  --source /path/to/LiTiaotiao_Custom_Rules.txt \
  --output app/src/main/assets/popup_ad_rules.json \
  --commit <upstream-commit> \
  --commit-date <upstream-commit-date>
```

外部规则采用 MIT 许可证，许可文本随 APK 一并放在 `THIRD_PARTY_NOTICES.txt` 中。

### 小米商城专项适配

`LiTiaoTiao_Custom_Rules` 当前没有小米商城包名哈希 `277863260`。小米商城的启动全屏活动广告关闭按钮不一定暴露“跳过”文字或资源 ID，因此通用规则会保守地拒绝点击。

3.1.1 加入仅绑定 `com.xiaomi.shop` 的结构规则，并参考公开的 [GKD 小米商城规则](https://github.com/Lin-arm/GKD_subscription/blob/main/src/apps/com.xiaomi.shop.ts) 对两种广告容器结构做交叉验证。该规则还要求候选控件为小尺寸、可点击、无危险操作文字的 `ImageView`，且只在启动后的扫描窗口内运行；它属于开屏适配，不受“应用内弹窗广告”开关影响。

## 适配边界

以下情况无法只靠无障碍节点可靠处理：

- 广告完全绘制在 `SurfaceView`、游戏引擎或未暴露语义的 Canvas 中；
- 厂商隐藏了控件文字、资源 ID 和可点击区域；
- 应用在远程配置中临时更换了广告结构；
- 系统或厂商省电策略关闭了无障碍服务。

为了隐私，本实现没有加入持续截图/OCR，也没有加入 VPN、Root、Hook 或证书中间人。遇到未适配应用时，优先补充精确控件 ID 规则，而不是放宽全局点击条件。

本项目仅用于个人设备上的辅助操作与学习研究。请遵守所在地法律、应用协议和 Android 平台关于无障碍服务的要求。
