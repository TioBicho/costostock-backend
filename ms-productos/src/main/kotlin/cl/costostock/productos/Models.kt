package cl.costostock.productos

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "productos")
class ProductoEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var title: String = "",

    @Column(length = 500)
    var description: String = "",

    var category: String = "",
    var storeName: String = "",
    var address: String = "",

    @Column(nullable = false)
    var originalPrice: Int = 0,

    var offerPrice: Int? = null,

    var offerEndsAt: Instant? = null,

    @Column(nullable = false)
    var stock: Int = 0,

    @Column(length = 1000)
    var imageUrl: String = "",

    var pickupHours: String = "",
    var co2SavedKg: Double = 0.0,

    @Column(nullable = false)
    var sellerId: Long = 0,

    @Column(nullable = false)
    var sellerEmail: String = "",

    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
)
