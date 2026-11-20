package cl.costostock.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:authtest;DB_CLOSE_DELAY=-1",
        "auth.admin-email=admin@test.cl",
        "auth.admin-password=Admin12345"
    ]
)
@AutoConfigureMockMvc
class AuthFlowTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val mapper: ObjectMapper
) {

    private fun send(method: HttpMethod, path: String, body: Any? = null, bearer: String? = null): ResultActions {
        val builder = request(method, path)
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        return mvc.perform(builder)
    }

    private fun json(result: ResultActions): JsonNode =
        mapper.readTree(result.andReturn().response.contentAsString)

    private fun uniqueEmail() = "u-${UUID.randomUUID().toString().take(8)}@mail.cl"

    private fun registerClient(correo: String) =
        send(HttpMethod.POST, "/api/auth/register",
            mapOf("correo" to correo, "password" to "clave12345", "nombre" to "Test"))
            .andExpect(status().isCreated)

    private fun login(correo: String, password: String = "clave12345") =
        send(HttpMethod.POST, "/api/auth/login", mapOf("correo" to correo, "password" to password))

    private fun registerAndLogin(correo: String = uniqueEmail()): JsonNode {
        registerClient(correo)
        return json(login(correo).andExpect(status().isOk))
    }

    private fun sellerBody(correo: String, rut: String) = mapOf(
        "correo" to correo, "password" to "clave12345", "nombre" to "Tienda", "role" to "VENDEDOR",
        "ficha" to mapOf(
            "businessRut" to rut, "legalName" to "Tienda SpA", "businessActivity" to "Alimentos",
            "activityStartDate" to "2020-01-01", "region" to "Valparaíso", "commune" to "Valparaíso",
            "street" to "Calle 1", "municipalPatentNumber" to "P-1", "sanitaryResolutionNumber" to "R-1"
        )
    )

    @Test
    fun `login devuelve tokens y me responde con el usuario`() {
        val correo = uniqueEmail()
        val tokens = registerAndLogin(correo)
        val access = tokens["accessToken"].asText()

        // El token va cifrado (JWE = 5 partes), no es un JWT legible
        assertTrue(access.split(".").size == 5)

        val me = json(send(HttpMethod.GET, "/api/auth/me", bearer = access).andExpect(status().isOk))
        assertTrue(me["correo"].asText() == correo)
    }

    @Test
    fun `contrasena incorrecta devuelve 401`() {
        val correo = uniqueEmail()
        registerClient(correo)
        login(correo, "incorrecta123").andExpect(status().isUnauthorized)
    }

    @Test
    fun `correo duplicado devuelve 409`() {
        val correo = uniqueEmail()
        registerClient(correo)
        send(HttpMethod.POST, "/api/auth/register",
            mapOf("correo" to correo, "password" to "clave12345", "nombre" to "Otro"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `no se puede registrar un administrador`() {
        send(HttpMethod.POST, "/api/auth/register",
            mapOf("correo" to uniqueEmail(), "password" to "clave12345", "nombre" to "X", "role" to "ADMINISTRADOR"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `un token alterado es rechazado`() {
        val access = registerAndLogin()["accessToken"].asText()
        val parts = access.split(".").toMutableList()
        parts[3] = (if (parts[3].first() == 'A') "B" else "A") + parts[3].drop(1) // altera el texto cifrado
        send(HttpMethod.GET, "/api/auth/me", bearer = parts.joinToString("."))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh rota el token y detecta reutilizacion`() {
        val first = registerAndLogin()
        val refresh1 = first["refreshToken"].asText()

        val second = json(
            send(HttpMethod.POST, "/api/auth/refresh", mapOf("refreshToken" to refresh1))
                .andExpect(status().isOk)
        )
        val refresh2 = second["refreshToken"].asText()
        assertNotEquals(refresh1, refresh2)

        // Reutilizar el viejo => 401 y se cierran todas las sesiones
        send(HttpMethod.POST, "/api/auth/refresh", mapOf("refreshToken" to refresh1))
            .andExpect(status().isUnauthorized)
        send(HttpMethod.POST, "/api/auth/refresh", mapOf("refreshToken" to refresh2))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `cambiar contrasena invalida el token anterior`() {
        val correo = uniqueEmail()
        val oldAccess = registerAndLogin(correo)["accessToken"].asText()

        val renewed = json(
            send(HttpMethod.POST, "/api/auth/change-password",
                mapOf("currentPassword" to "clave12345", "newPassword" to "nuevaClave123"), oldAccess)
                .andExpect(status().isOk)
        )

        send(HttpMethod.GET, "/api/auth/me", bearer = oldAccess).andExpect(status().isUnauthorized)
        send(HttpMethod.GET, "/api/auth/me", bearer = renewed["accessToken"].asText()).andExpect(status().isOk)
        login(correo, "nuevaClave123").andExpect(status().isOk)
    }

    @Test
    fun `vendedor queda pendiente hasta que el admin lo aprueba`() {
        val correo = uniqueEmail()
        val created = json(
            send(HttpMethod.POST, "/api/auth/register", sellerBody(correo, "76-${UUID.randomUUID().toString().take(6)}"))
                .andExpect(status().isCreated)
        )
        val sellerId = created["id"].asLong()
        assertTrue(created["status"].asText() == "PENDIENTE")

        login(correo).andExpect(status().isForbidden)

        val adminAccess = json(login("admin@test.cl", "Admin12345").andExpect(status().isOk))["accessToken"].asText()

        // Un cliente normal no puede usar el panel de admin
        send(HttpMethod.GET, "/api/admin/requests", bearer = registerAndLogin()["accessToken"].asText())
            .andExpect(status().isForbidden)

        val pending = json(send(HttpMethod.GET, "/api/admin/requests", bearer = adminAccess).andExpect(status().isOk))
        assertTrue(pending.any { it["id"].asLong() == sellerId })

        send(HttpMethod.PATCH, "/api/admin/users/$sellerId/status", mapOf("status" to "APROBADA"), adminAccess)
            .andExpect(status().isOk)

        login(correo).andExpect(status().isOk)
    }
}
