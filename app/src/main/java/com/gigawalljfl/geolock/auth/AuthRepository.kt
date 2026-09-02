package com.gigawalljfl.geolock.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

sealed class AuthResult {
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Conta na nuvem via Firebase Auth — usada SÓ pra permitir sincronizar
 * progresso e anexos entre aparelhos (ver Supabase Storage, próxima
 * etapa). É inteiramente opcional: o app continua funcionando 100% local,
 * sem login nenhum, exatamente como sempre funcionou.
 */
class AuthRepository {
    private val auth = FirebaseAuth.getInstance()

    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun currentUserSnapshot(): FirebaseUser? = auth.currentUser

    suspend fun signUp(email: String, password: String): AuthResult {
        return try {
            auth.createUserWithEmailAndPassword(email.trim(), password).await()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(mapAuthError(e))
        }
    }

    suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(mapAuthError(e))
        }
    }

    fun signOut() {
        auth.signOut()
    }

    /**
     * Token de identidade do Firebase — é isso, e não usuário/senha, que
     * vai autorizar as chamadas ao Supabase Storage na próxima etapa
     * (integração "Third-Party Auth" do Supabase aceitando o Firebase
     * como emissor confiável).
     */
    suspend fun getIdToken(): String? {
        return try {
            auth.currentUser?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            null
        }
    }

    private fun mapAuthError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("email address is already in use", ignoreCase = true) -> "Já existe uma conta com esse e-mail."
            msg.contains("badly formatted", ignoreCase = true) -> "E-mail inválido."
            msg.contains("password is invalid", ignoreCase = true) -> "Senha incorreta."
            msg.contains("no user record", ignoreCase = true) -> "Não existe conta com esse e-mail."
            msg.contains("network error", ignoreCase = true) -> "Sem conexão com a internet."
            msg.contains("at least 6 characters", ignoreCase = true) -> "A senha precisa de pelo menos 6 caracteres."
            else -> "Não foi possível autenticar. Tenta de novo."
        }
    }
}
