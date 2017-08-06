package twit.twit

import org.junit.Assert.assertEquals
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.test.test

class WebTest: AbstractIntegrationTest() {

    data class UserOutput(val name: String)

    @Autowired
    lateinit var eventRepository: EventRepository

    @Test
    fun `presents existing user`() {
        //given:
        eventRepository.add(UserCreatedEvent(UserId("john")))

        //expect:
        client.get().uri("/api/users/john")
                .accept(APPLICATION_JSON)
                .retrieve()
                .bodyToMono<UserOutput>()
                .test()
                .consumeNextWith {
                    assertEquals(it, UserOutput("john"))
                }
                .verifyComplete()
    }

    @Test
    fun `handles non existing user`() {
        //expect:
        testClient.get().uri("/api/users/paul")
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound
    }
}