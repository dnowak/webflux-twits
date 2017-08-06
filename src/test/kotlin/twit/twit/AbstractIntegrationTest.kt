package twit.twit

import org.junit.Before
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.client.WebClient

@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIntegrationTest {

    @LocalServerPort
    var port: Int? = null

    lateinit var testClient: WebTestClient
    lateinit var client: WebClient

    @Before
    fun setup() {
        testClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
        client = WebClient.create("http://localhost:$port")
    }

}