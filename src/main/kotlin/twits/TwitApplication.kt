package twits

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
@EnableAutoConfiguration
class TwitApplication

fun main(args: Array<String>) {
    SpringApplication.run(TwitApplication::class.java, *args)
}
