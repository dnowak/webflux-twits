package twits

import io.vavr.collection.List
import io.vavr.collection.List.empty
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import java.time.LocalDateTime

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
        return postId
    }

    fun wall(userId: UserId): Flux<Post> {
        fun updateProjection(posts: List<Post>, event: UserEvent) = when(event) {
            is PostSentEvent -> posts.prepend(Post(event.id, event.message, event.timestamp))
            else -> posts
        }
        return userEvents(userId)
                .reduce(empty<Post>(), ::updateProjection)
                .flatMapMany { it.toFlux() }
    }

    fun follow(followerId: UserId, followedId: UserId) {
        userRepository.user(followerId).subscribe { user -> user.follow(followedId) }
        userRepository.user(followedId).subscribe { user -> user.addFollower(followerId) }
    }

    fun unfollow(followerId: UserId, followedId: UserId) {
        userRepository.user(followerId).subscribe { user -> user.unfollow(followedId) }
        userRepository.user(followedId).subscribe { user -> user.removeFollower(followerId) }
    }

    fun timeline(userId: UserId): Flux<Post> {
        fun updateProjection(posts: List<Post>, event: UserEvent) = when(event) {
            is PostReceivedEvent -> posts.prepend(Post(event.author, event.message, event.timestamp))
            else -> posts
        }
        return userEvents(userId)
                .reduce(empty<Post>(), ::updateProjection)
                .flatMapMany { it.toFlux() }
    }

    private fun userEvents(userId: UserId): Flux<UserEvent> = eventRepository
            .findByAggregateId(AggregateId(AggregateType.USER, userId))
            .cast(UserEvent::class.java)
}


