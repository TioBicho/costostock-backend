package cl.costostock.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByCorreo(correo: String): UserEntity?
    fun existsByFichaBusinessRut(rut: String): Boolean
    fun findByStatusOrderByRequestedAtDesc(status: AccountStatus): List<UserEntity>

    /** Todas las cuentas que pidieron aprobación (pendientes, aprobadas y rechazadas). */
    fun findByRequestedAtIsNotNullOrderByRequestedAtDesc(): List<UserEntity>
}

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, Long> {
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    // flushAutomatically: guarda los cambios pendientes ANTES de limpiar el contexto; sin esto,
    // clearAutomatically descartaría cambios sin guardar (p. ej. la nueva contraseña o tokenVersion).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshTokenEntity r set r.revoked = true where r.userId = :userId")
    fun revokeAllForUser(userId: Long)
}
