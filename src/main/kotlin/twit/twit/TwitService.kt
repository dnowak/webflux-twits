package twit.twit

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import reactor.core.publisher.toMono
import twit.twit.web.on
import java.time.LocalDateTime
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicLong

@Component
class TwitService(private val userRepository: UserRepository, private val postRepository: PostRepository) {
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
        user.followed.toFlux()
                .flatMap(userRepository::user)
                .subscribe { user -> user.receive(post) }
        return postId;
    }

    fun wall(userId: UserId): Flux<Post> = userRepository.user(userId)
            .doOnNext{ user -> log.debug("wall for user: {}", user)}
            .flatMapMany { user -> user.posts() }
}

data class PostId(val id: Long)

class UserRepository(private val eventBus: EventBus, private val eventRepository: EventRepository) {
    fun user(id: UserId): Mono<User> = eventRepository
            .findByAggregateId(AggregateId(AggregateType.USER, id))
            .switchIfEmpty(createUserEvent(id))
            .reduce(User(eventBus), { u, e -> on(u, e) })

    private fun createUserEvent(id: UserId): Mono<Event> {
        val event = UserCreatedEvent(id);
        eventBus.publish(event)
        return event.toMono()
    }
}


class PostRepository(private val eventBus: EventBus) {
    private val counter = AtomicLong()
    fun create(publisher: UserId, text: String): Mono<PostId> {
        val id = PostId(counter.incrementAndGet())
        val event = PostCreatedEvent(id, publisher, text)
        eventBus.publish(event)
        return id.toMono()
    }
}

data class UserId(val name: String)
data class Post(val userId: UserId, val text: String, val timestamp: LocalDateTime)

class User(private val eventBus: EventBus) {
    companion object {
        private val log = LoggerFactory.getLogger(User::class.java)
    }

    private var userId: UserId? = null
    private var created: LocalDateTime? = null
    private var followedList = LinkedList<UserId>()
    private var wallList = LinkedList<Post>()
    private var timelineList = LinkedList<Post>()

    val followed: Collection<UserId>
        get() = followedList

    val id: UserId
        get() = userId!!

    fun on(event: UserCreatedEvent) {
        log.debug("on: {}", event)
        userId = event.id
        created = event.timestamp
    }

    fun on(event: PostSentEvent) {
        log.debug("on: {}", event)
        wallList.add(Post(event.id, event.message, event.timestamp))
    }

    fun on(event: PostReceivedEvent) {
        log.debug("on: {}", event)
        timelineList.add(Post(UserId(event.author), event.message, event.timestamp))
    }

    fun post(post: Post) {
        apply(PostSentEvent(userId!!, post.text));
    }

    fun receive(post: Post) {
        apply(PostReceivedEvent(userId!!, post.userId.name, post.text))
    }

    fun apply(event: Event) {
        eventBus.publish(event)
        on(this, event)
    }

    fun posts(): Flux<Post> = wallList.toFlux()
}
