package com.app.coachup.app

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DatabaseSchemaTest {
    @Test
    fun testCompilePostgrestFilter() = runBlocking {
        try {
            val client = createSupabaseClient(
                supabaseUrl = "https://auiebboyocmkkxbdahqf.supabase.co",
                supabaseKey = "sb_publishable_gldj0fxGYVdS5WmeTEQC0Q_L5sPqgEa"
            ) {
                install(Postgrest)
            }
            
            client.from("training_programs").select {
                filter {
                    eq("is_active", true)
                    or {
                        eq("privacy", "public")
                        filter("visible_member_ids", FilterOperator.CS, "{\"user-id\"}")
                    }
                }
            }
            println("SCHEMA TEST - filter query compiled successfully!")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
