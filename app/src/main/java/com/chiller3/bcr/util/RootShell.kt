/*
 * SPDX-FileCopyrightText: 2024 Your Name
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.bcr.util

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

class RootShell {
    companion object {
        private val TAG = RootShell::class.java.simpleName

        fun isRootAvailable(): Boolean {
            return try {
                val proc = Runtime.getRuntime().exec("su -c id")
                val exit = proc.waitFor()
                exit == 0
            } catch (e: Exception) {
                Log.w(TAG, "Root check failed", e)
                false
            }
        }

        fun runCommands(vararg commands: String): Boolean {
            return try {
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)
                for (cmd in commands) {
                    os.writeBytes(cmd)
                    os.writeBytes("\n")
                }
                os.writeBytes("exit\n")
                os.flush()
                val exitCode = process.waitFor()

                val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
                val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
                if (exitCode != 0) {
                    Log.e(TAG, "Root command failed ($exitCode):\n$stdout\n$stderr")
                }
                exitCode == 0
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run root commands", e)
                false
            }
        }
    }
}


