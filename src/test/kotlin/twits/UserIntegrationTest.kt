package twits

import org.apache.commons.lang3.RandomStringUtils
import org.junit.Test
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters

class UserIntegrationTest : AbstractIntegrationTest() {


    @Test
    fun `presents existing user`() {
        //given
        eventRepository.add(UserCreatedEvent(UserId("john")))

        //expect:
        assert().user("john").exists()
    }


    @Test
    fun `handles non existing user`() {
        //expect:
        assert().user("paul").doesNotExist()
        assert().user("alex").doesNotExist()
    }

    @Test
    fun `posts are published on followers timelines`() {
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

    @Test
    fun `does not allow posts longer than 140 characters`() {
        val text = RandomStringUtils.randomAlphanumeric(141)
        testClient.post().uri("/api/users/test/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromObject("""{"text": "$text"}"""))
                .exchange()
                .expectStatus().isBadRequest
                .expectBody().json("""{ "error": "Text too long"}""")
    }

    @Test
    fun `provides user statistics`() {
        //given
        fixture().user("a").posted("Post One")
        fixture().user("a").follows("b")
        fixture().user("a").follows("c")
        fixture().user("d").follows("a")

        //expect
        assert().user("a").isA(
                user().withName("a").withFollowers(1).withFollowed(2).withPosts(1)
        )
    }

    @Test
    fun `provides users list`() {
        //given
        fixture().user("d").follows("a")
        fixture().user("d").follows("b")
        fixture().user("a").posted("Post One - A")
        fixture().user("a").follows("b")
        fixture().user("a").follows("c")
        fixture().user("a").posted("Post Two - A")
        fixture().user("b").posted("Post One - B")

        //expect
        assert().users().containsInOrder(
                user().withName("a").withFollowers(1).withFollowed(2).withPosts(2),
                user().withName("b").withFollowers(2).withFollowed(0).withPosts(1),
                user().withName("c").withFollowers(1).withFollowed(0).withPosts(0),
                user().withName("d").withFollowers(0).withFollowed(2).withPosts(0)
        )
    }
}


