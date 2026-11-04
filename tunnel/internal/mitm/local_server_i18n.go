package mitm

import (
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
)

const AppLanguageFile = "app_language.txt"

// Official BlockAds app icon SVG (exact vector from res/drawable/ic_launcher_foreground.xml on #0A0A0F background).
const blockAdsLogoSVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 400" width="100%" height="100%">
  <rect width="400" height="400" rx="90" fill="#0A0A0F"/>
  <rect x="6" y="6" width="388" height="388" rx="84" fill="none" stroke="#00E676" stroke-opacity="0.22" stroke-width="6"/>
  <path fill="#00E676" d="M200,379.17C198.66,379.17 197.32,378.95 196.05,378.52C169.57,369.7 37.5,318.72 37.5,181.48V83.33C37.5,80.45 38.49,77.66 40.31,75.42C42.14,73.19 44.68,71.66 47.5,71.08C98.68,62.81 148.2,46.37 194.17,22.4C195.97,21.5 197.96,21.01 199.97,20.97C201.99,20.93 203.99,21.34 205.83,22.17C251.66,46.4 301.17,62.93 352.37,71.08C355.19,71.66 357.73,73.19 359.55,75.42C361.38,77.66 362.37,80.45 362.37,83.33V181.48C362.37,318.72 230.3,369.7 203.82,378.52C202.59,378.93 201.3,379.15 200,379.17ZM62.5,93.53V181.48C62.5,295.88 169.47,342.48 200,353.43C230.53,342.48 337.5,295.88 337.5,181.48V93.53C289.68,85.12 243.41,69.53 200.25,47.3C156.9,69.49 110.46,85.08 62.5,93.53ZM192.37,242.17L259.03,175.5C261.31,173.14 262.57,169.98 262.54,166.71C262.51,163.43 261.19,160.29 258.87,157.98C256.55,155.66 253.42,154.35 250.14,154.32C246.86,154.29 243.71,155.55 241.35,157.83L183.53,215.65L159.03,191.17C156.68,188.89 153.52,187.63 150.24,187.65C146.96,187.68 143.83,188.99 141.51,191.31C139.19,193.63 137.88,196.76 137.85,200.04C137.82,203.32 139.07,206.48 141.35,208.83L174.68,242.17C175.84,243.33 177.22,244.25 178.74,244.88C180.26,245.51 181.88,245.83 183.52,245.83C185.17,245.83 186.79,245.51 188.31,244.88C189.83,244.25 191.21,243.33 192.37,242.17Z"/>
</svg>`

type localPageStrings struct {
	Lang        string
	Dir         string
	BadgeActive string
	Subtitle    string
	Description string
	DownloadBtn string
	GuideTitle  string
	Step1       string
	Step2       string
	FooterNote  string
}

var (
	localLangMu sync.RWMutex
	localLang   string
)

// SetLocalAssetLanguage sets the preferred UI language for local.pwhs.app.
// Empty string or "system" falls back to the language file in certDir or English ("en").
func SetLocalAssetLanguage(lang string) {
	localLangMu.Lock()
	localLang = strings.TrimSpace(lang)
	localLangMu.Unlock()
}

func resolveLocalAssetLang(req *http.Request) string {
	if req != nil && req.URL != nil {
		if q := strings.TrimSpace(req.URL.Query().Get("lang")); q != "" {
			return normalizeLangCode(q)
		}
	}

	localLangMu.RLock()
	lang := localLang
	localLangMu.RUnlock()

	if lang == "" || strings.EqualFold(lang, "system") {
		certMu.RLock()
		cm := certManagerInstance
		certMu.RUnlock()
		if cm != nil && cm.certDir != "" {
			if data, err := os.ReadFile(filepath.Join(cm.certDir, AppLanguageFile)); err == nil {
				fileLang := strings.TrimSpace(string(data))
				if fileLang != "" && !strings.EqualFold(fileLang, "system") {
					lang = fileLang
				}
			}
		}
	}

	return normalizeLangCode(lang)
}

func normalizeLangCode(raw string) string {
	code := strings.ToLower(strings.TrimSpace(raw))
	if code == "" || code == "system" {
		return "en"
	}
	code = strings.ReplaceAll(code, "_", "-")
	switch {
	case strings.HasPrefix(code, "vi"):
		return "vi"
	case strings.HasPrefix(code, "ja"):
		return "ja"
	case strings.HasPrefix(code, "ko"):
		return "ko"
	case strings.HasPrefix(code, "zh"):
		return "zh"
	case strings.HasPrefix(code, "th"):
		return "th"
	case strings.HasPrefix(code, "es"):
		return "es"
	case strings.HasPrefix(code, "ru"):
		return "ru"
	case strings.HasPrefix(code, "it"):
		return "it"
	case strings.HasPrefix(code, "ar"):
		return "ar"
	case strings.HasPrefix(code, "tr"):
		return "tr"
	case strings.HasPrefix(code, "pl"):
		return "pl"
	case strings.HasPrefix(code, "in"), strings.HasPrefix(code, "id"):
		return "id"
	case strings.HasPrefix(code, "pt"):
		return "pt"
	case strings.HasPrefix(code, "uk"):
		return "uk"
	case strings.HasPrefix(code, "de"):
		return "de"
	case strings.HasPrefix(code, "cs"):
		return "cs"
	case strings.HasPrefix(code, "iw"), strings.HasPrefix(code, "he"):
		return "he"
	case strings.HasPrefix(code, "fr"):
		return "fr"
	case strings.HasPrefix(code, "kk"):
		return "kk"
	default:
		return "en"
	}
}

var localTranslations = map[string]localPageStrings{
	"en": {
		Lang:        "en",
		Dir:         "ltr",
		BadgeActive: "Active Protection",
		Subtitle:    "Official On-Device HTTPS & DNS Shield",
		Description: "HTTPS Filtering & DNS Shield are running locally on your device. 100% of traffic is filtered on-device with zero telemetry and no external servers.",
		DownloadBtn: "Download Root CA Certificate (BlockAds-CA.crt)",
		GuideTitle:  "Installation Guide:",
		Step1:       "1. Download the Root CA certificate to your device.",
		Step2:       "2. Open Settings → Security & privacy → More security settings → Encryption & credentials → Install a certificate → CA certificate.",
		FooterNote:  "100% On-Device • Zero Telemetry • Open Source",
	},
	"vi": {
		Lang:        "vi",
		Dir:         "ltr",
		BadgeActive: "Đang hoạt động",
		Subtitle:    "Bộ lọc HTTPS & DNS cục bộ chính thức",
		Description: "HTTPS Filtering & DNS Shield đang chạy cục bộ trên thiết bị của bạn. 100% dữ liệu được lọc nội bộ trên máy, không telemetry, không gửi dữ liệu ra máy chủ bên ngoài.",
		DownloadBtn: "Tải Chứng Chỉ Root CA (BlockAds-CA.crt)",
		GuideTitle:  "Hướng dẫn cài đặt:",
		Step1:       "1. Tải chứng chỉ Root CA về thiết bị.",
		Step2:       "2. Vào Cài đặt → Bảo mật & quyền riêng tư → Thông tin xác thực → Cài đặt chứng chỉ → Chứng chỉ CA.",
		FooterNote:  "100% Trên thiết bị • Không Telemetry • Mã nguồn mở",
	},
	"ja": {
		Lang:        "ja",
		Dir:         "ltr",
		BadgeActive: "保護有効",
		Subtitle:    "公式オンデバイス HTTPS & DNS シールド",
		Description: "HTTPS フィルタリングと DNS シールドはデバイス上でローカルに動作しています。通信は 100% 端末内で処理され、外部へのデータ送信はありません。",
		DownloadBtn: "ルート CA 証明書をダウンロード (BlockAds-CA.crt)",
		GuideTitle:  "インストール手順:",
		Step1:       "1. 証明書ファイルをデバイスにダウンロードします。",
		Step2:       "2. 設定 → セキュリティとプライバシー → 暗号化と認証情報 → 証明書のインストール → CA 証明書 を開きます。",
		FooterNote:  "100% オンデバイス • テレメトリなし • オープンソース",
	},
	"ko": {
		Lang:        "ko",
		Dir:         "ltr",
		BadgeActive: "보호 활성화됨",
		Subtitle:    "공식 온디바이스 HTTPS & DNS 보호",
		Description: "HTTPS 필터링 및 DNS 보호가 기기 내부에서 로컬로 실행 중입니다. 모든 트래픽은 100% 기기 내에서 처리되며 외부로 전송되지 않습니다.",
		DownloadBtn: "루트 CA 인증서 다운로드 (BlockAds-CA.crt)",
		GuideTitle:  "설치 안내:",
		Step1:       "1. 루트 CA 인증서를 기기에 다운로드합니다.",
		Step2:       "2. 설정 → 보안 및 개인정보 보호 → 기타 보안 설정 → 저장공간에서 인증서 설치 → CA 인증서로 이동합니다.",
		FooterNote:  "100% 온디바이스 • 텔레메트리 없음 • 오픈소스",
	},
	"zh": {
		Lang:        "zh",
		Dir:         "ltr",
		BadgeActive: "保护运行中",
		Subtitle:    "官方本地 HTTPS 与 DNS 防护引擎",
		Description: "HTTPS 过滤与 DNS 防护正在您的设备本地运行。100% 流量在设备端过滤，零遥测，不向任何外部服务器发送数据。",
		DownloadBtn: "下载根证书 (BlockAds-CA.crt)",
		GuideTitle:  "安装说明：",
		Step1:       "1. 将根证书下载到您的设备。",
		Step2:       "2. 前往 设置 → 安全与隐私 → 更多安全设置 → 加密与凭据 → 安装证书 → CA 证书。",
		FooterNote:  "100% 本地处理 • 零遥测 • 开源透明",
	},
	"th": {
		Lang:        "th",
		Dir:         "ltr",
		BadgeActive: "ทำงานอยู่",
		Subtitle:    "ระบบป้องกัน HTTPS & DNS บนอุปกรณ์อย่างเป็นทางการ",
		Description: "การกรอง HTTPS และ DNS Shield กำลังทำงานภายในเครื่องของคุณ กรองข้อมูล 100% บนอุปกรณ์โดยไม่ส่งข้อมูลออกภายนอก",
		DownloadBtn: "ดาวน์โหลดใบรับรอง Root CA (BlockAds-CA.crt)",
		GuideTitle:  "วิธีติดตั้ง:",
		Step1:       "1. ดาวน์โหลดใบรับรองลงในเครื่อง",
		Step2:       "2. ไปที่ การตั้งค่า → ความปลอดภัยและความเป็นส่วนตัว → การตั้งค่าความปลอดภัยเพิ่มเติม → ติดตั้งใบรับรอง → ใบรับรอง CA",
		FooterNote:  "100% บนอุปกรณ์ • ไม่มีการเก็บข้อมูล • โอเพนซอร์ส",
	},
	"es": {
		Lang:        "es",
		Dir:         "ltr",
		BadgeActive: "Protección activa",
		Subtitle:    "Escudo oficial local de HTTPS y DNS",
		Description: "El filtrado HTTPS y el escudo DNS se ejecutan localmente en tu dispositivo. El 100% del tráfico se filtra en el dispositivo sin telemetría.",
		DownloadBtn: "Descargar certificado Root CA (BlockAds-CA.crt)",
		GuideTitle:  "Guía de instalación:",
		Step1:       "1. Descarga el certificado Root CA en tu dispositivo.",
		Step2:       "2. Ve a Ajustes → Seguridad y privacidad → Cifrado y credenciales → Instalar un certificado → Certificado CA.",
		FooterNote:  "100% En el dispositivo • Cero telemetría • Código abierto",
	},
	"ru": {
		Lang:        "ru",
		Dir:         "ltr",
		BadgeActive: "Защита активна",
		Subtitle:    "Официальный локальный HTTPS и DNS экран",
		Description: "HTTPS-фильтрация и DNS-защита работают локально на вашем устройстве. 100% трафика фильтруется без телеметрии и внешних серверов.",
		DownloadBtn: "Скачать корневой сертификат CA (BlockAds-CA.crt)",
		GuideTitle:  "Инструкция по установке:",
		Step1:       "1. Скачайте сертификат Root CA на устройство.",
		Step2:       "2. Откройте Настройки → Безопасность → Шифрование и учетные данные → Установка сертификата → Сертификат ЦС.",
		FooterNote:  "100% На устройстве • Без телеметрии • Открытый код",
	},
	"it": {
		Lang:        "it",
		Dir:         "ltr",
		BadgeActive: "Protezione attiva",
		Subtitle:    "Scudo HTTPS & DNS locale ufficiale",
		Description: "Il filtro HTTPS e lo scudo DNS sono attivi localmente sul dispositivo. Il 100% del traffico è filtrato sul dispositivo senza telemetria.",
		DownloadBtn: "Scarica certificato Root CA (BlockAds-CA.crt)",
		GuideTitle:  "Guida all'installazione:",
		Step1:       "1. Scarica il certificato Root CA sul dispositivo.",
		Step2:       "2. Vai in Impostazioni → Sicurezza e privacy → Crittografia e credenziali → Installa un certificato → Certificato CA.",
		FooterNote:  "100% Sul dispositivo • Zero telemetria • Open Source",
	},
	"ar": {
		Lang:        "ar",
		Dir:         "rtl",
		BadgeActive: "الحماية نشطة",
		Subtitle:    "درع HTTPS و DNS المحلي الرسمي",
		Description: "تعمل تصفية HTTPS ودرع DNS محلياً على جهازك. تتم تصفية 100% من البيانات داخل الجهاز بدون أي تتبع أو خوادم خارجية.",
		DownloadBtn: "تنزيل شهادة الجذر (BlockAds-CA.crt)",
		GuideTitle:  "دليل التثبيت:",
		Step1:       "1. قم بتنزيل شهادة Root CA على جهازك.",
		Step2:       "2. انتقل إلى الإعدادات ← الأمان والخصوصية ← التشفير وبيانات الاعتماد ← تثبيت شهادة ← شهادة CA.",
		FooterNote:  "100% على الجهاز • بدون تتبع • مفتوح المصدر",
	},
	"tr": {
		Lang:        "tr",
		Dir:         "ltr",
		BadgeActive: "Koruma Aktif",
		Subtitle:    "Resmi Cihaz İçi HTTPS ve DNS Kalkanı",
		Description: "HTTPS Filtreleme ve DNS Kalkanı cihazınızda yerel olarak çalışıyor. Verilerin %100'ü telemetri olmadan cihaz içinde filtrelenir.",
		DownloadBtn: "Kök CA Sertifikasını İndir (BlockAds-CA.crt)",
		GuideTitle:  "Kurulum Rehberi:",
		Step1:       "1. Kök CA sertifikasını cihazınıza indirin.",
		Step2:       "2. Ayarlar → Güvenlik ve gizlilik → Şifreleme ve kimlik bilgileri → Sertifika yükle → CA sertifikası yolunu izleyin.",
		FooterNote:  "%100 Cihaz İçi • Sıfır Telemetri • Açık Kaynak",
	},
	"pl": {
		Lang:        "pl",
		Dir:         "ltr",
		BadgeActive: "Ochrona aktywna",
		Subtitle:    "Oficjalna lokalna tarcza HTTPS i DNS",
		Description: "Filtrowanie HTTPS i tarcza DNS działają lokalnie na urządzeniu. 100% ruchu jest filtrowane na urządzeniu bez telemetrii.",
		DownloadBtn: "Pobierz certyfikat Root CA (BlockAds-CA.crt)",
		GuideTitle:  "Instrukcja instalacji:",
		Step1:       "1. Pobierz certyfikat Root CA na urządzenie.",
		Step2:       "2. Otwórz Ustawienia → Bezpieczeństwo i prywatność → Szyfrowanie i dane logowania → Zainstaluj certyfikat → Certyfikat CA.",
		FooterNote:  "100% Na urządzeniu • Zero telemetrii • Open Source",
	},
	"id": {
		Lang:        "id",
		Dir:         "ltr",
		BadgeActive: "Perlindungan Aktif",
		Subtitle:    "Perisai HTTPS & DNS Lokal Resmi",
		Description: "Pemfilteran HTTPS & Perisai DNS berjalan secara lokal di perangkat Anda. 100% lalu lintas difilter di perangkat tanpa telemetri.",
		DownloadBtn: "Unduh Sertifikat Root CA (BlockAds-CA.crt)",
		GuideTitle:  "Panduan Instalasi:",
		Step1:       "1. Unduh sertifikat Root CA ke perangkat Anda.",
		Step2:       "2. Buka Pengaturan → Keamanan & privasi → Enkripsi & kredensial → Instal sertifikat → Sertifikat CA.",
		FooterNote:  "100% Di Perangkat • Tanpa Telemetri • Sumber Terbuka",
	},
	"pt": {
		Lang:        "pt",
		Dir:         "ltr",
		BadgeActive: "Proteção Ativa",
		Subtitle:    "Escudo Oficial Local de HTTPS e DNS",
		Description: "A filtragem HTTPS e o escudo DNS estão rodando localmente no seu dispositivo. 100% do tráfego é filtrado no aparelho sem telemetria.",
		DownloadBtn: "Baixar Certificado Root CA (BlockAds-CA.crt)",
		GuideTitle:  "Guia de Instalação:",
		Step1:       "1. Baixe o certificado Root CA no seu dispositivo.",
		Step2:       "2. Acesse Configurações → Segurança e privacidade → Criptografia e credenciais → Instalar um certificado → Certificado CA.",
		FooterNote:  "100% No Dispositivo • Zero Telemetria • Código Aberto",
	},
	"uk": {
		Lang:        "uk",
		Dir:         "ltr",
		BadgeActive: "Захист активний",
		Subtitle:    "Офіційний локальний HTTPS та DNS щит",
		Description: "Фільтрація HTTPS та DNS-захист працюють локально на вашому пристрої. 100% трафіку фільтрується без телеметрії.",
		DownloadBtn: "Завантажити сертифікат Root CA (BlockAds-CA.crt)",
		GuideTitle:  "Інструкція зі встановлення:",
		Step1:       "1. Завантажте сертифікат Root CA на пристрій.",
		Step2:       "2. Відкрийте Налаштування → Безпека та конфіденційність → Шифрування та облікові дані → Встановити сертифікат → Сертифікат ЦС.",
		FooterNote:  "100% На пристрої • Без телеметрії • Відкритий код",
	},
	"de": {
		Lang:        "de",
		Dir:         "ltr",
		BadgeActive: "Schutz aktiv",
		Subtitle:    "Offizieller lokaler HTTPS- & DNS-Schutzschild",
		Description: "HTTPS-Filterung & DNS-Schutz laufen lokal auf Ihrem Gerät. 100% des Datenverkehrs wird auf dem Gerät ohne Telemetrie gefiltert.",
		DownloadBtn: "Root-CA-Zertifikat herunterladen (BlockAds-CA.crt)",
		GuideTitle:  "Installationsanleitung:",
		Step1:       "1. Laden Sie das Root-CA-Zertifikat auf Ihr Gerät herunter.",
		Step2:       "2. Öffnen Sie Einstellungen → Sicherheit & Datenschutz → Verschlüsselung & Anmeldedaten → Zertifikat installieren → CA-Zertifikat.",
		FooterNote:  "100% Auf dem Gerät • Keine Telemetrie • Open Source",
	},
	"cs": {
		Lang:        "cs",
		Dir:         "ltr",
		BadgeActive: "Ochrana aktivní",
		Subtitle:    "Oficiální lokální štít HTTPS a DNS",
		Description: "Filtrování HTTPS a štít DNS běží lokálně ve vašem zařízení. 100 % provozu je filtrováno přímo v zařízení bez telemetrie.",
		DownloadBtn: "Stáhnout certifikát Root CA (BlockAds-CA.crt)",
		GuideTitle:  "Návod k instalaci:",
		Step1:       "1. Stáhněte si certifikát Root CA do zařízení.",
		Step2:       "2. Přejděte do Nastavení → Zabezpečení a soukromí → Šifrování a přihlašovací údaje → Nainstalovat certifikát → Certifikát CA.",
		FooterNote:  "100% V zařízení • Nulová telemetrie • Open Source",
	},
	"he": {
		Lang:        "he",
		Dir:         "rtl",
		BadgeActive: "הגנה פעילה",
		Subtitle:    "מגן HTTPS ו-DNS מקומי רשמי",
		Description: "סינון HTTPS ומגן DNS פועלים באופן מקומי במכשיר שלך. 100% מהתעבורה מסוננת על המכשיר ללא טלמטריה.",
		DownloadBtn: "הורד תעודת שורש (BlockAds-CA.crt)",
		GuideTitle:  "מדריך התקנה:",
		Step1:       "1. הורד את תעודת ה-Root CA למכשיר שלך.",
		Step2:       "2. עבור אל הגדרות ← אבטחה ופרטיות ← הצפנה ואישורים ← התקנת אישור ← אישור CA.",
		FooterNote:  "100% על המכשיר • אפס טלמטריה • קוד פתוח",
	},
	"fr": {
		Lang:        "fr",
		Dir:         "ltr",
		BadgeActive: "Protection active",
		Subtitle:    "Bouclier HTTPS & DNS local officiel",
		Description: "Le filtrage HTTPS et le bouclier DNS fonctionnent localement sur votre appareil. 100 % du trafic est filtré sur l'appareil sans télémétrie.",
		DownloadBtn: "Télécharger le certificat Root CA (BlockAds-CA.crt)",
		GuideTitle:  "Guide d'installation :",
		Step1:       "1. Téléchargez le certificat Root CA sur votre appareil.",
		Step2:       "2. Allez dans Paramètres → Sécurité et confidentialité → Chiffrement et identifiants → Installer un certificat → Certificat CA.",
		FooterNote:  "100 % Sur l'appareil • Zéro télémétrie • Open Source",
	},
	"kk": {
		Lang:        "kk",
		Dir:         "ltr",
		BadgeActive: "Қорғаныс белсенді",
		Subtitle:    "Ресми жергілікті HTTPS және DNS қалқаны",
		Description: "HTTPS сүзгілеу және DNS қалқаны құрылғыңызда жергілікті түрде жұмыс істейді. Трафиктің 100%-ы телеметриясыз сүзіледі.",
		DownloadBtn: "Root CA сертификатын жүктеу (BlockAds-CA.crt)",
		GuideTitle:  "Орнату нұсқаулығы:",
		Step1:       "1. Root CA сертификатын құрылғыға жүктеپ алыңыз.",
		Step2:       "2. Баптаулар → Қауіпсіздік және құпиялылық → Шифрлау және тіркелгі деректері → Сертификат орнату → CA сертификаты тармағына өтіңіз.",
		FooterNote:  "100% Құрылғыда • Телеметрия жоқ • Ашық бастапқы код",
	},
}
