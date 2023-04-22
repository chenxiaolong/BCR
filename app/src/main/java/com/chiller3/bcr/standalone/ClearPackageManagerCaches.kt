@file:Suppress("SameParameterValue")

package com.chiller3.bcr.standalone

import com.chiller3.bcr.BuildConfig
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.system.exitProcess

private val PACKAGE_CACHE_DIR = Paths.get("/data/system/package_cache")

private var dryRun = false

private fun delete(path: Path) {
    if (dryRun) {
        println("Would have deleted: $path")
    } else {
        println("Deleting: $path")
        path.deleteIfExists()
    }
}

private fun ByteArray.indexOfSubarray(needle: ByteArray, start: Int = 0): Int {
    require(start >= 0) { "start must be non-negative" }

    if (needle.isEmpty()) {
        return 0
    }

    outer@ for (i in 0 until size - needle.size + 1) {
        for (j in needle.indices) {
            if (this[i + j] != needle[j]) {
                continue@outer
            }
        }
        return i
    }

    return -1
}

@OptIn(ExperimentalPathApi::class)
private fun clearPackageManagerCache(appId: String): Boolean {
    // The current implementation of the package cache uses PackageImpl.writeToParcel(), which
    // serializes the cache entry to the file as a Parcel. The current Parcel implementation stores
    // string values as null-terminated little-endian UTF-16. One of the string values stored is
    // manifestPackageName, which we can match on.
    //
    // This is a unique enough search that there should never be a false positive, but even if there
    // is, the package manager will just repopulate the cache.
    val needle = "\u0000$appId\u0000".toByteArray(Charsets.UTF_16LE)
    var ret = true

    for (path in PACKAGE_CACHE_DIR.walk()) {
        if (!path.isRegularFile()) {
            continue
        }

        try {
            // Not the most efficient, but these are tiny files that Android is later going to read
            // entirely into memory anyway
            if (path.readBytes().indexOfSubarray(needle) >= 0) {
                delete(path)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ret = false
        }
    }

    return ret
}

private fun mainInternal() {
    clearPackageManagerCache(BuildConfig.APPLICATION_ID)
}

fun main(args: Array<String>) {
    if ("--dry-run" in args) {
        dryRun = true
    }

    try {
        mainInternal()
    } catch (e: Exception) {
        System.err.println("Failed to clear caches")
        e.printStackTrace()
        exitProcess(1)
    }
}