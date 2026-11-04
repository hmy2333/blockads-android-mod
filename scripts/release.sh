#!/usr/bin/env bash
#
# 一键出包 + 发布到 GitHub Releases。
#
# 用法：
#   ./scripts/release.sh v7.1.0-mod2 "修复 xx；更新 xx"
#
# 前置条件（与 README 的构建章节一致）：
#   - JDK 21、Android SDK 37、Go 1.23+、NDK 26.1.10909125、gomobile
#   - 仓库根目录有 key.properties（指向你的 keystore，已在 .gitignore 里）
#   - gh 已登录（gh auth login），对目标仓库有写权限
#
# 环境变量（可选）：
#   ANDROID_HOME / ANDROID_NDK_HOME / GOPATH   不设则用默认值
#   RELEASE_REPO                               默认 hmy2333/blockads-android-mod
#
set -euo pipefail

TAG="${1:?用法: ./scripts/release.sh <tag> [说明]}"
NOTES="${2:-}"
REPO="${RELEASE_REPO:-hmy2333/blockads-android-mod}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> 仓库: $ROOT"
echo "==> 标签: $TAG   目标仓库: $REPO"

# ── 前置检查 ────────────────────────────────────────────────
[ -f key.properties ] || { echo "缺少 key.properties（签名配置），拒绝发布"; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "不是 git 仓库"; exit 1; }

if [ -n "$(git status --porcelain)" ]; then
  echo "⚠️  工作区有未提交改动，发布产物将不对应任何 commit："
  git status --short | head -10
  read -r -p "继续? [y/N] " ok
  [ "$ok" = "y" ] || exit 1
fi

echo "==> 当前 HEAD: $(git log --oneline -1)"

# ── 工具链 ──────────────────────────────────────────────────
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/26.1.10909125}"
export GOPATH="${GOPATH:-$HOME/go}"
export PATH="$GOPATH/bin:$PATH"
export GOFLAGS="${GOFLAGS:--buildvcs=false}"

command -v gomobile >/dev/null || { echo "找不到 gomobile：go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.12 && gomobile init"; exit 1; }
command -v gh >/dev/null || { echo "找不到 gh（GitHub CLI）"; exit 1; }

# ── 1. 重建 Go 隧道 AAR（不含这步，APK 里就没有 DNS 走代理/分流）──
echo "==> 构建 tunnel.aar（gomobile, 4 个 ABI，约 2-4 分钟）"
mkdir -p build/tunnel
( cd tunnel && gomobile bind -target=android -androidapi 24 -trimpath \
    -ldflags='-s -w -extldflags=-Wl,-z,max-page-size=16384' \
    -o ../build/tunnel/tunnel.aar github.com/nqmgaming/blockads-tunnel )

# ── 2. 构建 APK ────────────────────────────────────────────
echo "==> 构建 release APK"
./gradlew -Ptunnel.source=prebuilt :app:assembleFdroidRelease --console=plain

OUT=app/build/outputs/apk/fdroid/release
ls -1 "$OUT"/*.apk >/dev/null 2>&1 || { echo "没有产物：$OUT"; exit 1; }

# ── 3. 校验与摘要 ──────────────────────────────────────────
echo "==> 产物"
( cd "$OUT" && sha256sum *.apk | tee SHA256SUMS.txt )

# ── 4. 发布 ────────────────────────────────────────────────
echo "==> 发布 $TAG 到 $REPO"
DEFAULT_NOTES="自签构建，与官方签名不同；覆盖安装前请确认已装的是本仓库的构建（同一把 keystore）。"
gh release create "$TAG" \
  --repo "$REPO" \
  --title "$TAG" \
  --notes "${NOTES:-$DEFAULT_NOTES}" \
  "$OUT/app-fdroid-arm64-v8a-release.apk" \
  "$OUT/app-fdroid-armeabi-v7a-release.apk" \
  "$OUT/app-fdroid-x86_64-release.apk" \
  "$OUT/app-fdroid-universal-release.apk" \
  "$OUT/SHA256SUMS.txt"

echo
echo "✅ 完成：https://github.com/$REPO/releases/tag/$TAG"
echo "   手机端装了 Obtainium 的话，轮询到新 tag 会提示更新（同一签名可直接覆盖安装）。"
