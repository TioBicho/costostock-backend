package cl.costostock.auth

import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

class ApiException(val status: HttpStatus, message: String) : RuntimeException(message)

@Service
class AuthService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val tokens: TokenService,
    private val encoder: PasswordEncoder,
    private val props: AuthProperties
) {

    // ---------- Registro y login ----------

    @Transactional
    fun register(req: RegisterRequest): UserResponse {
        if (req.role == Role.ADMINISTRADOR) {
            throw ApiException(HttpStatus.FORBIDDEN, "No se puede registrar una cuenta de administrador")
        }
        val correo = req.correo.trim().lowercase()
        if (users.findByCorreo(correo) != null) {
            throw ApiException(HttpStatus.CONFLICT, "El correo ya está registrado")
        }

        var ficha: SellerFicha? = null
        if (req.role == Role.VENDEDOR) {
            val f = req.ficha
                ?: throw ApiException(HttpStatus.BAD_REQUEST, "La ficha de negocio es obligatoria para vendedores")
            if (users.existsByFichaBusinessRut(f.businessRut.trim())) {
                throw ApiException(HttpStatus.CONFLICT, "El RUT de negocio ya está registrado")
            }
            ficha = SellerFicha(
                businessRut = f.businessRut.trim(),
                legalName = f.legalName.trim(),
                businessActivity = f.businessActivity.trim(),
                activityStartDate = f.activityStartDate.trim(),
                region = f.region.trim(),
                commune = f.commune.trim(),
                street = f.street.trim(),
                municipalPatentNumber = f.municipalPatentNumber.trim(),
                sanitaryResolutionNumber = f.sanitaryResolutionNumber.trim()
            )
        }

        val needsApproval = req.role == Role.VENDEDOR
        val saved = users.save(
            UserEntity(
                correo = correo,
                passwordHash = encoder.encode(req.password),
                role = req.role,
                nombre = req.nombre.trim(),
                telefono = req.telefono.trim(),
                status = if (needsApproval) AccountStatus.PENDIENTE else AccountStatus.APROBADA,
                requestedAt = if (needsApproval) Instant.now() else null,
                ficha = ficha
            )
        )
        return saved.toResponse()
    }

    @Transactional
    fun login(req: LoginRequest): TokenResponse {
        val user = users.findByCorreo(req.correo.trim().lowercase())
        if (user == null || !encoder.matches(req.password, user.passwordHash)) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos")
        }
        ensureApproved(user)
        return issueTokens(user)
    }

    // ---------- Renovación y cierre de sesión ----------

    /**
     * Rota el refresh token y emite un access token NUEVO con los datos actuales del usuario
     * (rol, estado, etc.). Si se reutiliza un refresh token ya usado, se asume robo y se cierran
     * todas las sesiones; por eso noRollbackFor: la revocación debe persistir aunque lancemos error.
     */
    @Transactional(noRollbackFor = [ApiException::class])
    fun refresh(refreshToken: String): TokenResponse {
        val stored = refreshTokens.findByTokenHash(tokens.hash(refreshToken))
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, "Refresh token inválido")

        if (stored.revoked) {
            refreshTokens.revokeAllForUser(stored.userId)
            throw ApiException(HttpStatus.UNAUTHORIZED, "Refresh token reutilizado; se cerraron todas las sesiones")
        }
        if (stored.expiresAt.isBefore(Instant.now())) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expirado")
        }

        val user = users.findById(stored.userId).orElse(null)
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado")
        ensureApproved(user)

        stored.revoked = true
        refreshTokens.save(stored)
        return issueTokens(user)
    }

    @Transactional
    fun logout(refreshToken: String) {
        val stored = refreshTokens.findByTokenHash(tokens.hash(refreshToken)) ?: return
        stored.revoked = true
        refreshTokens.save(stored)
    }

    // ---------- Endpoints autenticados ----------

    @Transactional(readOnly = true)
    fun me(authHeader: String?): UserResponse = authenticate(authHeader).toResponse()

    @Transactional
    fun changePassword(authHeader: String?, req: ChangePasswordRequest): TokenResponse {
        val user = authenticate(authHeader)
        if (!encoder.matches(req.currentPassword, user.passwordHash)) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "La contraseña actual es incorrecta")
        }
        user.passwordHash = encoder.encode(req.newPassword)
        user.tokenVersion++                      // invalida todos los access tokens anteriores
        users.saveAndFlush(user)                  // persistir antes de revocar sesiones
        refreshTokens.revokeAllForUser(user.id!!) // y todas las sesiones renovables
        return issueTokens(user)
    }

    // ---------- Administración ----------

    @Transactional(readOnly = true)
    fun pendingRequests(authHeader: String?): List<UserResponse> {
        requireAdmin(authenticate(authHeader))
        return users.findByStatusOrderByRequestedAtDesc(AccountStatus.PENDIENTE).map { it.toResponse() }
    }

    @Transactional
    fun setStatus(authHeader: String?, userId: Long, status: AccountStatus): UserResponse {
        requireAdmin(authenticate(authHeader))
        if (status == AccountStatus.PENDIENTE) {
            throw ApiException(HttpStatus.BAD_REQUEST, "El estado debe ser APROBADA o RECHAZADA")
        }
        val target = users.findById(userId).orElse(null)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado")
        target.status = status
        if (status == AccountStatus.RECHAZADA) {
            target.tokenVersion++
            refreshTokens.revokeAllForUser(target.id!!)
        }
        return users.save(target).toResponse()
    }

    // ---------- Internos ----------

    private fun requireAdmin(user: UserEntity) {
        if (user.role != Role.ADMINISTRADOR) throw ApiException(HttpStatus.FORBIDDEN, "Requiere rol ADMINISTRADOR")
    }

    private fun ensureApproved(user: UserEntity) {
        val tipo = user.role.name.lowercase()
        when (user.status) {
            AccountStatus.APROBADA -> Unit
            AccountStatus.PENDIENTE -> throw ApiException(
                HttpStatus.FORBIDDEN,
                "Tu cuenta de $tipo está pendiente de aprobación"
            )
            AccountStatus.RECHAZADA -> throw ApiException(
                HttpStatus.FORBIDDEN,
                "Tu solicitud de cuenta de $tipo fue rechazada"
            )
        }
    }

    private fun authenticate(authHeader: String?): UserEntity {
        val token = authHeader?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, "Falta el token")
        val claims = tokens.parseAccessToken(token)
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, "Token inválido o expirado")
        val user = users.findById(claims.userId).orElse(null)
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, "Token inválido")
        if (user.tokenVersion != claims.version || user.status != AccountStatus.APROBADA) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "Token revocado")
        }
        return user
    }

    private fun issueTokens(user: UserEntity): TokenResponse {
        val refresh = tokens.newRefreshToken()
        refreshTokens.save(
            RefreshTokenEntity(
                tokenHash = tokens.hash(refresh),
                userId = user.id!!,
                expiresAt = Instant.now().plus(props.refreshTtlDays, ChronoUnit.DAYS)
            )
        )
        return TokenResponse(
            accessToken = tokens.createAccessToken(user),
            refreshToken = refresh,
            expiresIn = props.accessTtlMinutes * 60,
            user = user.toResponse()
        )
    }
}
