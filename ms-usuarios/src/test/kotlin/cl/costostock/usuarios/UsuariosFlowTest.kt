package cl.costostock.usuarios

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.Date
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:usuariostest;DB_CLOSE_DELAY=-1"])
@AutoConfigureMockMvc
class UsuariosFlowTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val mapper: ObjectMapper
) {

    private val clave: SecretKey =
        SecretKeySpec(Base64.getDecoder().decode("l6+6NQu9fZnUSJfcn958ugivtCQjTm3Xs+oEVBVKkE0="), "AES")

    private fun token(
        id: Long,
        role: String = "CLIENTE",
        expira: Instant = Instant.now().plusSeconds(900),
        key: SecretKey = clave,
        issuer: String = "costostock-auth"
    ): String = Jwts.builder()
        .issuer(issuer)
        .subject(id.toString())
        .claim("correo", "usuario$id@mail.cl")
        .claim("role", role)
        .claim("ver", 0)
        .issuedAt(Date())
        .expiration(Date.from(expira))
        .encryptWith(key, Jwts.ENC.A256GCM)
        .compact()

    private fun send(method: HttpMethod, path: String, body: Any? = null, bearer: String? = null): ResultActions {
        val builder = request(method, path)
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        return mvc.perform(builder)
    }

    private fun json(result: ResultActions): JsonNode =
        mapper.readTree(result.andReturn().response.getContentAsString(StandardCharsets.UTF_8))

    private fun direccion(
        alias: String = "Casa",
        street: String = "Av. Pedro Montt 1820",
        principal: Boolean = false
    ) = mapOf(
        "alias" to alias,
        "street" to street,
        "commune" to "Valparaíso",
        "region" to "Valparaíso",
        "reference" to "Depto 5",
        "principal" to principal
    )

    private fun crear(bearer: String, body: Any = direccion()): JsonNode =
        json(send(HttpMethod.POST, "/api/direcciones", body, bearer).andExpect(status().isCreated))

    private fun listar(bearer: String): JsonNode =
        json(send(HttpMethod.GET, "/api/direcciones", bearer = bearer).andExpect(status().isOk))

    @Test
    fun `sin token o con token invalido no se entra`() {
        send(HttpMethod.GET, "/api/direcciones").andExpect(status().isUnauthorized)
        send(HttpMethod.GET, "/api/preferencias").andExpect(status().isUnauthorized)
        send(HttpMethod.POST, "/api/direcciones", direccion()).andExpect(status().isUnauthorized)
        val valido = token(10)
        val alterado = valido.dropLast(3) + (if (valido.endsWith("AAA")) "BBB" else "AAA")
        send(HttpMethod.GET, "/api/direcciones", bearer = alterado).andExpect(status().isUnauthorized)
        send(HttpMethod.GET, "/api/direcciones", bearer = token(10, expira = Instant.now().minusSeconds(60)))
            .andExpect(status().isUnauthorized)
        val otraClave = SecretKeySpec(ByteArray(32) { 7 }, "AES")
        send(HttpMethod.GET, "/api/preferencias", bearer = token(10, key = otraClave))
            .andExpect(status().isUnauthorized)
        send(HttpMethod.GET, "/api/preferencias", bearer = token(10, issuer = "otro"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `cliente vendedor y administrador usan sus propios datos`() {
        listOf(token(20, "CLIENTE"), token(21, "VENDEDOR"), token(22, "ADMINISTRADOR")).forEach { sesion ->
            send(HttpMethod.GET, "/api/direcciones", bearer = sesion).andExpect(status().isOk)
            send(HttpMethod.GET, "/api/preferencias", bearer = sesion).andExpect(status().isOk)
        }
    }

    @Test
    fun `un usuario hace el CRUD completo de sus direcciones`() {
        val sesion = token(30)
        val creada = crear(sesion, direccion(alias = "Casa"))
        val id = creada["id"].asLong()
        assertEquals("Casa", creada["alias"].asText())
        assertEquals("Depto 5", creada["reference"].asText())

        assertTrue(listar(sesion).any { it["id"].asLong() == id })

        val leida = json(send(HttpMethod.GET, "/api/direcciones/$id", bearer = sesion).andExpect(status().isOk))
        assertEquals("Casa", leida["alias"].asText())

        val editada = json(
            send(HttpMethod.PUT, "/api/direcciones/$id", direccion(alias = "Trabajo", street = "Calle 2"), sesion)
                .andExpect(status().isOk)
        )
        assertEquals("Trabajo", editada["alias"].asText())
        assertEquals("Calle 2", editada["street"].asText())

        send(HttpMethod.DELETE, "/api/direcciones/$id", bearer = sesion).andExpect(status().isNoContent)
        send(HttpMethod.GET, "/api/direcciones/$id", bearer = sesion).andExpect(status().isNotFound)
    }

    @Test
    fun `la primera direccion queda como principal y solo hay una principal`() {
        val sesion = token(40)
        val primera = crear(sesion, direccion(alias = "Casa"))
        assertTrue(primera["principal"].asBoolean())
        val segunda = crear(sesion, direccion(alias = "Trabajo"))
        assertFalse(segunda["principal"].asBoolean())

        val tercera = crear(sesion, direccion(alias = "Padres", principal = true))
        assertTrue(tercera["principal"].asBoolean())
        var lista = listar(sesion)
        assertEquals(1, lista.count { it["principal"].asBoolean() })
        assertEquals(tercera["id"].asLong(), lista[0]["id"].asLong())

        val marcada = json(
            send(HttpMethod.PUT, "/api/direcciones/${segunda["id"].asLong()}",
                direccion(alias = "Trabajo", principal = true), sesion)
                .andExpect(status().isOk)
        )
        assertTrue(marcada["principal"].asBoolean())
        lista = listar(sesion)
        assertEquals(1, lista.count { it["principal"].asBoolean() })
        assertEquals(segunda["id"].asLong(), lista[0]["id"].asLong())
    }

    @Test
    fun `al eliminar la principal pasa a serlo la mas reciente`() {
        val sesion = token(50)
        val casa = crear(sesion, direccion(alias = "Casa"))["id"].asLong()
        crear(sesion, direccion(alias = "Trabajo"))
        val padres = crear(sesion, direccion(alias = "Padres"))["id"].asLong()

        send(HttpMethod.DELETE, "/api/direcciones/$casa", bearer = sesion).andExpect(status().isNoContent)

        val lista = listar(sesion)
        assertEquals(2, lista.size())
        assertEquals(padres, lista[0]["id"].asLong())
        assertTrue(lista[0]["principal"].asBoolean())
        assertFalse(lista[1]["principal"].asBoolean())
    }

    @Test
    fun `las direcciones de otro usuario no se tocan ni siquiera el ADMINISTRADOR`() {
        val id = crear(token(60))["id"].asLong()

        listOf(token(61), token(1, "ADMINISTRADOR")).forEach { intruso ->
            send(HttpMethod.GET, "/api/direcciones/$id", bearer = intruso).andExpect(status().isForbidden)
            send(HttpMethod.PUT, "/api/direcciones/$id", direccion(alias = "Robada"), intruso)
                .andExpect(status().isForbidden)
            send(HttpMethod.DELETE, "/api/direcciones/$id", bearer = intruso).andExpect(status().isForbidden)
        }

        assertTrue(listar(token(61)).none { it["id"].asLong() == id })
        send(HttpMethod.GET, "/api/direcciones/$id", bearer = token(60)).andExpect(status().isOk)
    }

    @Test
    fun `se pueden guardar hasta 5 direcciones`() {
        val sesion = token(70)
        repeat(5) { crear(sesion, direccion(alias = "Dir $it")) }

        val respuesta = json(
            send(HttpMethod.POST, "/api/direcciones", direccion(alias = "Una mas"), sesion)
                .andExpect(status().isBadRequest)
        )
        assertEquals("Puedes guardar hasta 5 direcciones", respuesta["error"].asText())
        assertEquals(5, listar(sesion).size())
    }

    @Test
    fun `validaciones de la direccion`() {
        val sesion = token(80)
        send(HttpMethod.POST, "/api/direcciones", direccion(alias = ""), sesion).andExpect(status().isBadRequest)
        send(HttpMethod.POST, "/api/direcciones", direccion(street = ""), sesion).andExpect(status().isBadRequest)
        send(HttpMethod.POST, "/api/direcciones", direccion(alias = "x".repeat(31)), sesion)
            .andExpect(status().isBadRequest)
        send(HttpMethod.POST, "/api/direcciones", mapOf("alias" to "Casa"), sesion).andExpect(status().isBadRequest)
        send(HttpMethod.GET, "/api/direcciones/abc", bearer = sesion).andExpect(status().isBadRequest)
        send(HttpMethod.GET, "/api/direcciones/999999", bearer = sesion).andExpect(status().isNotFound)
    }

    @Test
    fun `las preferencias tienen valores por defecto y se pueden guardar y restablecer`() {
        val sesion = token(90)
        val inicial = json(send(HttpMethod.GET, "/api/preferencias", bearer = sesion).andExpect(status().isOk))
        assertEquals(5, inicial["maxDistanceKm"].asInt())
        assertTrue(inicial["offerAlerts"].asBoolean())
        assertEquals(0, inicial["favoriteCategories"].size())
        assertTrue(inicial["updatedAt"].isNull)

        val guardada = json(
            send(HttpMethod.PUT, "/api/preferencias",
                mapOf("favoriteCategories" to listOf("Lácteos", "Panadería", "lácteos"),
                    "maxDistanceKm" to 10, "offerAlerts" to false), sesion)
                .andExpect(status().isOk)
        )
        assertEquals(10, guardada["maxDistanceKm"].asInt())
        assertFalse(guardada["offerAlerts"].asBoolean())
        assertEquals(2, guardada["favoriteCategories"].size())
        assertTrue(guardada["updatedAt"].asLong() > 0)

        val leida = json(send(HttpMethod.GET, "/api/preferencias", bearer = sesion).andExpect(status().isOk))
        assertEquals(10, leida["maxDistanceKm"].asInt())
        assertEquals(2, leida["favoriteCategories"].size())

        val soloCategorias = json(
            send(HttpMethod.PUT, "/api/preferencias", mapOf("favoriteCategories" to listOf("Snacks")), sesion)
                .andExpect(status().isOk)
        )
        assertEquals(1, soloCategorias["favoriteCategories"].size())
        assertEquals(5, soloCategorias["maxDistanceKm"].asInt())

        send(HttpMethod.DELETE, "/api/preferencias", bearer = sesion).andExpect(status().isNoContent)
        val restablecida = json(send(HttpMethod.GET, "/api/preferencias", bearer = sesion).andExpect(status().isOk))
        assertEquals(0, restablecida["favoriteCategories"].size())
        assertTrue(restablecida["updatedAt"].isNull)
    }

    @Test
    fun `cada usuario tiene sus propias preferencias`() {
        send(HttpMethod.PUT, "/api/preferencias", mapOf("maxDistanceKm" to 20), token(100)).andExpect(status().isOk)

        val deOtro = json(send(HttpMethod.GET, "/api/preferencias", bearer = token(101)).andExpect(status().isOk))
        assertEquals(5, deOtro["maxDistanceKm"].asInt())
    }

    @Test
    fun `validaciones de las preferencias`() {
        val sesion = token(110)
        send(HttpMethod.PUT, "/api/preferencias", mapOf("maxDistanceKm" to 0), sesion).andExpect(status().isBadRequest)
        send(HttpMethod.PUT, "/api/preferencias", mapOf("maxDistanceKm" to 51), sesion).andExpect(status().isBadRequest)
        send(HttpMethod.PUT, "/api/preferencias", mapOf("favoriteCategories" to List(11) { "Cat $it" }), sesion)
            .andExpect(status().isBadRequest)
        val larga = json(
            send(HttpMethod.PUT, "/api/preferencias", mapOf("favoriteCategories" to listOf("x".repeat(41))), sesion)
                .andExpect(status().isBadRequest)
        )
        assertEquals("Cada categoría puede tener hasta 40 caracteres", larga["error"].asText())
    }
}
