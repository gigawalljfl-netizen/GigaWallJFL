package com.gigawalljfl.geolock.cloud

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Preencha com os valores reais do teu projeto Supabase — Project
 * Settings → API no painel (supabase.com/dashboard). O bucket precisa
 * existir e ser PRIVADO, com uma política de RLS restringindo cada
 * usuário à própria pasta (ver instruções que te passei).
 */
object SupabaseConfig {
    const val SUPABASE_URL = "https://fcgdnaqijekxqzhkijms.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZjZ2RuYXFpamVreHF6aGtpam1zIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg3MTE0MjQsImV4cCI6MjEwNDI4NzQyNH0.CHySuJoMiGxCj3_VekfA98bbm5qvaT5-OV4-5iI9fNo"
    const val BUCKET_NAME = "geolock-vault"
}

/**
 * Fala diretamente com a API REST do Supabase Storage (sem SDK oficial —
 * evita puxar uma cadeia de dependências Kotlin Multiplatform que exigiria
 * subir a versão do Kotlin do projeto inteiro, um risco desnecessário
 * dado que só precisamos de upload/download simples).
 *
 * Toda chamada é autorizada com o TOKEN DE IDENTIDADE DO FIREBASE (não
 * usuário/senha) — é essa ponte que a integração "Third-Party Auth" do
 * Supabase valida do lado do servidor, sem precisar de uma segunda conta.
 *
 * Convenção de caminho dentro do bucket: sempre prefixado pelo UID do
 * Firebase, batendo com a política de RLS (storage.foldername(name)[1] =
 * auth.uid()) — cada usuário só enxerga a própria pasta.
 */
class CloudStorageRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Envia bytes pra um caminho dentro do bucket. Retorna true se deu certo. */
    fun upload(path: String, bytes: ByteArray, mimeType: String, idToken: String): Boolean {
        return try {
            val url = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/${SupabaseConfig.BUCKET_NAME}/$path"
            val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $idToken")
                .addHeader("x-upsert", "true")
                .build()
            client.newCall(request).execute().use { response -> response.isSuccessful }
        } catch (e: IOException) {
            false
        }
    }

    /** Baixa bytes de um caminho dentro do bucket. Retorna null se não existir ou falhar. */
    fun download(path: String, idToken: String): ByteArray? {
        return try {
            val url = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/${SupabaseConfig.BUCKET_NAME}/$path"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $idToken")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (e: IOException) {
            null
        }
    }
}
