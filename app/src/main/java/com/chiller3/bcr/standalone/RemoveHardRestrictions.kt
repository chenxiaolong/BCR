@file:SuppressLint(
    "BlockedPrivateApi",
    "DiscouragedPrivateApi",
    "PrivateApi",
    "SoonBlockedPrivateApi",
)
@file:Suppress("SameParameterValue")

package com.chiller3.bcr.standalone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.os.Process
import android.system.ErrnoException
import androidx.annotation.RequiresApi
import com.chiller3.bcr.BuildConfig
import kotlin.system.exitProcess

private const val GET_SERVICE_ATTEMPTS = 30
private const val IS_USER_UNLOCKED_ATTEMPTS = 3600

private object ServiceManagerProxy {
    private val CLS = Class.forName("android.os.ServiceManager")

    private val METHOD_GET_SERVICE =
        CLS.getDeclaredMethod("getService", String::class.java)

    fun getService(name: String): IBinder? {
        return METHOD_GET_SERVICE.invoke(null, name) as IBinder?
    }
}

private fun getService(interfaceClass: Class<*>, serviceName: String): IInterface {
    val stubCls = Class.forName("${interfaceClass.canonicalName}\$Stub")
    val stubMethodAsInterface = stubCls.getDeclaredMethod("asInterface", IBinder::class.java)

    // ServiceManager.waitForService() tries to start the service, which we want to avoid to be 100%
    // sure we're not disrupting the boot flow. It also wasn't introduced until Android 11+.
    for (attempt in 1..GET_SERVICE_ATTEMPTS) {
        val iBinder = ServiceManagerProxy.getService(serviceName)
        if (iBinder != null) {
            return stubMethodAsInterface.invoke(null, iBinder) as IInterface
        }

        if (attempt < GET_SERVICE_ATTEMPTS) {
            Thread.sleep(1000)
        }
    }

    throw IllegalStateException(
        "Service $serviceName not found after $GET_SERVICE_ATTEMPTS attempts")
}

private class PackageManagerProxy private constructor(private val iFace: IInterface) {
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

        val instance by lazy {
            PackageManagerProxy(getService(CLS, "package"))
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
        return METHOD_IS_PACKAGE_AVAILABLE.invoke(iFace, packageName, userId) as Boolean
    }

    fun getPermissionFlags(permissionName: String, packageName: String, userId: Int): Int {
        return METHOD_GET_PERMISSION_FLAGS.invoke(iFace, permissionName, packageName, userId) as Int
    }

    fun updatePermissionFlags(
        permissionName: String,
        packageName: String,
        flagMask: Int,
        flagValues: Int,
        userId: Int,
    ) {
        METHOD_UPDATE_PERMISSION_FLAGS.invoke(
            iFace,
            permissionName,
            packageName,
            flagMask,
            flagValues,
            userId,
        )
    }

}

@RequiresApi(Build.VERSION_CODES.R)
private class PermissionManagerProxy private constructor(private val iFace: IInterface) {
    companion object {
        private val CLS = Class.forName("android.permission.IPermissionManager")
        private val METHOD_GET_PERMISSION_FLAGS_14_QPR2 by lazy {
            CLS.getDeclaredMethod(
                "getPermissionFlags",
                String::class.java,
                String::class.java,
                Int::class.java,
                Int::class.java,
            )
        }
        private val METHOD_GET_PERMISSION_FLAGS by lazy {
            CLS.getDeclaredMethod(
                "getPermissionFlags",
                String::class.java,
                String::class.java,
                Int::class.java,
            )
        }
        private val METHOD_UPDATE_PERMISSION_FLAGS_14_QPR2 by lazy {
            CLS.getDeclaredMethod(
                "updatePermissionFlags",
                String::class.java,
                String::class.java,
                Int::class.java,
                Int::class.java,
                Boolean::class.java,
                Int::class.java,
                Int::class.java,
            )
        }
        private val METHOD_UPDATE_PERMISSION_FLAGS by lazy {
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

        val instance by lazy {
            PermissionManagerProxy(getService(CLS, "permissionmgr"))
        }
    }

    fun getPermissionFlags(packageName: String, permissionName: String, userId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                return METHOD_GET_PERMISSION_FLAGS_14_QPR2(
                    iFace,
                    packageName,
                    permissionName,
                    Context.DEVICE_ID_DEFAULT,
                    userId
                ) as Int
            }
        } catch (e: NoSuchMethodException) {
            // 14 QPR2 has a breaking change in the interface, but no version bump.
        }

        return METHOD_GET_PERMISSION_FLAGS(iFace, packageName, permissionName, userId) as Int
    }

    fun updatePermissionFlags(
        packageName: String,
        permissionName: String,
        flagMask: Int,
        flagValues: Int,
        checkAdjustPolicyFlagPermission: Boolean,
        userId: Int,
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                METHOD_UPDATE_PERMISSION_FLAGS_14_QPR2.invoke(
                    iFace,
                    packageName,
                    permissionName,
                    flagMask,
                    flagValues,
                    checkAdjustPolicyFlagPermission,
                    Context.DEVICE_ID_DEFAULT,
                    userId,
                )

                return
            }
        } catch (e: NoSuchMethodException) {
            // 14 QPR2 has a breaking change in the interface, but no version bump.
        }

        METHOD_UPDATE_PERMISSION_FLAGS.invoke(
            iFace,
            packageName,
            permissionName,
            flagMask,
            flagValues,
            checkAdjustPolicyFlagPermission,
            userId,
        )
    }
}

private class UserManagerProxy private constructor(private val iFace: IInterface) {
    companion object {
        private val CLS = Class.forName("android.os.IUserManager")
        private val METHOD_IS_USER_UNLOCKING_OR_UNLOCKED =
            CLS.getDeclaredMethod("isUserUnlockingOrUnlocked", Int::class.java)

        val instance by lazy {
            UserManagerProxy(getService(CLS, Context.USER_SERVICE))
        }
    }

    fun isUserUnlockingOrUnlocked(userId: Int): Boolean {
        return METHOD_IS_USER_UNLOCKING_OR_UNLOCKED.invoke(iFace, userId) as Boolean
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

private fun removeRestriction(packageName: String, permission: String, userId: Int): Boolean {
    val packageManager = PackageManagerProxy.instance
    if (!packageManager.isPackageAvailable(packageName, userId)) {
        throw IllegalArgumentException("Package $packageName is not installed for user $userId")
    }

    val (getFlags, updateFlags) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val permissionManager = PermissionManagerProxy.instance

        Pair(
            { permissionManager.getPermissionFlags(packageName, permission, userId) },
            { mask: Int, set: Int ->
                permissionManager.updatePermissionFlags(
                    packageName, permission, mask, set, false, userId)
            },
        )
    } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
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

private fun waitForLogin(userId: Int) {
    val userManager = UserManagerProxy.instance

    System.err.println("Waiting for user $userId to unlock the device")

    for (attempt in 1..IS_USER_UNLOCKED_ATTEMPTS) {
        if (userManager.isUserUnlockingOrUnlocked(userId)) {
            println("User $userId is unlocking/unlocked")
            return
        }

        if (attempt < IS_USER_UNLOCKED_ATTEMPTS) {
            Thread.sleep(1000)
        }
    }

    throw IllegalStateException(
        "User $userId did not unlock the device after $IS_USER_UNLOCKED_ATTEMPTS attempts")
}

private fun mainInternal() {
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
        println("Android 9 does not have hard-restricted permissions")
        return
    }

    switchToSystemUid()
    waitForLogin(0)

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
        System.err.println("Failed to remove hard restrictions")
        e.printStackTrace()
        exitProcess(1)
    }
}