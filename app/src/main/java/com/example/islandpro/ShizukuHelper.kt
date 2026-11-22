package com.example.islandpro

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuHelper {

    fun isRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            isRunning() && !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    fun requestPermission(code: Int) {
        try {
            if (isRunning() && !Shizuku.isPreV11()) {
                Shizuku.requestPermission(code)
            }
        } catch (e: Throwable) {
        }
    }

    private fun run(cmd: String): Boolean {
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            m.isAccessible = true
            val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            p.waitFor() == 0
        } catch (e: Throwable) {
            false
        }
    }

    // enabled = false hides the phone's own pop-up banners, true brings them back
    fun setHeadsUp(context: Context, enabled: Boolean): Boolean {
        if (!hasPermission()) return false
        val value = if (enabled) 1 else 0
        val ok = run("settings put global heads_up_notifications_enabled $value")
        if (ok) {
            context.getSharedPreferences("island", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("headsup_off", !enabled)
                .apply()
        }
        return ok
    }
}
