package cl.costostock.auth

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SellerFichaDto(
    @field:NotBlank val businessRut: String,
    @field:NotBlank val legalName: String,
    @field:NotBlank val businessActivity: String,
    @field:NotBlank val activityStartDate: String,
    @field:NotBlank val region: String,
    @field:NotBlank val commune: String,
    @field:NotBlank val street: String,
    @field:NotBlank val municipalPatentNumber: String,
    @field:NotBlank val sanitaryResolutionNumber: String
)

data class RegisterRequest(
    @field:Email @field:NotBlank val correo: String,
    // BCrypt solo considera los primeros 72 bytes
    @field:Size(min = 8, max = 72) val password: String,
    val role: Role = Role.CLIENTE,
    @field:NotBlank val nombre: String,
    val telefono: String = "",
    @field:Valid val ficha: SellerFichaDto? = null
)

data class LoginRequest(
    @field:NotBlank val correo: String,
    @field:NotBlank val password: String
)

data class RefreshRequest(@field:NotBlank val refreshToken: String)

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:Size(min = 8, max = 72) val newPassword: String
)

data class StatusRequest(val status: AccountStatus)

data class UserResponse(
    val id: Long,
    val correo: String,
    val nombre: String,
    val telefono: String,
    val role: Role,
    val status: AccountStatus
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    /** Segundos de vida del access token. */
    val expiresIn: Long,
    val user: UserResponse
)

data class ErrorResponse(val error: String)

fun UserEntity.toResponse() = UserResponse(
    id = id!!, correo = correo, nombre = nombre, telefono = telefono, role = role, status = status
)
