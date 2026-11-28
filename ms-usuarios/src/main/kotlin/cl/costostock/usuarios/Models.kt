package cl.costostock.usuarios

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "direcciones")
class DireccionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var usuarioId: Long = 0,

    @Column(nullable = false)
    var alias: String = "",

    @Column(nullable = false)
    var street: String = "",

    @Column(nullable = false)
    var commune: String = "",

    @Column(nullable = false)
    var region: String = "",

    var reference: String = "",

    @Column(nullable = false)
    var principal: Boolean = false,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "preferencias")
class PreferenciaEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var usuarioId: Long = 0,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "preferencia_categorias", joinColumns = [JoinColumn(name = "preferencia_id")])
    @Column(name = "categoria", length = 40)
    var favoriteCategories: MutableList<String> = mutableListOf(),

    @Column(nullable = false)
    var maxDistanceKm: Int = 5,

    @Column(nullable = false)
    var offerAlerts: Boolean = true,

    var updatedAt: Instant? = null
)
