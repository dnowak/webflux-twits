package twits

import org.junit.Before
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.client.WebClient

@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIntegrationTest {
    companion object {
        private val log = LoggerFactory.getLogger(AbstractIntegrationTest::class.java)
    }

    @LocalServerPort
    var port: Int? = null

    lateinit var testClient: WebTestClient
    lateinit var client: WebClient

    @Before
    fun setup() {
        log.debug("Setting up test clients on port: $port")
        testClient = testClient()
        client = client()
    }

    internal fun testClient() = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    protected fun client() = WebClient.create("http://localhost:$port")
}