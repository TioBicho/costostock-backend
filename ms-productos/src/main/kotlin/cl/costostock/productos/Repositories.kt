package cl.costostock.productos

import org.springframework.data.jpa.repository.JpaRepository

interface ProductoRepository : JpaRepository<ProductoEntity, Long> {
    fun findAllByOrderByIdDesc(): List<ProductoEntity>
    fun findBySellerIdOrderByIdDesc(sellerId: Long): List<ProductoEntity>
}
