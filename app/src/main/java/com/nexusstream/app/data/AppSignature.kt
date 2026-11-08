package com.nexusstream.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** The SHA-1 fingerprint of the certificate this app is signed with, as Google's consoles write it. */
object AppSignature {
    fun sha1(context: Context): String = runCatching {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            @Suppress("PackageManagerGetSignatures")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION", "PackageManagerGetSignatures")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val first = signatures?.firstOrNull() ?: return@runCatching ""
        hex(MessageDigest.getInstance("SHA-1").digest(first.toByteArray()))
    }.getOrDefault("")

    /** "AB:CD:EF" upper-case hex pairs. */
    internal fun hex(bytes: ByteArray): String = bytes.joinToString(":") { "%02X".format(it) }
}
