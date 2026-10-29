package com.ljyh.mei.playback.lx

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object LxCrypto {
    fun invoke(raw: String): String {
        require(raw.length <= 2 * 1024 * 1024)
        val obj = lxJson.parseToJsonElement(raw).jsonObject
        fun string(key: String, default: String = "") =
            (obj[key] as? JsonPrimitive)?.content ?: default
        fun bytes(key: String): ByteArray = (obj[key] as? JsonArray)
            ?.map { it.jsonPrimitive.int.toByte() }?.toByteArray()
            ?: string(key).toByteArray(Charsets.UTF_8)
        fun array(value: ByteArray) = JsonArray(value.map { JsonPrimitive(it.toInt() and 255) }).toString()

        return when (string("op")) {
            "from" -> array(when (string("format", "utf8")) {
                "base64" -> Base64.getDecoder().decode(string("value"))
                "hex" -> string("value").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                else -> string("value").toByteArray(Charsets.UTF_8)
            })
            "string" -> JsonPrimitive(when (string("format", "utf8")) {
                "base64" -> Base64.getEncoder().encodeToString(bytes("value"))
                "hex" -> bytes("value").joinToString("") { "%02x".format(it.toInt() and 255) }
                else -> bytes("value").toString(Charsets.UTF_8)
            }).toString()
            "md5" -> JsonPrimitive(MessageDigest.getInstance("MD5").digest(bytes("value"))
                .joinToString("") { "%02x".format(it.toInt() and 255) }).toString()
            "random" -> array(ByteArray(string("size").toInt().coerceIn(0, 65_536)).also {
                SecureRandom().nextBytes(it)
            })
            "aes" -> {
                val mode = string("mode")
                val cipher = Cipher.getInstance(if (mode.endsWith("cbc")) "AES/CBC/PKCS5Padding" else "AES/ECB/PKCS5Padding")
                val key = SecretKeySpec(bytes("key").also { require(it.size == 16) }, "AES")
                if (mode.endsWith("cbc")) cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(bytes("iv")))
                else cipher.init(Cipher.ENCRYPT_MODE, key)
                array(cipher.doFinal(bytes("value")))
            }
            "rsa" -> {
                val pem = string("key").replace(Regex("-----[^-]+-----|\\s"), "")
                val key = KeyFactory.getInstance("RSA").generatePublic(
                    X509EncodedKeySpec(Base64.getDecoder().decode(pem))
                )
                val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                array(cipher.doFinal(bytes("value")))
            }
            else -> error("Unsupported utility")
        }
    }
}
