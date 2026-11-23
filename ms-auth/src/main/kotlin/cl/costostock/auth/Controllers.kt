package cl.costostock.auth

import jakarta.validation.Valid
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val auth: AuthService) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody req: RegisterRequest) = auth.register(req)

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest) = auth.login(req)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody req: RefreshRequest) = auth.refresh(req.refreshToken)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@Valid @RequestBody req: RefreshRequest) = auth.logout(req.refreshToken)

    @GetMapping("/me")
    fun me(@RequestHeader("Authorization", required = false) authorization: String?) =
        auth.me(authorization)

    @PutMapping("/me")
    fun updateMe(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @Valid @RequestBody req: UpdateProfileRequest
    ) = auth.updateProfile(authorization, req)

    @PostMapping("/change-password")
    fun changePassword(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @Valid @RequestBody req: ChangePasswordRequest
    ) = auth.changePassword(authorization, req)
}

@RestController
@RequestMapping("/api/admin")
class AdminController(private val auth: AuthService) {

    @GetMapping("/requests")
    fun pending(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestParam(name = "todas", defaultValue = "false") todas: Boolean
    ) = auth.pendingRequests(authorization, todas)

    @PatchMapping("/users/{id}/status")
    fun setStatus(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: Long,
        @RequestBody req: StatusRequest
    ) = auth.setStatus(authorization, id, req.status)
}

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApi(e: ApiException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(e.status).body(ErrorResponse(e.message ?: "Error"))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val msg = e.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.badRequest().body(ErrorResponse(msg))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse("El cuerpo de la petición no es válido"))

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleConflict(e: DataIntegrityViolationException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("El correo o el RUT ya está registrado"))
}
