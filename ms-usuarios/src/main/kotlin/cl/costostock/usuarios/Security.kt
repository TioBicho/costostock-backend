package cl.costostock.usuarios

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class ApiException(val status: HttpStatus, message: String) : RuntimeException(message)

@ConfigurationProperties("auth")
data class AuthProperties(
    val tokenKey: String
)

data class UsuarioToken(
    val id: Long,
    val correo: String,
    val role: String
) {
    val esAdmin: Boolean get() = role == "ADMINISTRADOR"
}

@Service
class TokenService(props: AuthProperties) {

    private val key: SecretKey

    init {
        val bytes = try {
            Base64.getDecoder().decode(props.tokenKey)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("AUTH_TOKEN_KEY debe estar en Base64", e)
        }
        check(bytes.size == 32) { "AUTH_TOKEN_KEY debe ser de 32 bytes (256 bits): la misma clave de ms-auth" }
        key = SecretKeySpec(bytes, "AES")
    }

    fun leer(token: String): UsuarioToken? {
        val claims = try {
            Jwts.parser()
                .requireIssuer(ISSUER)
                .decryptWith(key)
                .build()
                .parseEncryptedClaims(token)
                .payload
        } catch (e: JwtException) {
            return null
        } catch (e: RuntimeException) {
            return null
        }
        val id = claims.subject?.toLongOrNull() ?: return null
        val correo = claims["correo"] as? String ?: return null
        val role = claims["role"] as? String ?: return null
        return UsuarioToken(id, correo, role)
    }

    private companion object {
        const val ISSUER = "costostock-auth"
    }
}

@Component
class RolInterceptor(private val tokens: TokenService) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val token = request.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ") }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, "Falta el token")

        val usuario = tokens.leer(token)
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, "Token inválido o expirado")

        if (usuario.role !in ROLES_PERMITIDOS) {
            throw ApiException(
                HttpStatus.FORBIDDEN,
                "Tu rol no tiene acceso a estos datos"
            )
        }

        request.setAttribute(USUARIO, usuario)
        return true
    }

    companion object {
        const val USUARIO = "usuarioToken"
        val ROLES_PERMITIDOS = setOf("CLIENTE", "VENDEDOR", "ADMINISTRADOR")
    }
}

@Configuration
class WebConfig(private val rolInterceptor: RolInterceptor) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(rolInterceptor)
            .addPathPatterns("/api/direcciones", "/api/direcciones/**", "/api/preferencias")
    }
}
