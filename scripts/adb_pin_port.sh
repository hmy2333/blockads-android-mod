#!/usr/bin/env bash
#
# adb_pin_port.sh - Ghim cố định port Wireless Debugging (mặc định 5555) cho Android
#

set -e

TARGET_PORT="${1:-5555}"
ROOT_MODE=false

# Kiểm tra cờ --root hoặc -r
for arg in "$@"; do
    case "$arg" in
        --root|-r)
            ROOT_MODE=true
            ;;
        [0-9]*)
            TARGET_PORT="$arg"
            ;;
    esac
done

# Màu sắc hiển thị
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}====================================================${NC}"
echo -e "${CYAN}     Android Wireless Debugging Port Pinning Tool   ${NC}"
echo -e "${CYAN}====================================================${NC}"
echo -e "Target Port: ${GREEN}${TARGET_PORT}${NC}\n"

# 1. Tìm ADB
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

# Hàm lấy IP Wi-Fi của thiết bị
get_device_ip() {
    local target_serial="$1"
    local ip=""
    # Thử qua wlan0
    ip=$("$ADB" -s "$target_serial" shell ip -4 addr show wlan0 2>/dev/null | grep -oE 'inet [0-9.]+' | awk '{print $2}' | head -n 1 || true)
    
    # Nếu không có wlan0, thử qua ip route
    if [ -z "$ip" ]; then
        ip=$("$ADB" -s "$target_serial" shell ip route 2>/dev/null | grep -oE 'src [0-9.]+' | awk '{print $2}' | head -n 1 || true)
    fi

    # Fallback: getprop dhcp.wlan0.ipaddress
    if [ -z "$ip" ]; then
        ip=$("$ADB" -s "$target_serial" shell getprop dhcp.wlan0.ipaddress 2>/dev/null | tr -d '\r\n' || true)
    fi
    echo "$ip"
}

ATTACHED_DEVICES=()
while IFS= read -r line; do
    [ -n "$line" ] && ATTACHED_DEVICES+=("$line")
done < <("$ADB" devices | grep -v "List of devices attached" | grep "device$" | awk '{print $1}' || true)

TARGET_SERIAL=""

if [ ${#ATTACHED_DEVICES[@]} -gt 0 ]; then
    echo -e "${GREEN}[INFO] Tìm thấy ${#ATTACHED_DEVICES[@]} thiết bị đang kết nối qua ADB:${NC}"
    for i in "${!ATTACHED_DEVICES[@]}"; do
        echo -e "  [$i] ${ATTACHED_DEVICES[$i]}"
    done
    TARGET_SERIAL="${ATTACHED_DEVICES[0]}"
else
    echo -e "${YELLOW}[!] Chưa có thiết bị nào kết nối qua USB hoặc ADB.${NC}"
    echo -e "${YELLOW}[*] Đang quét tìm thiết bị bật Wireless Debugging qua mDNS...${NC}"

    MDNS_OUTPUT=$("$ADB" mdns services 2>/dev/null || true)
    MDNS_TARGET=$(echo "$MDNS_OUTPUT" | grep -E '_adb-tls-connect\._tcp|_adb\._tcp' | head -n 1 | awk '{print $NF}' || true)

    if [ -n "$MDNS_TARGET" ]; then
        echo -e "${GREEN}[+] Tìm thấy thiết bị qua mDNS: ${MDNS_TARGET}${NC}"
        echo -e "${BLUE}[*] Đang kết nối thử tới ${MDNS_TARGET}...${NC}"
        "$ADB" connect "$MDNS_TARGET"
        TARGET_SERIAL="$MDNS_TARGET"
    else
        echo -e "\n${YELLOW}Không tự động quét được qua mDNS. Vui lòng nhập thủ công:${NC}"
        echo -e "Mở điện thoại: ${CYAN}Cài đặt -> Tùy chọn nhà phát triển -> Gỡ lỗi qua Wi-Fi${NC}"
        read -r -p "Nhập Địa chỉ IP & Cổng hiển thị trên máy (ví dụ: 192.168.1.50:39485): " MANUAL_ADDR
        if [ -z "$MANUAL_ADDR" ]; then
            echo -e "${RED}[ERROR] Không có địa chỉ kết nối. Huỷ bỏ.${NC}"
            exit 1
        fi
        "$ADB" connect "$MANUAL_ADDR"
        TARGET_SERIAL="$MANUAL_ADDR"
    fi
fi

# Chờ xác nhận thiết bị sẵn sàng
sleep 1
DEVICE_STATE=$("$ADB" -s "$TARGET_SERIAL" get-state 2>/dev/null || true)
if [ "$DEVICE_STATE" != "device" ]; then
    echo -e "${RED}[ERROR] Thiết bị $TARGET_SERIAL không ở trạng thái sẵn sàng (state: $DEVICE_STATE).${NC}"
    echo -e "${YELLOW}Vui lòng kiểm tra thông báo 'Cho phép gỡ lỗi USB/Wi-Fi' trên màn hình điện thoại.${NC}"
    exit 1
fi

DEVICE_IP=$(get_device_ip "$TARGET_SERIAL")
if [ -z "$DEVICE_IP" ]; then
    # Nếu TARGET_SERIAL có dạng IP:PORT
    if [[ "$TARGET_SERIAL" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+ ]]; then
        DEVICE_IP="${TARGET_SERIAL%%:*}"
    else
        echo -e "${RED}[ERROR] Không lấy được địa chỉ IP Wi-Fi của điện thoại.${NC}"
        exit 1
    fi
fi

echo -e "\n${GREEN}[✓] IP Wi-Fi của thiết bị: ${DEVICE_IP}${NC}"

# 3. Kích hoạt port cố định bằng adb tcpip
echo -e "${BLUE}[*] Đang chuyển adbd trên điện thoại sang port ${TARGET_PORT}...${NC}"
"$ADB" -s "$TARGET_SERIAL" tcpip "$TARGET_PORT"
sleep 2

# 4. Kết nối tới IP:TARGET_PORT
echo -e "${BLUE}[*] Đang kết nối tới ${DEVICE_IP}:${TARGET_PORT}...${NC}"
"$ADB" connect "${DEVICE_IP}:${TARGET_PORT}"

# 5. Nếu bật chế độ --root, ghi script boot vĩnh viễn
if [ "$ROOT_MODE" = true ]; then
    echo -e "\n${BLUE}[*] Đang cấu hình ghim port VĨNH VIỄN qua Root (Magisk / KernelSU / APatch)...${NC}"
    IS_ROOT=$("$ADB" -s "${DEVICE_IP}:${TARGET_PORT}" shell "su -c 'id -u'" 2>/dev/null | tr -d '\r\n' || true)
    if [ "$IS_ROOT" = "0" ]; then
        "$ADB" -s "${DEVICE_IP}:${TARGET_PORT}" shell "su -c '
            setprop service.adb.tcp.port $TARGET_PORT
            mkdir -p /data/adb/service.d
            cat << \"EOF\" > /data/adb/service.d/adb_tcpip.sh
#!/system/bin/sh
# Auto-generated by BlockAds adb_pin_port.sh
resetprop service.adb.tcp.port $TARGET_PORT || setprop service.adb.tcp.port $TARGET_PORT
stop adbd
start adbd
EOF
            chmod 755 /data/adb/service.d/adb_tcpip.sh
        '"
        echo -e "${GREEN}[✓] Đã tạo boot script tại /data/adb/service.d/adb_tcpip.sh!${NC}"
        echo -e "${GREEN}[✓] Kể từ giờ, mỗi khi khởi động lại điện thoại, port ${TARGET_PORT} sẽ tự động được mở!${NC}"
    else
        echo -e "${YELLOW}[!] Thiết bị không có quyền Root hoặc chưa cấp quyền su cho ADB Shell.${NC}"
        echo -e "${YELLOW}[!] Port ${TARGET_PORT} chỉ duy trì cho đến khi máy khởi động lại.${NC}"
    fi
fi

echo -e "\n${GREEN}====================================================${NC}"
echo -e "${GREEN}  ✓ THÀNH CÔNG: Đã ghim Wireless Debugging ở port ${TARGET_PORT}!${NC}"
echo -e "${GREEN}  ✓ Bạn có thể rút cáp USB (nếu đang cắm).${NC}"
echo -e "${GREEN}  ✓ Lệnh kết nối lần sau: adb connect ${DEVICE_IP}:${TARGET_PORT}${NC}"
echo -e "${GREEN}====================================================${NC}"
