package cl.costostock.usuarios

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PreferenciaService(private val preferencias: PreferenciaRepository) {

    @Transactional(readOnly = true)
    fun obtener(usuario: UsuarioToken): PreferenciaResponse =
        (preferencias.findByUsuarioId(usuario.id) ?: PreferenciaEntity(usuarioId = usuario.id)).toResponse()

    @Transactional
    fun guardar(usuario: UsuarioToken, req: PreferenciaRequest): PreferenciaResponse {
        val categorias = req.favoriteCategories
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        if (categorias.any { it.length > MAX_LARGO_CATEGORIA }) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Cada categoría puede tener hasta $MAX_LARGO_CATEGORIA caracteres")
        }

        val preferencia = preferencias.findByUsuarioId(usuario.id) ?: PreferenciaEntity(usuarioId = usuario.id)
        preferencia.favoriteCategories.clear()
        preferencia.favoriteCategories.addAll(categorias)
        preferencia.maxDistanceKm = req.maxDistanceKm
        preferencia.offerAlerts = req.offerAlerts
        preferencia.updatedAt = Instant.now()
        return preferencias.save(preferencia).toResponse()
    }

    @Transactional
    fun restablecer(usuario: UsuarioToken) {
        preferencias.findByUsuarioId(usuario.id)?.let { preferencias.delete(it) }
    }

    private companion object {
        const val MAX_LARGO_CATEGORIA = 40
    }
}
