package twits

import org.hamcrest.Description
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert
import org.junit.Before
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.test.test

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

    @Autowired
    lateinit var eventRepository: MemoryEventRepository

    @Before
    fun setup() {
        log.debug("Setting up test clients on port: $port")
        testClient = testClient()
        client = client()
        eventRepository.clear()
    }

    internal fun testClient() = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    protected fun client(): WebClient = WebClient.create("http://localhost:$port")

    data class UserOutput(val name: String, val followedCount: Int, val followerCount: Int, val postCount: Int)

    data class PostOutput(val author: String, val text: String, val timestamp: String)

    fun assert() = AssertBuilder(testClient)

    class AssertBuilder(val testClient: WebTestClient) {
        fun user(name: String) = UserAssert(testClient, name)
        fun users() = UsersAssert(testClient)
        fun posts() = PostsAssert(testClient)
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
                        Assert.assertEquals(it, UserOutput(name, 0, 0, 0))
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

        fun isA(matcher: UserMatcher) {
            val user = testClient.get().uri("/api/users/$name")
                    .accept(APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk
                    .expectBody(UserOutput::class.java)
                    .returnResult()
                    .responseBody
            assertThat(user, matcher)
        }
    }

    class TimelineAssert(val testClient: WebTestClient, val name: String) {
        fun containsInOrder(vararg matchers: PostMatcher) {
            val posts = testClient.get().uri("/api/users/$name/timeline")
                    .accept(APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk
                    .expectBodyList(PostOutput::class.java)
                    .returnResult()
                    .responseBody
            assertThat(posts, Matchers.contains(*matchers))
        }
    }

    class UsersAssert(val testClient: WebTestClient) {
        fun containsInOrder(vararg matchers: UserMatcher) {
            val posts = testClient.get().uri("/api/users")
                    .accept(APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk
                    .expectBodyList(UserOutput::class.java)
                    .returnResult()
                    .responseBody
            assertThat(posts, Matchers.contains(*matchers))
        }
    }

    class PostsAssert(val testClient: WebTestClient) {
        fun containsInOrder(vararg matchers: PostMatcher) {
            val posts = testClient.get().uri("/api/posts")
                    .accept(APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk
                    .expectBodyList(PostOutput::class.java)
                    .returnResult()
                    .responseBody
            assertThat(posts, Matchers.contains(*matchers))
        }
    }

    class WallAssert(val testClient: WebTestClient, val name: String) {
        fun containsInOrder(vararg matchers: PostMatcher) {
            val posts = testClient.get().uri("/api/users/$name/wall")
                    .accept(APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk
                    .expectBodyList(PostOutput::class.java)
                    .returnResult()
                    .responseBody
            assertThat(posts, Matchers.contains(*matchers))
        }

    }

    class PostMatcher : TypeSafeMatcher<PostOutput>(PostOutput::class.java) {
        override fun matchesSafely(user: PostOutput): Boolean {
            return (name?.let { it == user.author } ?: true) &&
                    (text?.let { it == user.text } ?: true)
        }

        override fun describeTo(description: Description) {
            description.appendText("Post[name:<$name>, text:<$text>]")
        }

        var name: String? = null
        var text: String? = null
        fun from(name: String) = this.also { this.name = name }
        fun withText(text: String) = this.also { this.text = text }
    }

    fun a() = MatcherFactory()

    fun an() = a()

    class MatcherFactory {
        fun post() = PostMatcher()
        fun user() = UserMatcher()

    }


    fun user(name: String): UserActions = UserActions(testClient, name)

    fun fixture() = FixtureBuilder(testClient)

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


    class UserMatcher : TypeSafeMatcher<UserOutput>(UserOutput::class.java) {
        override fun matchesSafely(user: UserOutput): Boolean {
            return (name?.let { it == user.name } ?: true) &&
                    (followerCount?.let { it == user.followerCount } ?: true) &&
                    (followedCount?.let { it == user.followedCount } ?: true) &&
                    (postCount?.let { it == user.postCount } ?: true)
        }

        override fun describeTo(description: Description) {
            description.appendText("User[name:<$name>, followed: <$followedCount>, followers:<$followerCount>, posts:<$postCount>]")
        }

        var name: String? = null
        var followedCount: Int? = null
        var followerCount: Int? = null
        var postCount: Int? = null

        fun withName(name: String) = this.also { this.name = name }
        fun withFollowers(count: Int) = this.also { this.followerCount = count }
        fun withFollowed(count: Int) = this.also { this.followedCount = count }
        fun withPosts(count: Int) = this.also { this.postCount = count }

    }
}

fun post(testClient: WebTestClient, name: String, text: String) {
    testClient.post().uri("/api/users/$name/wall")
            .contentType(APPLICATION_JSON)
            .body(BodyInserters.fromObject("""{"text": "$text"}"""))
            .exchange()
            .expectStatus().isCreated

}
