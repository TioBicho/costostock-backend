package cl.costostock.auth

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

data class AccessClaims(
    val userId: Long,
    val correo: String,
    val role: Role,
    val version: Int,
    val expiresAt: Instant
)

@Service
class TokenService(private val props: AuthProperties) {

    private val key: SecretKey
    private val random = SecureRandom()

    init {
        val bytes = try {
            Base64.getDecoder().decode(props.tokenKey)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("AUTH_TOKEN_KEY debe estar en Base64", e)
        }
        check(bytes.size == 32) { "AUTH_TOKEN_KEY debe ser de 32 bytes (256 bits). Usa: openssl rand -base64 32" }
        key = SecretKeySpec(bytes, "AES")
    }

    fun createAccessToken(user: UserEntity): String {
        val now = Instant.now()
        val exp = now.plus(props.accessTtlMinutes, ChronoUnit.MINUTES)
        return Jwts.builder()
            .issuer(ISSUER)
            .subject(user.id.toString())
            .claim("correo", user.correo)
            .claim("role", user.role.name)
            .claim("ver", user.tokenVersion)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .encryptWith(key, Jwts.ENC.A256GCM)
            .compact()
    }

    fun parseAccessToken(token: String): AccessClaims? = try {
        val c = Jwts.parser()
            .requireIssuer(ISSUER)
            .decryptWith(key)
            .build()
            .parseEncryptedClaims(token)
            .payload
        AccessClaims(
            userId = c.subject.toLong(),
            correo = c["correo"] as String,
            role = Role.valueOf(c["role"] as String),
            version = (c["ver"] as Number).toInt(),
            expiresAt = c.expiration.toInstant()
        )
    } catch (e: JwtException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    fun newRefreshToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val ISSUER = "costostock-auth"
    }
}
