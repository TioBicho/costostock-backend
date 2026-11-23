package cl.costostock.productos

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import kotlin.math.roundToInt

data class ProductoRequest(
    @field:NotBlank @field:Size(max = 80) val title: String,
    @field:Size(max = 500) val description: String = "",
    @field:NotBlank @field:Size(max = 40) val category: String,
    @field:NotBlank @field:Size(max = 80) val storeName: String,
    @field:Size(max = 120) val address: String = "",
    @field:Min(1) val originalPrice: Int,
    @field:Min(1) val offerPrice: Int? = null,
    @field:Min(1) @field:Max(10080) val offerDurationMinutes: Int? = null,
    @field:Min(0) val stock: Int,
    @field:Size(max = 1000) val imageUrl: String = "",
    @field:Size(max = 80) val pickupHours: String = "",
    @field:DecimalMin("0.0") val co2SavedKg: Double = 0.0
)

data class ProductoResponse(
    val id: Long,
    val title: String,
    val description: String,
    val category: String,
    val storeName: String,
    val address: String,
    val originalPrice: Int,
    val offerPrice: Int?,
    val offerEndsAt: Long?,
    val discountPercent: Int,
    val onOffer: Boolean,
    val currentPrice: Int,
    val stock: Int,
    val imageUrl: String,
    val pickupHours: String,
    val co2SavedKg: Double,
    val sellerId: Long,
    val sellerEmail: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class ErrorResponse(val error: String)

fun ProductoEntity.toResponse(ahora: Instant = Instant.now()): ProductoResponse {
    val oferta = offerPrice
    val vigente = oferta != null && offerEndsAt?.isAfter(ahora) == true
    val descuento = if (oferta != null && originalPrice > 0) {
        ((originalPrice - oferta) * 100.0 / originalPrice).roundToInt()
    } else {
        0
    }
    return ProductoResponse(
        id = id!!,
        title = title,
        description = description,
        category = category,
        storeName = storeName,
        address = address,
        originalPrice = originalPrice,
        offerPrice = oferta,
        offerEndsAt = offerEndsAt?.toEpochMilli(),
        discountPercent = descuento,
        onOffer = vigente,
        currentPrice = if (vigente && oferta != null) oferta else originalPrice,
        stock = stock,
        imageUrl = imageUrl,
        pickupHours = pickupHours,
        co2SavedKg = co2SavedKg,
        sellerId = sellerId,
        sellerEmail = sellerEmail,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )
}
