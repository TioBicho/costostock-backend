package cl.costostock.usuarios

import org.springframework.data.jpa.repository.JpaRepository

interface DireccionRepository : JpaRepository<DireccionEntity, Long> {
    fun findByUsuarioIdOrderByPrincipalDescIdDesc(usuarioId: Long): List<DireccionEntity>
}

interface PreferenciaRepository : JpaRepository<PreferenciaEntity, Long> {
    fun findByUsuarioId(usuarioId: Long): PreferenciaEntity?
}
