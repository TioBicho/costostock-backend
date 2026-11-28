package cl.costostock.usuarios

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class UsuariosApplication

fun main(args: Array<String>) {
    runApplication<UsuariosApplication>(*args)
}
