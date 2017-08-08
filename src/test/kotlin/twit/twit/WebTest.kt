package twit.twit

import org.junit.Assert.assertEquals
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.web.reactive.function.BodyInserters
import reactor.test.test

class WebTest: AbstractIntegrationTest() {

    data class UserOutput(val name: String)

    data class PostOutput(val author: String, val text: String, val timestamp: String)

    @Autowired
    lateinit var eventRepository: EventRepository

    @Test
    fun `presents existing user`() {
        //given:
        eventRepository.add(UserCreatedEvent(UserId("john")))

        //expect:
        testClient.get().uri("/api/users/john")
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .returnResult(UserOutput::class.java)
                .responseBody
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
        testClient.get().uri("/api/users/alex")
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound

    }

    @Test
    fun `post is published on follwers timelines`() {
        //when
        client.put().uri("/api/users/a/followed/b").accept(APPLICATION_JSON).exchange().block()
        client.put().uri("/api/users/c/followed/b").accept(APPLICATION_JSON).exchange().block()
        testClient.post().uri("/api/users/b/posts")
                .contentType(APPLICATION_JSON)
                .body(BodyInserters.fromObject("""{"text": "Post from B"}"""))
                .exchange()
                .expectStatus().isCreated

        //then
        testClient.get().uri("/api/users/a/timeline")
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .returnResult(PostOutput::class.java)
                .responseBody
                .test()
                .consumeNextWith {
                    assertEquals("Post from B", it.text)
                    assertEquals("b", it.author)
                }
                .verifyComplete()

        testClient.get().uri("/api/users/c/timeline")
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .returnResult(PostOutput::class.java)
                .responseBody
                .test()
                .consumeNextWith {
                    assertEquals("Post from B", it.text)
                    assertEquals("b", it.author)
                }
                .verifyComplete()

    }
}