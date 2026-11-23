package cl.costostock.productos

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestController
@RequestMapping("/api/productos")
class ProductoController(private val servicio: ProductoService) {

    @GetMapping
    fun listar(
        @RequestAttribute(RolInterceptor.USUARIO) usuario: UsuarioToken,
        @RequestParam(name = "mios", defaultValue = "false") mios: Boolean,
        @RequestParam(name = "q", required = false) q: String?
    ) = servicio.listar(usuario, mios, q)

    @GetMapping("/{id}")
    fun obtener(@PathVariable id: Long) = servicio.obtener(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun crear(
        @RequestAttribute(RolInterceptor.USUARIO) usuario: UsuarioToken,
        @Valid @RequestBody req: ProductoRequest
    ) = servicio.crear(usuario, req)

    @PutMapping("/{id}")
    fun actualizar(
        @RequestAttribute(RolInterceptor.USUARIO) usuario: UsuarioToken,
        @PathVariable id: Long,
        @Valid @RequestBody req: ProductoRequest
    ) = servicio.actualizar(usuario, id, req)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun eliminar(
        @RequestAttribute(RolInterceptor.USUARIO) usuario: UsuarioToken,
        @PathVariable id: Long
    ) = servicio.eliminar(usuario, id)
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
}
