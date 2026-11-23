package cl.costostock.productos

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ProductosApplication

fun main(args: Array<String>) {
    runApplication<ProductosApplication>(*args)
}
