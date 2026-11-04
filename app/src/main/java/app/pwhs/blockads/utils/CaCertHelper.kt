package app.pwhs.blockads.utils

import android.content.Intent
import android.os.Build
import timber.log.Timber
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Shared helper for Root CA certificate verification and system settings intent.
 */
object CaCertHelper {

    /**
     * Checks if the Root CA represented by [caPem] is installed in the Android trust store.
     */
    fun checkCertInTrustStore(caPem: String?): Boolean {
        if (caPem.isNullOrEmpty()) return false
        return try {
            val certFactory = CertificateFactory.getInstance("X.509")
            val ourCert = certFactory.generateCertificate(caPem.byteInputStream()) as X509Certificate
            val ourEncoded = ourCert.encoded

            val ks = KeyStore.getInstance("AndroidCAStore")
            ks.load(null)

            for (alias in ks.aliases()) {
                val cert = ks.getCertificate(alias) as? X509Certificate ?: continue
                if (cert.encoded.contentEquals(ourEncoded)) {
                    Timber.d("Cert verification: found matching CA in trust store (alias=$alias)")
                    return true
                }
            }

            false
        } catch (e: Exception) {
            Timber.e(e, "Cert verification failed")
            false
        }
    }

    /**
     * Creates an Intent to open system security settings for certificate installation.
     */
    fun createSecuritySettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent("android.settings.SECURITY_SETTINGS")
        } else {
            Intent("android.credentials.INSTALL").apply {
                type = "application/x-x509-ca-cert"
            }
        }
    }
}
