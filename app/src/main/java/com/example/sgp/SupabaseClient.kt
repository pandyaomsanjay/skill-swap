package com.example.sgp

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = "https://ghrxltlstncjcizyqyfo.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdocnhsdGxzdG5jamNpenl5cWZvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM0OTE5OTMsImV4cCI6MjA4OTA2Nzk5M30.FkIIFgdOzRUCYAtBPokKL91caDpL0G0tsdeot_JeOEQ"
    ) {
        install(Storage)
        install(Postgrest)
        install(Auth)
    }
}
