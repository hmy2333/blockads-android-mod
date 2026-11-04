package app.pwhs.blockads.utils

import android.content.Context
import android.os.Build
import app.pwhs.blockads.R
import java.util.Locale

/**
 * Detects device manufacturer and Android version to provide accurate,
 * device-specific instructions for installing CA certificates.
 */
object DeviceManager {

    enum class Manufacturer {
        SAMSUNG,
        GOOGLE,
        XIAOMI,
        OPPO_REALME,
        VIVO,
        HUAWEI_HONOR,
        MOTOROLA,
        GENERIC
    }

    val currentManufacturer: Manufacturer by lazy { detectManufacturer(Build.MANUFACTURER, Build.BRAND) }

    internal fun detectManufacturer(manufacturer: String, brand: String): Manufacturer {
        val m = manufacturer.lowercase(Locale.US)
        val b = brand.lowercase(Locale.US)
        return when {
            m.contains("samsung") || b.contains("samsung") -> Manufacturer.SAMSUNG
            m.contains("google") || b.contains("google") -> Manufacturer.GOOGLE
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> Manufacturer.XIAOMI
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> Manufacturer.OPPO_REALME
            m.contains("vivo") || m.contains("iqoo") -> Manufacturer.VIVO
            m.contains("huawei") || m.contains("honor") -> Manufacturer.HUAWEI_HONOR
            m.contains("motorola") || m.contains("moto") -> Manufacturer.MOTOROLA
            else -> Manufacturer.GENERIC
        }
    }

    val currentBrandName: String
        get() = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

    /**
     * Returns a list of steps tailored to the current device and Android version.
     */
    fun getInstallSteps(context: Context): List<String> =
        installStepsFor(currentManufacturer, Build.VERSION.SDK_INT, context)

    fun getInstallStepResIds(): List<Int> =
        installStepResIdsFor(currentManufacturer, Build.VERSION.SDK_INT)

    internal fun installStepsFor(manufacturer: Manufacturer, sdk: Int, context: Context): List<String> =
        installStepResIdsFor(manufacturer, sdk).map { context.getString(it) }

    internal fun installStepResIdsFor(manufacturer: Manufacturer, sdk: Int): List<Int> {
        return when (manufacturer) {
            Manufacturer.SAMSUNG -> when {
                sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
                    R.string.device_step_open_settings,
                    R.string.device_step_samsung_security_privacy,
                    R.string.device_step_samsung_more_security,
                    R.string.device_step_samsung_install_from_storage,
                    R.string.device_step_samsung_ca_cert_confirm,
                    R.string.device_step_select_downloaded_cert
                )
                sdk >= Build.VERSION_CODES.S -> listOf(
                    R.string.device_step_open_settings,
                    R.string.device_step_samsung_biometrics,
                    R.string.device_step_samsung_other_security,
                    R.string.device_step_samsung_install_from_storage,
                    R.string.device_step_samsung_ca_cert_warn,
                    R.string.device_step_select_downloaded_cert
                )
                else -> listOf(
                    R.string.device_step_open_settings,
                    R.string.device_step_samsung_biometrics,
                    R.string.device_step_samsung_other_sec_install_storage,
                    R.string.device_step_select_ca_cert,
                    R.string.device_step_select_downloaded_cert
                )
            }

            Manufacturer.GOOGLE, Manufacturer.MOTOROLA -> when {
                sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
                    R.string.device_step_open_settings,
                    R.string.device_step_google_security_privacy,
                    R.string.device_step_google_more_security,
                    R.string.device_step_google_encryption_credentials,
                    R.string.device_step_google_install_ca_cert,
                    R.string.device_step_google_confirm_and_select
                )
                else -> listOf(
                    R.string.device_step_open_settings,
                    R.string.device_step_google_security_encryption,
                    R.string.device_step_google_install_certificate,
                    R.string.device_step_google_ca_cert_confirm,
                    R.string.device_step_select_downloaded_cert
                )
            }

            Manufacturer.XIAOMI -> listOf(
                R.string.device_step_xiaomi_open_settings,
                R.string.device_step_xiaomi_passwords_security,
                R.string.device_step_xiaomi_privacy_encryption,
                R.string.device_step_xiaomi_install_ca,
                R.string.device_step_xiaomi_confirm_select
            )

            Manufacturer.OPPO_REALME -> listOf(
                R.string.device_step_xiaomi_open_settings,
                R.string.device_step_oppo_security_more,
                R.string.device_step_oppo_credential_storage,
                R.string.device_step_oppo_install_ca,
                R.string.device_step_select_downloaded_cert
            )

            Manufacturer.VIVO -> listOf(
                R.string.device_step_xiaomi_open_settings,
                R.string.device_step_vivo_security_encryption,
                R.string.device_step_vivo_install_ca,
                R.string.device_step_select_downloaded_cert
            )

            else -> listOf(
                R.string.device_step_open_settings,
                R.string.device_step_generic_search_certificate,
                R.string.device_step_generic_choose_ca,
                R.string.device_step_generic_confirm,
                R.string.device_step_generic_select_downloads
            )
        }
    }
}
