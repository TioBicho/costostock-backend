package cl.costostock.auth

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@ConfigurationProperties("auth")
data class AuthProperties(
    val tokenKey: String,
    val accessTtlMinutes: Long = 15,
    val refreshTtlDays: Long = 7,
    val adminEmail: String = "",
    val adminPassword: String = ""
)

@Configuration
class SecurityBeans {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}

@Component
class AdminSeeder(
    private val props: AuthProperties,
    private val users: UserRepository,
    private val encoder: PasswordEncoder
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (props.adminEmail.isBlank() || props.adminPassword.isBlank()) return
        val correo = props.adminEmail.trim().lowercase()
        if (users.findByCorreo(correo) != null) return
        users.save(
            UserEntity(
                correo = correo,
                passwordHash = encoder.encode(props.adminPassword),
                role = Role.ADMINISTRADOR,
                nombre = "Administrador",
                status = AccountStatus.APROBADA
            )
        )
        log.info("Administrador inicial creado: {}", correo)
    }
}

@Component
@Profile("dev")
class DevProfileWarning {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun warn() {
        log.warn("PERFIL DEV ACTIVO: clave de token y admin de desarrollo. NO usar en produccion " +
                "(define SPRING_PROFILES_ACTIVE=prod y AUTH_TOKEN_KEY).")
    }
}
