package cl.costostock.usuarios

import jakarta.validation.Valid
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestController
@RequestMapping("/api/direcciones")
class DireccionController(private val servicio: DireccionService) {

    @GetMapping
    fun listar(@RequestAttribute(RolInterceptor.USUARIO) usuario: UsuarioToken) =
        servicio.listar(usuario)

    @GetMapping("/{id}")
    fun obtener(
        @RequestAttribute(RolInterceptor.USUARIO) usuario: UsuarioToken,
        @PathVariable id: Long
    ) = servicio.obtener(usuario, id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun crear(
        @RequestAttribute(RolInterceptor.USUARIO) usuario: UsuarioToken,
        @Valid @RequestBody req: DireccionRequest
    ) = servicio.crear(usuario, req)

    @PutMapping("/{id}")
    fun actualizar(
        @RequestAttribute(RolInterceptor.USUARIO) usuario: UsuarioToken,
        @PathVariable id: Long,
        @Valid @RequestBody req: DireccionRequest
    ) = servicio.actualizar(usuario, id, req)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun eliminar(
        @RequestAttribute(RolInterceptor.USUARIO) usuario: UsuarioToken,
        @PathVariable id: Long
    ) = servicio.eliminar(usuario, id)
}

@RestController
@RequestMapping("/api/preferencias")
class PreferenciaController(private val servicio: PreferenciaService) {

    @GetMapping
    fun obtener(@RequestAttribute(RolInterceptor.USUARIO) usuario: UsuarioToken) =
        servicio.obtener(usuario)

    @PutMapping
    fun guardar(
        @RequestAttribute(RolInterceptor.USUARIO) usuario: UsuarioToken,
        @Valid @RequestBody req: PreferenciaRequest
    ) = servicio.guardar(usuario, req)

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun restablecer(@RequestAttribute(RolInterceptor.USUARIO) usuario: UsuarioToken) =
        servicio.restablecer(usuario)
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
        ResponseEntity.badRequest().body(ErrorResponse("El cuerpo de la petición no es válido o le faltan campos"))

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse("El parámetro '${e.name}' no es válido"))

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleConflict(e: DataIntegrityViolationException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("Ya existen preferencias para este usuario"))
}
