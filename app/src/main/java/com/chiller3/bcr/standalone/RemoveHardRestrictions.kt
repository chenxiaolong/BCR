@file:SuppressLint(
    "BlockedPrivateApi",
    "DiscouragedPrivateApi",
    "PrivateApi",
    "SoonBlockedPrivateApi",
)

package com.chiller3.bcr.standalone

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.system.ErrnoException
import androidx.annotation.RequiresApi
import com.chiller3.bcr.BuildConfig
import kotlin.system.exitProcess

private object ActivityThreadProxy {
    private val CLS = Class.forName("android.app.ActivityThread")
    private val METHOD_GET_PACKAGE_MANAGER = CLS.getDeclaredMethod("getPackageManager")
    private val METHOD_GET_PERMISSION_MANAGER = CLS.getDeclaredMethod("getPermissionManager")

    fun getPackageManager(): PackageManagerProxy {
        val iface = METHOD_GET_PACKAGE_MANAGER.invoke(null)!!
        return PackageManagerProxy(iface)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun getPermissionManager(): PermissionManagerProxy {
        val iface = METHOD_GET_PERMISSION_MANAGER.invoke(null)!!
        return PermissionManagerProxy(iface)
    }
}

private class PackageManagerProxy(private val iface: Any) {
    companion object {
        private val CLS = Class.forName("android.content.pm.IPackageManager")
        private val METHOD_IS_PACKAGE_AVAILABLE = CLS.getDeclaredMethod(
            "isPackageAvailable", String::class.java, Int::class.java)
        // Android 10 only
        private val METHOD_GET_PERMISSION_FLAGS by lazy {
            CLS.getDeclaredMethod(
                "getPermissionFlags",
                String::class.java,
                String::class.java,
                Int::class.java,
            )
        }
        // Android 10 only
        private val METHOD_UPDATE_PERMISSION_FLAGS by lazy {
            CLS.getDeclaredMethod(
                "updatePermissionFlags",
                String::class.java,
                String::class.java,
                Int::class.java,
                Int::class.java,
                Int::class.java,
            )
        }

        private val WRAPPER_CLS = PackageManager::class.java
        val FLAG_PERMISSION_APPLY_RESTRICTION = WRAPPER_CLS.getDeclaredField(
            "FLAG_PERMISSION_APPLY_RESTRICTION").getInt(null)
        val FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT = WRAPPER_CLS.getDeclaredField(
            "FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT").getInt(null)
        val FLAG_PERMISSION_RESTRICTION_ANY_EXEMPT = WRAPPER_CLS.getDeclaredField(
            "FLAGS_PERMISSION_RESTRICTION_ANY_EXEMPT").getInt(null)
    }

    fun isPackageAvailable(packageName: String, userId: Int): Boolean {
        return METHOD_IS_PACKAGE_AVAILABLE.invoke(iface, packageName, userId) as Boolean
    }

    fun getPermissionFlags(permissionName: String, packageName: String, userId: Int): Int {
        return METHOD_GET_PERMISSION_FLAGS.invoke(iface, permissionName, packageName, userId) as Int
    }

    fun updatePermissionFlags(
        permissionName: String,
        packageName: String,
        flagMask: Int,
        flagValues: Int,
        userId: Int,
    ) {
        METHOD_UPDATE_PERMISSION_FLAGS.invoke(
            iface,
            permissionName,
            packageName,
            flagMask,
            flagValues,
            userId,
        )
    }

}

@RequiresApi(Build.VERSION_CODES.R)
private class PermissionManagerProxy(private val iface: Any) {
    companion object {
        private val CLS = Class.forName("android.permission.IPermissionManager")
        private val METHOD_GET_PERMISSION_FLAGS =
            CLS.getDeclaredMethod(
                "getPermissionFlags",
                String::class.java,
                String::class.java,
                Int::class.java,
            )
        private val METHOD_UPDATE_PERMISSION_FLAGS =
            CLS.getDeclaredMethod(
                "updatePermissionFlags",
                String::class.java,
                String::class.java,
                Int::class.java,
                Int::class.java,
                Boolean::class.java,
                Int::class.java,
            )
    }

    fun getPermissionFlags(packageName: String, permissionName: String, userId: Int): Int {
        return METHOD_GET_PERMISSION_FLAGS(iface, packageName, permissionName, userId) as Int
    }

    fun updatePermissionFlags(
        packageName: String,
        permissionName: String,
        flagMask: Int,
        flagValues: Int,
        checkAdjustPolicyFlagPermission: Boolean,
        userId: Int,
    ) {
        METHOD_UPDATE_PERMISSION_FLAGS.invoke(
            iface,
            packageName,
            permissionName,
            flagMask,
            flagValues,
            checkAdjustPolicyFlagPermission,
            userId,
        )
    }
}

private fun switchToSystemUid() {
    if (Process.myUid() != Process.SYSTEM_UID) {
        val setUid = Process::class.java.getDeclaredMethod("setUid", Int::class.java)
        val errno = setUid.invoke(null, Process.SYSTEM_UID) as Int

        if (errno != 0) {
            throw Exception("Failed to switch to SYSTEM (${Process.SYSTEM_UID}) user",
                ErrnoException("setuid", errno))
        }
        if (Process.myUid() != Process.SYSTEM_UID) {
            throw IllegalStateException("UID didn't actually change: " +
                "${Process.myUid()} != ${Process.SYSTEM_UID}")
        }
    }
}

@Suppress("SameParameterValue")
private fun removeRestriction(packageName: String, permission: String, userId: Int): Boolean {
    val packageManager = ActivityThreadProxy.getPackageManager()
    if (!packageManager.isPackageAvailable(packageName, userId)) {
        throw IllegalArgumentException("Package $packageName is not installed for user $userId")
    }

    val (getFlags, updateFlags) = if (Build.VERSION.SDK_INT in
        Build.VERSION_CODES.R..Build.VERSION_CODES.TIRAMISU) {
        val permissionManager = ActivityThreadProxy.getPermissionManager()

        Pair(
            { permissionManager.getPermissionFlags(packageName, permission, userId) },
            { mask: Int, set: Int ->
                permissionManager.updatePermissionFlags(
                    packageName, permission, mask, set, false, userId)
            },
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Pair(
            { packageManager.getPermissionFlags(permission, packageName, userId) },
            { mask: Int, set: Int ->
                packageManager.updatePermissionFlags(permission, packageName, mask, set, userId)
            },
        )
    } else {
        throw IllegalStateException("Not supported on SDK version ${Build.VERSION.SDK_INT}")
    }

    val oldFlags = getFlags()

    updateFlags(
        PackageManagerProxy.FLAG_PERMISSION_RESTRICTION_ANY_EXEMPT or
                PackageManagerProxy.FLAG_PERMISSION_APPLY_RESTRICTION,
        PackageManagerProxy.FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT,
    )

    val newFlags = getFlags()
    if (newFlags and PackageManagerProxy.FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT == 0) {
        throw IllegalStateException("RESTRICTION_SYSTEM_EXEMPT flag did not get added")
    }
    if (newFlags and PackageManagerProxy.FLAG_PERMISSION_APPLY_RESTRICTION != 0) {
        throw IllegalStateException("APPLY_RESTRICTION flag did not get removed")
    }

    return newFlags != oldFlags
}

fun mainInternal() {
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
        // Android 9 does not have FLAG_PERMISSION_APPLY_RESTRICTION
        System.err.println("Android 9 does not have hard-restricted permissions")
        return
    }

    switchToSystemUid()

    val packageManager = ActivityThreadProxy.getPackageManager()
    if (!packageManager.isPackageAvailable(BuildConfig.APPLICATION_ID, 0)) {
        System.err.println("""
            ---------------- NOTE ----------------
            Android 10+ marks the READ_CALL_LOG
            permission as being hard restricted.
            This makes it impossible to grant the
            (optional) permission, even from
            Android's settings. To remove this
            restriction for BCR only, reboot and
            reflash one more time. This procedure
            only needs to be done once and will
            persist across upgrades. This does not
            affect other apps and the changes go
            away when BCR is uninstalled.
            --------------------------------------
        """.trimIndent())
        exitProcess(2)
    }

    val changed = removeRestriction(BuildConfig.APPLICATION_ID, Manifest.permission.READ_CALL_LOG, 0)
    val suffix = "from ${BuildConfig.APPLICATION_ID} for ${Manifest.permission.READ_CALL_LOG}"

    if (changed) {
        println("Successfully removed hard restriction $suffix")
    } else {
        println("Hard restriction already removed $suffix")
    }
}

fun main() {
    try {
        mainInternal()
    } catch (e: Exception) {
        // Otherwise, exceptions go to the logcat
        e.printStackTrace()
        exitProcess(1)
    }
}