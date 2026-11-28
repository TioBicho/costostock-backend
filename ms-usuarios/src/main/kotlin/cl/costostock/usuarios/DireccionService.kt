package cl.costostock.usuarios

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class DireccionService(private val direcciones: DireccionRepository) {

    @Transactional(readOnly = true)
    fun listar(usuario: UsuarioToken): List<DireccionResponse> =
        direcciones.findByUsuarioIdOrderByPrincipalDescIdDesc(usuario.id).map { it.toResponse() }

    @Transactional(readOnly = true)
    fun obtener(usuario: UsuarioToken, id: Long): DireccionResponse {
        val direccion = buscar(id)
        exigirDueno(usuario, direccion)
        return direccion.toResponse()
    }

    @Transactional
    fun crear(usuario: UsuarioToken, req: DireccionRequest): DireccionResponse {
        val existentes = direcciones.findByUsuarioIdOrderByPrincipalDescIdDesc(usuario.id)
        if (existentes.size >= MAX_DIRECCIONES) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Puedes guardar hasta $MAX_DIRECCIONES direcciones")
        }
        val ahora = Instant.now()
        val nueva = DireccionEntity(usuarioId = usuario.id, createdAt = ahora, updatedAt = ahora)
        aplicar(nueva, req)
        nueva.principal = req.principal || existentes.isEmpty()
        if (nueva.principal) quitarPrincipal(existentes)
        return direcciones.save(nueva).toResponse()
    }

    @Transactional
    fun actualizar(usuario: UsuarioToken, id: Long, req: DireccionRequest): DireccionResponse {
        val direccion = buscar(id)
        exigirDueno(usuario, direccion)
        aplicar(direccion, req)
        direccion.updatedAt = Instant.now()
        // La principal solo cambia marcando otra; enviar principal=false no la desmarca
        if (req.principal && !direccion.principal) {
            quitarPrincipal(direcciones.findByUsuarioIdOrderByPrincipalDescIdDesc(usuario.id))
            direccion.principal = true
        }
        return direcciones.save(direccion).toResponse()
    }

    @Transactional
    fun eliminar(usuario: UsuarioToken, id: Long) {
        val direccion = buscar(id)
        exigirDueno(usuario, direccion)
        direcciones.delete(direccion)
        if (direccion.principal) {
            direcciones.findByUsuarioIdOrderByPrincipalDescIdDesc(usuario.id).firstOrNull()?.principal = true
        }
    }

    private fun buscar(id: Long): DireccionEntity =
        direcciones.findById(id).orElse(null)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "La dirección no existe")

    private fun exigirDueno(usuario: UsuarioToken, direccion: DireccionEntity) {
        if (direccion.usuarioId != usuario.id) {
            throw ApiException(HttpStatus.FORBIDDEN, "Solo puedes usar tus propias direcciones")
        }
    }

    private fun quitarPrincipal(lista: List<DireccionEntity>) {
        lista.filter { it.principal }.forEach { it.principal = false }
    }

    private fun aplicar(d: DireccionEntity, req: DireccionRequest) {
        d.alias = req.alias.trim()
        d.street = req.street.trim()
        d.commune = req.commune.trim()
        d.region = req.region.trim()
        d.reference = req.reference.trim()
    }

    private companion object {
        const val MAX_DIRECCIONES = 5
    }
}
