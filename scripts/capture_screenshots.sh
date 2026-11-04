#!/usr/bin/env bash
#
# capture_screenshots.sh - Chụp ảnh màn hình thiết bị Android lưu vào thư mục Downloads
#
# Cách dùng:
#   ./scripts/capture_screenshots.sh                # Chế độ tương tác (nhấn Enter để chụp liên tiếp)
#   ./scripts/capture_screenshots.sh [Tên_Folder]   # Chỉ định tên folder lưu trong Downloads
#   ./scripts/capture_screenshots.sh --single [tên] # Chụp nhanh 1 ảnh duy nhất
#

set -e

# Màu sắc hiển thị
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# 1. Tìm lệnh ADB
ADB="$(which adb 2>/dev/null || true)"
if [ -z "$ADB" ]; then
    if [ -f "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        ADB="$HOME/Library/Android/sdk/platform-tools/adb"
    elif [ -n "$ANDROID_HOME" ] && [ -f "$ANDROID_HOME/platform-tools/adb" ]; then
        ADB="$ANDROID_HOME/platform-tools/adb"
    else
        echo -e "${RED}[ERROR] Không tìm thấy lệnh 'adb'. Hãy cài đặt Android SDK Platform-Tools.${NC}"
        exit 1
    fi
fi

# 2. Kiểm tra kết nối thiết bị
DEVICES=$("$ADB" devices | grep -v "List of devices attached" | grep -w "device" | awk '{print $1}')

if [ -z "$DEVICES" ]; then
    echo -e "${YELLOW}[INFO] Không thấy thiết bị online. Đang thử kết nối lại 192.168.1.112:5555...${NC}"
    "$ADB" connect 192.168.1.112:5555 2>/dev/null || true
    sleep 1
    DEVICES=$("$ADB" devices | grep -v "List of devices attached" | grep -w "device" | awk '{print $1}')
fi

DEVICE_COUNT=$(echo "$DEVICES" | grep -c . || true)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo -e "${RED}[ERROR] Không tìm thấy thiết bị Android nào được kết nối qua ADB!${NC}"
    echo -e "${YELLOW}Gợi ý: Cắm cáp USB hoặc chạy 'adb connect <IP>:5555'.${NC}"
    exit 1
fi

# Chọn thiết bị đầu tiên nếu có nhiều hơn 1
TARGET_DEVICE=$(echo "$DEVICES" | head -n 1)
echo -e "${CYAN}====================================================${NC}"
echo -e "${CYAN}        Android Screenshot Capture Tool            ${NC}"
echo -e "${CYAN}====================================================${NC}"
echo -e "Thiết bị: ${GREEN}${TARGET_DEVICE}${NC}"

# 3. Thiết lập thư mục lưu trong Downloads
FOLDER_NAME="${1:-ZephyrNG_Screenshots_$(date +%Y%m%d_%H%M%S)}"
# Nếu tham số đầu là --single
IS_SINGLE=false
if [ "$1" == "--single" ] || [ "$1" == "-s" ]; then
    IS_SINGLE=true
    FOLDER_NAME="ZephyrNG_Screenshots"
    SHOT_NAME="${2:-screenshot_$(date +%H%M%S)}"
fi

OUTPUT_DIR="$HOME/Downloads/$FOLDER_NAME"
mkdir -p "$OUTPUT_DIR"
echo -e "Thư mục lưu: ${BLUE}${OUTPUT_DIR}${NC}\n"

# 4. Thực hiện chụp ảnh
if [ "$IS_SINGLE" = true ]; then
    FILE_PATH="$OUTPUT_DIR/${SHOT_NAME}.png"
    echo -e "Đang chụp màn hình..."
    "$ADB" -s "$TARGET_DEVICE" exec-out screencap -p > "$FILE_PATH"
    if [ -s "$FILE_PATH" ]; then
        echo -e "${GREEN}✅ Đã lưu: ${FILE_PATH}${NC}"
        if command -v open >/dev/null 2>&1; then
            open "$OUTPUT_DIR"
        fi
    else
        echo -e "${RED}❌ Lỗi khi chụp màn hình!${NC}"
        rm -f "$FILE_PATH"
        exit 1
    fi
    exit 0
fi

# Chế độ tương tác chụp nhiều màn hình
echo -e "${YELLOW}Hướng dẫn sử dụng:${NC}"
echo -e " • Nhấn ${GREEN}[Enter]${NC} để chụp ảnh màn hình hiện tại."
echo -e " • Hoặc gõ tên gợi nhớ (VD: ${CYAN}home${NC}, ${CYAN}proxy${NC}, ${CYAN}firewall${NC}) rồi nhấn [Enter]."
echo -e " • Gõ ${RED}q${NC} hoặc nhấn ${RED}Ctrl+C${NC} để hoàn tất và mở thư mục.\n"

INDEX=1
while true; do
    printf "📸 [Ảnh #%02d] Nhập tên file (mặc định screen_%02d) hoặc 'q' để dừng: " "$INDEX" "$INDEX"
    read -r USER_INPUT

    if [ "$USER_INPUT" == "q" ] || [ "$USER_INPUT" == "Q" ] || [ "$USER_INPUT" == "exit" ]; then
        echo -e "\n${GREEN}Hoàn tất phiên chụp ảnh!${NC}"
        break
    fi

    if [ -z "$USER_INPUT" ]; then
        IMG_NAME=$(printf "screen_%02d" "$INDEX")
    else
        # Làm sạch tên file
        IMG_NAME=$(echo "$USER_INPUT" | tr ' ' '_' | sed 's/\.png$//')
        IMG_NAME=$(printf "%02d_%s" "$INDEX" "$IMG_NAME")
    fi

    FILE_PATH="$OUTPUT_DIR/${IMG_NAME}.png"
    echo -e "  -> Đang lưu: ${CYAN}${IMG_NAME}.png${NC}..."

    "$ADB" -s "$TARGET_DEVICE" exec-out screencap -p > "$FILE_PATH"

    if [ -s "$FILE_PATH" ]; then
        echo -e "  -> ${GREEN}Thành công!${NC}"
        INDEX=$((INDEX + 1))
    else
        echo -e "  -> ${RED}Chụp thất bại, vui lòng thử lại!${NC}"
        rm -f "$FILE_PATH"
    fi
    echo ""
done

COUNT=$((INDEX - 1))
echo -e "\n===================================================="
echo -e "🎉 Đã chụp tổng cộng: ${GREEN}${COUNT}${NC} ảnh."
echo -e "📁 Vị trí thư mục: ${BLUE}${OUTPUT_DIR}${NC}"
echo -e "===================================================="

# Mở Finder nếu trên macOS
if command -v open >/dev/null 2>&1; then
    open "$OUTPUT_DIR"
fi
