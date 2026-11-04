# BlockAds Android — 非官方修改版 / Unofficial fork

> **This is an unofficial, modified build.** It is not affiliated with, endorsed by, or supported by the
> upstream author. 上游是 [pass-with-high-score/blockads-android](https://github.com/pass-with-high-score/blockads-android)，
> 本仓库只是它的一个修改版（fork），改动见 [CHANGES.md](CHANGES.md)。

| 项 | 值 |
|---|---|
| 上游 | `pass-with-high-score/blockads-android` @ `514cbb4`（v7.1.0，2026-11-01 发布） |
| 本版标识 | `7.1.0-mod1` |
| 许可证 | **GPL-3.0**（沿用上游，[LICENSE](LICENSE) 未改动；本仓库的改动同样以 GPL-3.0 发布） |
| 上游 README | [README.upstream.md](README.upstream.md) |

---

## 为什么有这个仓库

官方 v7.1.0 新增了「代理与 VPN」模式（一个 VPN 槽同时做去广告 + 代理），但实际**连不上网**。排查后确认是三处代码问题 + 两处能力缺失：

1. **SS 节点被强行套 TLS**：`ProxyProfile.resolveServer()` 无条件补 SNI，`sanitizeOrRepair()` 随后把 `security=none` 改成 `tls`，Go 侧真的和 SS 服务器做 TLS 握手 → 所有连接失败。
2. **连接按钮无视选中节点**：`_selectedTab` 恒为 0，永远按 WireGuard 起。
3. **DNS 不走代理**：解析器用 `protect()` 的直连 socket，被墙域名拿到污染 IP，而隧道只按 IP 拨号。
4. **没有分流**：`relayDirectFromFlow()` 只有「走代理」和「直连」两个出口，没有 GeoIP 判定，国内流量也全走机场。
5. **Clash 订阅导不进来**：只有链接/Base64/3X-UI JSON 三种格式，没有 YAML。

本仓库把它们都修了，见下表与 [CHANGES.md](CHANGES.md)。

## 与原版的差异

**修 bug**

| # | 问题 | 改动位置 |
|---|---|---|
| 1 | SS 被强套 TLS | `ProxyProfile.kt`、`ProxyEditViewModel.kt`、`tunnel/internal/shadowsocks/config.go` |
| 2 | 大按钮恒按 WireGuard 启动 | `ProxyVpnViewModel.kt` |
| 3 | 代理页 Connect 不启动服务（`requestRestart` 在服务未运行时是空操作） | `ServiceController.requestApply()` |
| 4 | 切回本地 VPN 后旧代理仍生效（engine 跨启停存活，配置未清） | `GoTunnelAdapter.start()` |

**加能力**

| # | 能力 | 改动位置 |
|---|---|---|
| 5 | DNS 走代理（DNS-over-TCP 经代理发出，代理不可用自动回退） | `tunnel/engine_dns_proxy.go`、`tunnel/internal/dns/proxied.go` |
| 6 | 分流：国内域名走墙内 DNS、GeoIP 判 CN 直连、QUIC 只对境外丢弃 | `tunnel/engine_routing.go`、`tunnel/internal/dns/domestic.go`、`tunnel/fulltunnel.go` |
| 7 | 代理激活时 AAAA 返回空（GeoIP 只有 IPv4，保证分流判定完整） | `tunnel/engine_dns_query.go`、`tunnel/engine_dns_server.go` |
| 8 | Clash/mihomo 订阅解析（block + flow 两种 YAML 写法） | `utils/ClashSubscriptionParser.kt`、`SubscriptionImporter.kt` |
| 9 | GeoIP 库内置进 APK（`assets/preset/geoip_ipv4.bin`），装上即分流 | `app/src/main/assets/preset/` |
| 10 | 启动日志打印分流状态 | `tunnel/fulltunnel.go` |

## 构建

⚠️ **默认构建不含本仓库的 Go 改动。** 上游把 Go 隧道打成 `tunnel.aar` 发布到 release，Gradle 默认下载那个带校验的预编译版。要包含本仓库的 Go 代码（DNS 走代理、分流、SS 修复），必须自己编译隧道：

```bash
# 需要 JDK 21、Android SDK 37、Go 1.23+、NDK 26.1.10909125
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.1.10909125
export GOPATH=$HOME/go
./gradlew -Ptunnel.source=local :app:assembleFdroidRelease
```

或者先手工产出 AAR 再让 Gradle 直接用它（本仓库作者就是这样构建的）：

```bash
cd tunnel
gomobile bind -target=android -androidapi 24 -trimpath \
  -ldflags='-s -w -extldflags=-Wl,-z,max-page-size=16384' \
  -o ../build/tunnel/tunnel.aar github.com/nqmgaming/blockads-tunnel
cd .. && ./gradlew -Ptunnel.source=prebuilt :app:assembleFdroidRelease
```

> `gomobile` 需要 `go install golang.org/x/mobile/cmd/gomobile@latest` 与 `gobind`。
> 构建目录路径不能包含非 ASCII 字符（AGP 限制）。

## 安装包

见 [Releases](../../releases)。一切都是**自签**构建（`CN=BlockAds Self Build`），与官方签名不同，
安装前需要先卸载官方版本（会清空 App 数据）。本仓库不提供、也不应提交任何签名密钥。

## 已知限制

- 域名分流用的是内置的国内域名后缀表（约 80 条高频域），不是完整 geosite：小众国内站可能走机场。
- 代理激活时 DNS 的 AAAA 查询返回空（强制 IPv4），这是为了让 IPv4-only 的 GeoIP 库能完整判定。
- 没有 MITM：网页同域广告位、YouTube 贴片去不掉（只有域名级拦截）。
- UDP 不走代理：SS 等流式协议只承载 TCP；境外 QUIC 会被丢弃并回落 TCP。
- SS 不支持 `obfs` / `v2ray-plugin` / `shadow-tls` 插件。
- 代理节点若用域名且该域名被墙，本地解析会失败（应用自身不走 VPN），建议节点直接填 IP。

## 测试说明

改动经过两层验证，细节见 [CHANGES.md](CHANGES.md#测试与验证)：

- 单元测试：Go（DNS-over-TCP 走代理、国内域名判定、GeoIP 分流判定）、Kotlin（Clash 解析 7 例，含 flow-style）
- 端到端：Android 15 模拟器 + 真实 Shadowsocks 服务端 + 真实机场订阅（46 节点全部导入、Google 可达、国内站直连）

## 开发方式与 AI 协作声明

本修改版是**人机协作**产物，不隐瞒 AI 的参与：

| 角色 | 负责 |
|---|---|
| **hmy2333**（人类） | 提出需求、确定取舍、真机验收、决定开源与合规处理、承担发布责任 |
| **DeepSeek Harness（DSH）**（AI 助手运行环境） | 读上游源码定位根因、编写 Go/Kotlin 补丁与单元测试、搭建 gomobile + NDK + Gradle 构建链、在 Android 15 模拟器与真实 Shadowsocks 服务端/真实订阅上做端到端验证 |

换句话说：**代码分析、补丁实现、构建与测试由 AI 完成，方向、取舍与验收由人完成。**
AI 由人类逐轮驱动与审阅，不是全自动无人值守流程；所有结论都在真机/模拟器上复验过（见 [CHANGES.md](CHANGES.md#测试与验证)）。

如果你不接受 AI 参与产生的代码，请使用[上游仓库](https://github.com/pass-with-high-score/blockads-android)。

## 版本更新与发布

### 手机端怎么收到更新

App 内**没有更新检查**（上游也没有），所以不会自己弹升级。两条路：

1. **Obtainium**（推荐，开源，零后端）
   添加本仓库 `https://github.com/hmy2333/blockads-android-mod` → 它盯 Releases → 有新 tag 时通知并直接安装。
   前提：新 APK 用**同一把 keystore** 签名，才能覆盖安装不丢数据。
2. **手动**：关注 [Releases](https://github.com/hmy2333/blockads-android-mod/releases)，
   或订阅 `https://github.com/hmy2333/blockads-android-mod/releases.atom`（RSS）。

### 出包：本地一键

```bash
./scripts/release.sh v7.1.0-mod2 "修复 xxx；更新 yyy"
```

脚本做四件事：`gomobile bind` 重出 `tunnel.aar`（关键，否则没有本仓库的 Go 改动）→
`assembleFdroidRelease` → 生成 `SHA256SUMS.txt` → `gh release create` 传 4 个 ABI 的 APK。
前置要求与「构建」一节一致，另需 `gh auth login`。

### 出包：GitHub Actions 自动

`.github/workflows/release.yml`：**打 tag（`v*`）即自动构建并发布**，也可在 Actions 页面手动触发。

首次使用要在 *Settings → Secrets and variables → Actions* 配 4 个 secret：

| Secret | 内容 |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 keystore.jks` 的输出 |
| `KEYSTORE_PASSWORD` | storePassword |
| `KEY_ALIAS` | keyAlias |
| `KEY_PASSWORD` | keyPassword |

没有这 4 个 secret 时工作流会在第一步明确失败——故意的：换一把密钥签出来的 APK 装不上。

### 上游发新版了怎么合

本仓库的改动集中在 12 个文件，合流是可预期的：

```bash
git remote add upstream https://github.com/pass-with-high-score/blockads-android.git
git fetch upstream
git rebase upstream/main      # 冲突通常只在下列文件
```

冲突高发文件：`ProxyProfile.kt`、`ProxyVpnViewModel.kt`、`GoTunnelAdapter.kt`、`ServiceController.kt`、
`SubscriptionImporter.kt`、`tunnel/fulltunnel.go`、`tunnel/internal/dns/resolver.go`、
`tunnel/internal/shadowsocks/config.go`。

合完必须：① 若 `tunnel/` 有变动则重编 AAR；② 跑单元测试；③ 在真机/模拟器上过一遍
「导入订阅 → 连上 → 开 google / 开 bilibili」；④ 打新 tag 发版。

### 关于版本号与签名

- `versionCode` / `versionName` 沿用上游（当前 `7.1.0 (57)`）。同签名覆盖安装时 versionCode 相等即可，
  所以 mod 版本不需要为了"升级"去改它。
- 想把 mod 版本区分开，可以把 `versionName` 改成 `7.1.0-mod2`（`app/build.gradle.kts`），
  Obtainium 也能正常识别。
- **keystore 必须保管好**（`D:\AndroidEnv\keys\blockads.jks`，别放进仓库、别丢）。
  换了密钥 = 所有人必须卸载重装。

## 许可证与来源

- 代码：**GNU GPL-3.0**，见 [LICENSE](LICENSE)。上游版权归 Nguyen Quang Minh 及贡献者所有；本仓库的修改同样以 GPL-3.0 发布。
- 上游 README 原文保留在 [README.upstream.md](README.upstream.md)，未做改动。
- GeoIP 数据：`app/src/main/assets/preset/geoip_ipv4.bin` 取自上游 release 资产 `geoip-database`，数据源为
  [sapics/ip-location-db](https://github.com/sapics/ip-location-db)（**CC0 1.0 Universal**）。
- 广告过滤列表、浏览器注入资源等均沿用上游配置，未做改动。
- 本仓库**移除了上游的 GitHub Actions workflows**（`deploy.yml` / `update_tunnel.yml` / `update_geoip.yml` / `ci.yml`），
  因为它们依赖上游的 release 命名空间与仓库级 secrets，在本仓库直接运行只会失败或污染 release 列表；
  只保留了本仓库自己的 `.github/workflows/release.yml`（打 tag 出包，用本仓库的 secret 签名）。
- 本仓库不包含任何签名密钥、订阅链接或他人凭证。

## 免责声明

非官方修改版，按 GPL-3.0 第 15/16 条**不提供任何担保**。使用前请自行评估；因使用本构建产生的任何后果由使用者承担。
如果你需要官方支持，请使用上游仓库。
