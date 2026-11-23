package cl.costostock.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByCorreo(correo: String): UserEntity?
    fun existsByFichaBusinessRut(rut: String): Boolean
    fun findByStatusOrderByRequestedAtDesc(status: AccountStatus): List<UserEntity>

    fun findByRequestedAtIsNotNullOrderByRequestedAtDesc(): List<UserEntity>
}

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, Long> {
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshTokenEntity r set r.revoked = true where r.userId = :userId")
    fun revokeAllForUser(userId: Long)
}
