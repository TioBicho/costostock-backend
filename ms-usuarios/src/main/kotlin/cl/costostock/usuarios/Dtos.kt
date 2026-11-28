package cl.costostock.usuarios

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class DireccionRequest(
    @field:NotBlank @field:Size(max = 30) val alias: String,
    @field:NotBlank @field:Size(max = 80) val street: String,
    @field:NotBlank @field:Size(max = 60) val commune: String,
    @field:NotBlank @field:Size(max = 60) val region: String,
    @field:Size(max = 120) val reference: String = "",
    val principal: Boolean = false
)

data class DireccionResponse(
    val id: Long,
    val alias: String,
    val street: String,
    val commune: String,
    val region: String,
    val reference: String,
    val principal: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class PreferenciaRequest(
    @field:Size(max = 10) val favoriteCategories: List<String> = emptyList(),
    @field:Min(1) @field:Max(50) val maxDistanceKm: Int = 5,
    val offerAlerts: Boolean = true
)

data class PreferenciaResponse(
    val favoriteCategories: List<String>,
    val maxDistanceKm: Int,
    val offerAlerts: Boolean,
    val updatedAt: Long?
)

data class ErrorResponse(val error: String)

fun DireccionEntity.toResponse() = DireccionResponse(
    id = id!!,
    alias = alias,
    street = street,
    commune = commune,
    region = region,
    reference = reference,
    principal = principal,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli()
)

fun PreferenciaEntity.toResponse() = PreferenciaResponse(
    favoriteCategories = favoriteCategories.toList(),
    maxDistanceKm = maxDistanceKm,
    offerAlerts = offerAlerts,
    updatedAt = updatedAt?.toEpochMilli()
)
