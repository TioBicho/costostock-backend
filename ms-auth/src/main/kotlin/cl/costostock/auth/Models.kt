package cl.costostock.auth

import jakarta.persistence.*
import java.time.Instant

enum class Role { CLIENTE, VENDEDOR, ADMINISTRADOR }

enum class AccountStatus { APROBADA, PENDIENTE, RECHAZADA }

@Embeddable
class SellerFicha(
    var businessRut: String = "",
    var legalName: String = "",
    var businessActivity: String = "",
    var activityStartDate: String = "",
    var region: String = "",
    var commune: String = "",
    var street: String = "",
    var municipalPatentNumber: String = "",
    var sanitaryResolutionNumber: String = ""
)

@Entity
@Table(name = "users")
class UserEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var correo: String = "",

    @Column(nullable = false)
    var passwordHash: String = "",

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    var role: Role = Role.CLIENTE,

    var nombre: String = "",
    var telefono: String = "",

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    var status: AccountStatus = AccountStatus.APROBADA,

    var requestedAt: Instant? = null,

    @Column(nullable = false)
    var tokenVersion: Int = 0,

    @Embedded
    var ficha: SellerFicha? = null
)

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var tokenHash: String = "",

    @Column(nullable = false)
    var userId: Long = 0,

    @Column(nullable = false)
    var expiresAt: Instant = Instant.now(),

    @Column(nullable = false)
    var revoked: Boolean = false
)
