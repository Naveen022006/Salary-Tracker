package com.local.salarytracker.ui

import android.content.Context
import com.local.salarytracker.parse.PaymentApps
import java.util.concurrent.ConcurrentHashMap

/** Friendly app name for a package (catalog name first, then the installed app's label). */
object AppLabels {
    private val cache = ConcurrentHashMap<String, String>()

    fun label(ctx: Context, pkg: String?): String? {
        if (pkg.isNullOrBlank()) return null
        PaymentApps.byPackage(pkg)?.let { return it.name }
        cache[pkg]?.let { return it.takeIf { l -> l.isNotEmpty() } }
        val l = try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            ""
        }
        cache[pkg] = l
        return l.takeIf { it.isNotEmpty() }
    }
}
