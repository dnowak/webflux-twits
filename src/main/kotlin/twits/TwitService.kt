package twits

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import java.time.LocalDateTime
import java.util.LinkedList

@Component
class TwitService(private val userRepository: UserRepository,
                  private val postRepository: PostRepository,
                  private val eventRepository: EventRepository) {

    companion object {
        private val log = LoggerFactory.getLogger(TwitService::class.java)
    }

    fun post(id: UserId, text: String): Mono<PostId> {
        return userRepository.user(id)
                .flatMap { user -> post(user, text) }
    }

    fun post(user: User, text: String): Mono<PostId> {
        val postId = postRepository.create(user.id, text)
        val post = Post(user.id, text, LocalDateTime.now())
        user.post(post)
        user.followers.toFlux()
                .flatMap(userRepository::user)
                .subscribe { user -> user.receive(post) }
        return postId;
    }

    fun wall(userId: UserId): Flux<Post> = userEvents(userId)
            .reduce(WallProjection(), { projection, event -> on(projection, event) })
            .flatMapMany { it.posts() }

    fun follow(followerId: UserId, followedId: UserId) {
        userRepository.user(followerId).subscribe { user -> user.follow(followedId) }
        userRepository.user(followedId).subscribe { user -> user.addFollower(followerId) }
    }

    fun unfollow(followerId: UserId, followedId: UserId) {
        userRepository.user(followerId).subscribe { user -> user.unfollow(followedId) }
        userRepository.user(followedId).subscribe { user -> user.removeFollower(followerId) }
    }

    fun timeline(userId: UserId): Flux<Post> = userEvents(userId)
            .reduce(TimelineProjection(), { projection, event -> on(projection, event) })
            .flatMapMany { it.posts() }

    private fun userEvents(userId: UserId) = eventRepository.findByAggregateId(AggregateId(AggregateType.USER, userId))

    class WallProjection {
        val posts = LinkedList<Post>()

        fun on(event: PostSentEvent) {
            posts.add(0, Post(event.id, event.message, event.timestamp))

        }

        fun posts(): Flux<Post> = posts.toFlux()
    }

    class TimelineProjection {
        val posts = LinkedList<Post>()

        fun on(event: PostReceivedEvent) {
            posts.add(0, Post(event.author, event.message, event.timestamp))
        }

        fun posts(): Flux<Post> = posts.toFlux()
    }
}


