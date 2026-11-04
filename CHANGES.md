# 改动记录（相对上游 v7.1.0 / `514cbb4`）

所有改动都在本仓库源码里，可直接 `git diff upstream` 查看。下面按「修 bug」和「加能力」分组，并给出为什么这么改。

---

## 一、修 bug

### 1. Shadowsocks 被强行套 TLS，导致代理模式所有连接失败

**现象**：官方 v7.1.0 的「代理与 VPN」模式，填入任何 SS 节点都连不上，日志里是 TLS 握手失败。

**原因链**（三处叠加）：

1. `ProxyProfile.resolveServer()` 无条件执行 `sni = sni.ifEmpty { server }` —— SS 节点也会被填上 SNI（服务器是域名就填域名，是 IP 就填 IP 字符串）。
2. `ProxyProfile.sanitizeOrRepair()` 看到 `security == "none"` 且 `sni` 非空，就把 `security` 改成 `"tls"`。
3. Go 侧 `internal/shadowsocks/client.go` 见 `security == "tls"` 真的去和 SS 服务器做 TLS 握手；SS 服务端不会回 TLS，握手必然失败。

另外两条独立入坑路径：手工新建节点时 `ProxyEditViewModel` 的默认值是 `else -> "tls"`；旧版本迁移过来的 SS 节点用的是 `ProxyProfile` 字段默认值 `security = "tls"`。

**修复**：

- `ProxyProfile` 新增 `usesTls`（SS 恒为 false），`resolveServer()` 只对真需要 TLS 的协议补 SNI。
- `sanitizeOrRepair()` 的 `none → tls` 补全只对 `usesTls == true` 的协议生效。
- `ShadowsocksProfile.toProxyProfile()` 迁移时显式 `security = "none"`。
- `ProxyEditViewModel` 新建 SS 节点默认 `security = "none"`。
- Go 侧去掉「端口 443 就当 TLS」的启发式（`internal/shadowsocks/config.go`），只按显式配置走。

### 2. 代理页的连接按钮无视选中节点

`ProxyVpnViewModel.toggleProxyVpn()` 用 `_selectedTab.value` 判断类型，但节点页在 2026-10-25 合并成统一列表后 `selectTab()` 已无调用方，`_selectedTab` 恒为 0 → 永远按 WireGuard 类型启动。

**修复**：改为看实际选中的节点（`_activeProxyProfileId` / `_activeWgProfileId`）。

### 3. 代理页点 Connect 不会真正启动服务

`ServiceController.requestRestart()` 的实现是「如果服务在跑就重启」，服务没跑时是空操作 —— 于是 UI 状态变了，VPN 没起来。

**修复**：新增 `ServiceController.requestApply()`（在跑就重启，没跑就启动），`ProxyVpnViewModel` 中除「配置回调」外的调用点改用它。

### 4. 切回「本地 VPN 模式」后旧代理节点仍在生效

`GoTunnelAdapter` 持有的 Go engine 实例跨启停存活，`stop()` 不清代理配置。切回本地模式时 Kotlin 不传代理参数，Go 侧就继续用上一次的节点转发流量。

**修复**：`GoTunnelAdapter.start()` 在既没有代理也没有 SS 配置时显式 `engine.setProxyNodeConfig("", "")`。

---

## 二、加能力

### 5. DNS 走代理（DNS-over-TCP through proxy）

**问题**：隧道所有 TCP 流都按 **IP** 交给代理（SS 客户端只发 SOCKS5 的 IP 地址类型，没有域名类型）。所以域名必须先本地解析；墙内解析被污染 → 代理按假 IP 去连 → 网站打不开。

**实现**：

- `tunnel/engine_dns_proxy.go`：把 `Engine.DialProxy` 适配成 `net.Conn` 拨号器，拒绝域名（避免二次本地解析）。
- `tunnel/internal/dns/proxied.go`：`Resolver.SetProxyDial()` + `queryProxiedTCP()`，按 RFC 7766 的 2 字节长度前缀收发。
- `Resolver.Resolve()` 在代理激活时优先走代理 DNS；代理不可用自动回退本地解析（国内不至于断 DNS）。

### 6. 分流（split routing）

**实现**：

- `tunnel/engine_routing.go`：`shouldBypassProxy(ip)` —— 私有/回环/CGNAT 直连；GeoIP 判定为 `CN` 直连；无 GeoIP 库时保守地全走代理。
- `tunnel/fulltunnel.go`：`relayDirectFromFlow()` 按上面的判定决定走代理还是直连；UDP 443（QUIC）只对「会被代理」的目标丢弃，国内目标保留 HTTP/3。
- `tunnel/internal/dns/domestic.go`：国内域名后缀表 + 从原始 query 里取 QNAME（不整包解析，热路径零分配）。
- `Resolver.Resolve()`：国内域名改用墙内 DNS（默认 223.5.5.5）直连解析，避免境外解析把国内 CDN 指到海外边缘。

### 7. 代理激活时 AAAA 返回空

GeoIP 库是 IPv4-only，IPv6 目标无法判定归属。不加这条的话，双栈网络下国内站点拿到 IPv6 → 判不出 CN → 全部丢给机场（实测就是这个现象）。

**实现**：`engine_dns_query.go` / `engine_dns_server.go` 在 `preferredIPType == auto && proxyActive()` 时，AAAA 查询返回空响应（等价于「首选 IPv4」）。

### 8. Clash / mihomo 订阅解析

上游只认「纯链接 / Base64 链接列表 / 3X-UI JSON」，不认 Clash 的 `proxies:` YAML。

**实现**：新增 `utils/ClashSubscriptionParser.kt`（无第三方依赖，自写 YAML 子集解析），`SubscriptionImporter.parseSubscriptionContent()` 开头分流。

- 支持 **block style**（`- name: x` 换行缩进）与 **flow style**（`- {name: x, type: ss, ...}`）。
- 支持两级嵌套：`ws-opts.path`、`ws-opts.headers.Host`、`reality-opts.public-key/short-id`。
- 支持 `ss / trojan / vmess / vless(+Reality) / hysteria2 / tuic`。
- 不支持的（`socks5`/`http`/`ssh`/`anytls`/`ss+plugin`）计入 `failedCount` 跳过，不影响其余节点。
- 故意忽略 `proxy-groups` / `rules`：本隧道的分流是按 IP/域名做的，Clash 路由规则在这里没有语义。

### 9. GeoIP 库内置进 APK

上游是运行时从 GitHub release 下载（国内没梯子时经常拿不到，分流就退化成「全走代理」）。本仓库把同一份文件放进 `app/src/main/assets/preset/geoip_ipv4.bin`（2.9 MB / 297,983 条），`TunnelRuleLoader.loadGeoIPDatabase()` 优先用 `filesDir` 的下載版，没有才用内置。

### 10. 启动日志

`StartFull` 打印 `Split routing: on (N geoip records)` / `off`，方便确认分流是否生效。

---

## 测试与验证

### 单元测试

| 位置 | 覆盖 |
|---|---|
| `tunnel/internal/dns/proxied_test.go` | 起本地 DoT 服务，验证查询确实经 `proxyDial` 发出、答案来自它；拨号失败时不吞异常 |
| `tunnel/internal/dns/domestic_test.go` | 国内域名后缀匹配（含大小写、`cn.` 前缀反例）、原始 query 的 QNAME 提取 |
| `tunnel/engine_routing_test.go` | 构造合成 GeoIP 库，验证 CN/境外/私有/回环/CGNAT 判定；无库时的保守行为 |
| `app/src/test/.../ClashSubscriptionParserTest.kt` | block + flow 两种写法；SS 不得被标成 tls；VLESS Reality 嵌套字段；VMess WS；Trojan/Hy2 字段 |

### 端到端（Android 15 模拟器，x86_64）

1. 本机用 sing-box 起真实 Shadowsocks 服务端，验证：国内站 `[Split] ... -> DIRECT`、境外站经代理、DNS 走代理、切回本地模式后 `Proxy node cleared`。
2. 用真实机场订阅（Clash，46 节点 / SS 34 + VMess 12）验证：全部导入、Google 可打开（出口为香港节点）、`bilibili.com` 走直连、无 `proxy dial ... failed`。

---

## 未改动的部分

- Go 隧道的数据面（gVisor 栈、MITM、过滤器、脚本注入）未改动。
- 广告过滤列表、浏览器资源、UI 文案与布局未改动。
- `gradle/tunnel.aar.sha256` 仍指向上游发布的 AAR：**默认构建不会有本仓库的 Go 改动**，必须 `-Ptunnel.source=local`（见 README「构建」）。
