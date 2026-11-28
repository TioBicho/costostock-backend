package cl.costostock.usuarios

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("dev")
class DevProfileWarning {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun warn() {
        log.warn("PERFIL DEV ACTIVO: clave de token de desarrollo. NO usar en produccion " +
                "(define SPRING_PROFILES_ACTIVE=prod y AUTH_TOKEN_KEY, la misma de ms-auth).")
    }
}
