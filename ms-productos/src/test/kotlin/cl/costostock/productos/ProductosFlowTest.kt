package cl.costostock.productos

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Assertions.assertEquals
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

@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:productostest;DB_CLOSE_DELAY=-1"])
@AutoConfigureMockMvc
class ProductosFlowTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val mapper: ObjectMapper
) {

    private val clave: SecretKey =
        SecretKeySpec(Base64.getDecoder().decode("l6+6NQu9fZnUSJfcn958ugivtCQjTm3Xs+oEVBVKkE0="), "AES")

    private fun token(
        id: Long,
        role: String,
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

    private val vendedor = token(100, "VENDEDOR")
    private val otroVendedor = token(200, "VENDEDOR")
    private val admin = token(1, "ADMINISTRADOR")
    private val cliente = token(300, "CLIENTE")

    private fun send(method: HttpMethod, path: String, body: Any? = null, bearer: String? = null): ResultActions {
        val builder = request(method, path)
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        return mvc.perform(builder)
    }

    private fun json(result: ResultActions): JsonNode =
        mapper.readTree(result.andReturn().response.getContentAsString(StandardCharsets.UTF_8))

    private fun producto(
        title: String = "Pan amasado x6",
        originalPrice: Int = 2000,
        offerPrice: Int? = null,
        offerDurationMinutes: Int? = null,
        stock: Int = 5
    ) = mapOf(
        "title" to title,
        "description" to "Del día",
        "category" to "Panadería",
        "storeName" to "Panadería Central",
        "address" to "Calle 1",
        "originalPrice" to originalPrice,
        "offerPrice" to offerPrice,
        "offerDurationMinutes" to offerDurationMinutes,
        "stock" to stock
    )

    private fun crear(bearer: String, body: Any = producto()): JsonNode =
        json(send(HttpMethod.POST, "/api/productos", body, bearer).andExpect(status().isCreated))

    @Test
    fun `sin token o con token invalido no se entra`() {
        send(HttpMethod.GET, "/api/productos").andExpect(status().isUnauthorized)
        send(HttpMethod.POST, "/api/productos", producto()).andExpect(status().isUnauthorized)
        val alterado = vendedor.dropLast(3) + (if (vendedor.endsWith("AAA")) "BBB" else "AAA")
        send(HttpMethod.GET, "/api/productos", bearer = alterado).andExpect(status().isUnauthorized)
        send(HttpMethod.GET, "/api/productos", bearer = token(100, "VENDEDOR", expira = Instant.now().minusSeconds(60)))
            .andExpect(status().isUnauthorized)
        val otraClave = SecretKeySpec(ByteArray(32) { 7 }, "AES")
        send(HttpMethod.GET, "/api/productos", bearer = token(100, "VENDEDOR", key = otraClave))
            .andExpect(status().isUnauthorized)
        send(HttpMethod.GET, "/api/productos", bearer = token(100, "VENDEDOR", issuer = "otro"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `un CLIENTE no tiene acceso a ninguna parte del CRUD`() {
        val id = crear(vendedor)["id"].asLong()

        send(HttpMethod.GET, "/api/productos", bearer = cliente).andExpect(status().isForbidden)
        send(HttpMethod.GET, "/api/productos/$id", bearer = cliente).andExpect(status().isForbidden)
        send(HttpMethod.POST, "/api/productos", producto(), cliente).andExpect(status().isForbidden)
        send(HttpMethod.PUT, "/api/productos/$id", producto(), cliente).andExpect(status().isForbidden)
        val body = json(send(HttpMethod.DELETE, "/api/productos/$id", bearer = cliente).andExpect(status().isForbidden))
        assertTrue(body["error"].asText().contains("VENDEDOR"))

        send(HttpMethod.GET, "/api/productos/$id", bearer = vendedor).andExpect(status().isOk)
    }

    @Test
    fun `un VENDEDOR hace el CRUD completo de sus productos`() {
        val creado = crear(vendedor, producto(title = "Yogur x4", originalPrice = 3000, offerPrice = 1500, offerDurationMinutes = 60))
        val id = creado["id"].asLong()
        assertEquals(100, creado["sellerId"].asLong())
        assertEquals("usuario100@mail.cl", creado["sellerEmail"].asText())
        assertEquals(50, creado["discountPercent"].asInt())
        assertTrue(creado["onOffer"].asBoolean())
        assertEquals(1500, creado["currentPrice"].asInt())

        val lista = json(send(HttpMethod.GET, "/api/productos", bearer = vendedor).andExpect(status().isOk))
        assertTrue(lista.any { it["id"].asLong() == id })

        val leido = json(send(HttpMethod.GET, "/api/productos/$id", bearer = vendedor).andExpect(status().isOk))
        assertEquals("Yogur x4", leido["title"].asText())

        val editado = json(
            send(HttpMethod.PUT, "/api/productos/$id",
                producto(title = "Yogur x4 frutilla", originalPrice = 3000, offerPrice = 1200, stock = 2), vendedor)
                .andExpect(status().isOk)
        )
        assertEquals("Yogur x4 frutilla", editado["title"].asText())
        assertEquals(60, editado["discountPercent"].asInt())
        assertEquals(creado["offerEndsAt"].asLong(), editado["offerEndsAt"].asLong())

        val sinOferta = json(send(HttpMethod.PUT, "/api/productos/$id", producto(originalPrice = 3000), vendedor))
        assertTrue(sinOferta["offerPrice"].isNull)
        assertEquals(3000, sinOferta["currentPrice"].asInt())

        send(HttpMethod.DELETE, "/api/productos/$id", bearer = vendedor).andExpect(status().isNoContent)
        send(HttpMethod.GET, "/api/productos/$id", bearer = vendedor).andExpect(status().isNotFound)
    }

    @Test
    fun `un VENDEDOR no toca productos ajenos y el ADMINISTRADOR si`() {
        val id = crear(vendedor)["id"].asLong()

        send(HttpMethod.PUT, "/api/productos/$id", producto(title = "Robado"), otroVendedor).andExpect(status().isForbidden)
        send(HttpMethod.DELETE, "/api/productos/$id", bearer = otroVendedor).andExpect(status().isForbidden)

        val delOtro = json(send(HttpMethod.GET, "/api/productos?mios=true", bearer = otroVendedor).andExpect(status().isOk))
        assertTrue(delOtro.none { it["id"].asLong() == id })

        send(HttpMethod.PUT, "/api/productos/$id", producto(title = "Revisado por admin"), admin).andExpect(status().isOk)
        send(HttpMethod.DELETE, "/api/productos/$id", bearer = admin).andExpect(status().isNoContent)
    }

    @Test
    fun `validaciones del producto`() {
        send(HttpMethod.POST, "/api/productos", producto(title = ""), vendedor).andExpect(status().isBadRequest)
        send(HttpMethod.POST, "/api/productos", producto(originalPrice = 0), vendedor).andExpect(status().isBadRequest)
        send(HttpMethod.POST, "/api/productos", producto(stock = -1), vendedor).andExpect(status().isBadRequest)
        send(HttpMethod.POST, "/api/productos", producto(originalPrice = 1000, offerPrice = 1000, offerDurationMinutes = 30), vendedor)
            .andExpect(status().isBadRequest)
        val sinDuracion = json(
            send(HttpMethod.POST, "/api/productos", producto(originalPrice = 1000, offerPrice = 500), vendedor)
                .andExpect(status().isBadRequest)
        )
        assertEquals("Indica cuántos minutos durará la oferta", sinDuracion["error"].asText())
        send(HttpMethod.POST, "/api/productos", mapOf("title" to "Sin precio"), vendedor).andExpect(status().isBadRequest)
        send(HttpMethod.GET, "/api/productos/abc", bearer = vendedor).andExpect(status().isBadRequest)
        send(HttpMethod.GET, "/api/productos/999999", bearer = vendedor).andExpect(status().isNotFound)
    }

    @Test
    fun `busqueda por texto`() {
        crear(vendedor, producto(title = "Kuchen de manzana"))
        val resultado = json(send(HttpMethod.GET, "/api/productos?q=KUCHEN", bearer = admin).andExpect(status().isOk))
        assertTrue(resultado.isArray && resultado.size() >= 1)
        assertTrue(resultado.all { it["title"].asText().contains("Kuchen", ignoreCase = true) })
    }
}
