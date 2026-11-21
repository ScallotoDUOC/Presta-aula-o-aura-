package com.duoc.prestaaula.util

import java.security.MessageDigest

/** Las claves nunca se guardan en texto plano: se almacena el hash SHA-256 en hexadecimal. */
object PasswordHasher {
    fun hash(password: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
