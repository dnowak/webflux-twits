package twits

import org.junit.Test

class PostIntegrationTest : AbstractIntegrationTest() {
    @Test
    fun `posts are presented in chronological order`() {
        //given
        fixture().user("a").posted("A - 1")
        fixture().user("a").posted("A - 2")
        fixture().user("b").posted("B - 1")
        fixture().user("b").posted("B - 2")
        fixture().user("c").posted("C - 1")
        fixture().user("a").posted("A - 3")
        fixture().user("c").posted("C - 2")

        //expect
        assert().posts().containsInOrder(
                a().post().from("a").withText("A - 1"),
                a().post().from("a").withText("A - 2"),
                a().post().from("b").withText("B - 1"),
                a().post().from("b").withText("B - 2"),
                a().post().from("c").withText("C - 1"),
                a().post().from("a").withText("A - 3"),
                a().post().from("c").withText("C - 2")
        )
    }
}