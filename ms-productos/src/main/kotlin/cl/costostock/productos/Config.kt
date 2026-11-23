package cl.costostock.productos

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
@Profile("dev")
class ProductosDeEjemplo(private val productos: ProductoRepository) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (productos.count() > 0) return
        val ahora = Instant.now()
        fun ejemplo(
            title: String, description: String, category: String, storeName: String, address: String,
            originalPrice: Int, offerPrice: Int, stock: Int, minutos: Long, co2: Double, imageUrl: String
        ) = ProductoEntity(
            title = title, description = description, category = category, storeName = storeName,
            address = address, originalPrice = originalPrice, offerPrice = offerPrice,
            offerEndsAt = ahora.plus(minutos, ChronoUnit.MINUTES), stock = stock, imageUrl = imageUrl,
            pickupHours = "Consultar horario con el local", co2SavedKg = co2,
            sellerId = 0, sellerEmail = "demo@costostock.cl", createdAt = ahora, updatedAt = ahora
        )
        productos.saveAll(
            listOf(
                ejemplo(
                    "Pack Yogur Batido 12x120g", "Lote refrigerado en óptimo estado, vencimiento a 5 días.",
                    "Lácteos", "Supermercado Central Valparaíso", "Av. Pedro Montt 1820",
                    4990, 1990, 8, 60, 1.45,
                    "https://images.unsplash.com/photo-1488477181946-6428a0291777?auto=format&fit=crop&w=600&q=80"
                ),
                ejemplo(
                    "Caja Galletas Avena & Miel 400g", "Excedente de temporada con empaque intacto.",
                    "Snacks", "EcoMarket Viña del Mar", "Calle Valparaíso 650",
                    3290, 1290, 4, 30, 0.85,
                    "https://images.unsplash.com/photo-1558961363-fa8fdf82db35?auto=format&fit=crop&w=600&q=80"
                ),
                ejemplo(
                    "Queso Gauda Laminado 500g", "Corte laminado al vacío, conservación en frío.",
                    "Lácteos", "Distribuidora Costa", "Av. Libertad 340",
                    5890, 2790, 12, 120, 2.30,
                    "https://media.solotodo.com/media/products/2091794.jpg"
                )
            )
        )
        log.info("Se cargaron 3 productos de ejemplo")
    }
}

@Component
@Profile("dev")
class DevProfileWarning {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun warn() {
        log.warn("PERFIL DEV ACTIVO: clave de token de desarrollo. NO usar en produccion " +
                "(define SPRING_PROFILES_ACTIVE=prod y AUTH_TOKEN_KEY, la misma de ms-auth).")
    }
}
