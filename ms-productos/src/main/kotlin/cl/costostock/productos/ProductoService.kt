package cl.costostock.productos

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class ProductoService(private val productos: ProductoRepository) {

    @Transactional(readOnly = true)
    fun listar(usuario: UsuarioToken, soloMios: Boolean, texto: String?): List<ProductoResponse> {
        val lista = if (soloMios) {
            productos.findBySellerIdOrderByIdDesc(usuario.id)
        } else {
            productos.findAllByOrderByIdDesc()
        }
        val busqueda = texto?.trim().orEmpty()
        val ahora = Instant.now()
        return lista
            .filter { p ->
                busqueda.isEmpty() ||
                        p.title.contains(busqueda, ignoreCase = true) ||
                        p.category.contains(busqueda, ignoreCase = true) ||
                        p.storeName.contains(busqueda, ignoreCase = true)
            }
            .map { it.toResponse(ahora) }
    }

    @Transactional(readOnly = true)
    fun obtener(id: Long): ProductoResponse = buscar(id).toResponse()

    @Transactional
    fun crear(usuario: UsuarioToken, req: ProductoRequest): ProductoResponse {
        val ahora = Instant.now()
        val nuevo = ProductoEntity(
            sellerId = usuario.id,
            sellerEmail = usuario.correo,
            createdAt = ahora,
            updatedAt = ahora
        )
        aplicar(nuevo, req, ahora)
        return productos.save(nuevo).toResponse(ahora)
    }

    @Transactional
    fun actualizar(usuario: UsuarioToken, id: Long, req: ProductoRequest): ProductoResponse {
        val producto = buscar(id)
        exigirDueno(usuario, producto)
        val ahora = Instant.now()
        aplicar(producto, req, ahora)
        producto.updatedAt = ahora
        return productos.save(producto).toResponse(ahora)
    }

    @Transactional
    fun eliminar(usuario: UsuarioToken, id: Long) {
        val producto = buscar(id)
        exigirDueno(usuario, producto)
        productos.delete(producto)
    }

    private fun buscar(id: Long): ProductoEntity =
        productos.findById(id).orElse(null)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "El producto no existe")

    private fun exigirDueno(usuario: UsuarioToken, producto: ProductoEntity) {
        if (!usuario.esAdmin && producto.sellerId != usuario.id) {
            throw ApiException(HttpStatus.FORBIDDEN, "Solo puedes modificar tus propios productos")
        }
    }

    private fun aplicar(p: ProductoEntity, req: ProductoRequest, ahora: Instant) {
        val oferta = req.offerPrice
        if (oferta != null && oferta >= req.originalPrice) {
            throw ApiException(HttpStatus.BAD_REQUEST, "El precio de oferta debe ser menor al precio normal")
        }
        val finOferta: Instant? = when {
            oferta == null -> null
            req.offerDurationMinutes != null ->
                ahora.plus(req.offerDurationMinutes.toLong(), ChronoUnit.MINUTES)
            p.offerPrice != null && p.offerEndsAt != null -> p.offerEndsAt
            else -> throw ApiException(HttpStatus.BAD_REQUEST, "Indica cuántos minutos durará la oferta")
        }

        p.title = req.title.trim()
        p.description = req.description.trim()
        p.category = req.category.trim()
        p.storeName = req.storeName.trim()
        p.address = req.address.trim()
        p.originalPrice = req.originalPrice
        p.offerPrice = oferta
        p.offerEndsAt = finOferta
        p.stock = req.stock
        p.imageUrl = req.imageUrl.trim()
        p.pickupHours = req.pickupHours.trim()
        p.co2SavedKg = req.co2SavedKg
    }
}
