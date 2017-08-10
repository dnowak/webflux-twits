package twits

import org.hamcrest.Description
import org.hamcrest.Matchers
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import reactor.test.test

class WebTest : AbstractIntegrationTest() {

    data class UserOutput(val name: String)

    data class PostOutput(val author: String, val text: String, val timestamp: String)

    @Autowired
    lateinit var eventRepository: MemoryEventRepository

    @Before
    fun setupRepo() {
        eventRepository.clear()
    }

    @Test
    fun `presents existing user`() {
        //given
        eventRepository.add(UserCreatedEvent(UserId("john")))

        //expect:
        assert().user("john").exists()
    }

    fun assert() = AssertBuilder(testClient)

    class AssertBuilder(val testClient: WebTestClient) {
        fun user(name: String) = UserAssert(testClient, name)
    }

    class UserAssert(val testClient: WebTestClient, val name: String) {
        fun exists() {
            testClient.get().uri("/api/users/$name")
                    .accept(APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk
                    .returnResult(UserOutput::class.java)
                    .responseBody
                    .test()
                    .consumeNextWith {
                        assertEquals(it, UserOutput(name))
                    }
                    .verifyComplete()
        }

        fun doesNotExist() {
            testClient.get().uri("/api/users/$name")
                    .accept(APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isNotFound
        }

        fun timeline() = TimelineAssert(testClient, name)

        fun wall() = WallAssert(testClient, name)
    }

    class TimelineAssert(val testClient: WebTestClient, val name: String) {
        fun containsInOrder(vararg matchers: PostMatcher) {
            val posts = testClient.get().uri("/api/users/$name/timeline")
                    .accept(APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk
                    .expectBodyList(Post::class.java)
                    .returnResult()
                    .responseBody
            org.hamcrest.MatcherAssert.assertThat(posts, Matchers.contains(*matchers))
        }

    }

    class WallAssert(val testClient: WebTestClient, val name: String) {
        fun containsInOrder(vararg matchers: PostMatcher) {
            val posts = testClient.get().uri("/api/users/$name/wall")
                    .accept(APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk
                    .expectBodyList(Post::class.java)
                    .returnResult()
                    .responseBody
            org.hamcrest.MatcherAssert.assertThat(posts, Matchers.contains(*matchers))
        }

    }

    data class Post(val author: String, val text: String, val timestamp: String)

    class PostMatcher() : TypeSafeMatcher<Post>(Post::class.java) {
        override fun matchesSafely(post: Post): Boolean {
            return (name?.let { it == post.author } ?: true) &&
                    (text?.let { it == post.text } ?: true)
        }

        override fun describeTo(description: Description) {
            description.appendText("Post[name:<$name>, text:<$text>]")
        }

        var name: String? = null
        var text: String? = null
        fun from(name: String) = this.also { this.name = name }
        fun withText(text: String) = this.also { this.text = text }

        fun matches(post: Post): Boolean {
            return (name?.let { it == post.author } ?: true) &&
                    (text?.let { it == post.text } ?: true)
        }
    }

    fun post() = PostMatcher()

    @Test
    fun `handles non existing user`() {
        //expect:
        assert().user("paul").doesNotExist()
        assert().user("alex").doesNotExist()
    }

    @Test
    fun `posts are published on follwers timelines`() {
        //given
        fixture().user("a").follows("b")
        fixture().user("a").follows("d")
        fixture().user("c").follows("b")
        fixture().user("c").follows("e")

        //when
        user("b").posts("Post from B - 1")
        user("d").posts("Post from D - 1")
        user("e").posts("Post from E - 1")
        user("b").posts("Post from B - 2")
        user("d").posts("Post from D - 2")
        user("e").posts("Post from E - 2")

        //then
        assert().user("a").timeline().containsInOrder(
                post().from("d").withText("Post from D - 2"),
                post().from("b").withText("Post from B - 2"),
                post().from("d").withText("Post from D - 1"),
                post().from("b").withText("Post from B - 1")
        )
        assert().user("c").timeline().containsInOrder(
                post().from("e").withText("Post from E - 2"),
                post().from("b").withText("Post from B - 2"),
                post().from("e").withText("Post from E - 1"),
                post().from("b").withText("Post from B - 1")
        )
    }

    @Test
    fun `posts are added to timeline only for active followers`() {
        //given
        fixture().user("a").follows("b")
        fixture().user("b").posted("Post from B - 1")
        fixture().user("b").posted("Post from B - 2")
        fixture().user("a").unfollows("b")
        fixture().user("b").posted("Post from B - 3")
        fixture().user("b").posted("Post from B - 4")
        fixture().user("a").follows("b")
        fixture().user("b").posted("Post from B - 5")

        //then
        assert().user("a").timeline().containsInOrder(
                post().from("b").withText("Post from B - 5"),
                post().from("b").withText("Post from B - 2"),
                post().from("b").withText("Post from B - 1")
        )
    }

    private fun user(name: String): UserActions = UserActions(testClient, name)

    @Test
    fun `posts are visible on author's wall`() {
        //given
        fixture().user("a").posted("Post One")
        fixture().user("a").posted("Post Two")
        fixture().user("a").posted("Post Three")

        //expect
        assert().user("a").wall().containsInOrder(
                post().withText("Post Three"),
                post().withText("Post Two"),
                post().withText("Post One")
        )
    }

    private fun fixture() = FixtureBuilder(testClient)

    class FixtureBuilder(val testClient: WebTestClient) {
        fun user(name: String) = UserFixture(testClient, name)
    }

    class UserFixture(val testClient: WebTestClient, val name: String) {
        fun follows(followed: String) {
            testClient.put().uri("/api/users/$name/followed/$followed").accept(APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk
        }

        fun unfollows(followed: String) {
            testClient.delete().uri("/api/users/$name/followed/$followed").accept(APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk
        }

        fun posted(text: String) = post(testClient, name, text)
    }

    class UserActions(val testClient: WebTestClient, val name: String) {
        fun posts(text: String) = post(testClient, name, text)
    }

}

fun post(testClient: WebTestClient, name: String, text: String) {
    testClient.post().uri("/api/users/$name/posts")
            .contentType(APPLICATION_JSON)
            .body(BodyInserters.fromObject("""{"text": "$text"}"""))
            .exchange()
            .expectStatus().isCreated

}
