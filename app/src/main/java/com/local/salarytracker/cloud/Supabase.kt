package com.local.salarytracker.cloud

import com.local.salarytracker.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

/** Cloud keys come from the gitignored supabase.properties at build time; blank means "not set up in this build". */
object CloudConfig {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank() &&
            BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
}

object Cloud {
    /** Lazy singleton. Only call when [CloudConfig.isConfigured]. Session is stored by the Auth plugin's default storage. */
    @OptIn(SupabaseInternal::class) // httpConfig is the only way to set the HTTP timeouts
    val client: SupabaseClient by lazy {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            httpEngine = OkHttp.create()
            // slow mobile / Wi-Fi: allow 30 s per request, 15 s to connect
            httpConfig {
                install(HttpTimeout) {
                    requestTimeoutMillis = 30_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 30_000
                }
            }
            install(Auth)
            install(Postgrest)
        }
    }
}
