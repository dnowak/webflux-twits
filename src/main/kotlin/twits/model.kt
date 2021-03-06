package twits

import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import reactor.core.publisher.toMono
import java.time.LocalDateTime
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicLong

data class PostId(val id: Long)

class UserRepository(private val eventBus: EventBus, private val eventRepository: EventRepository) {
    fun user(id: UserId): Mono<User> {
        val events = eventRepository
                .findByAggregateId(AggregateId(AggregateType.USER, id))
                .collectList()
                .block()!!
        val withOptionalCreate = events.toFlux()
                .switchIfEmpty(createUserEvent(id))
                .collectList()
                .block()!!
        return withOptionalCreate.toFlux()
                .reduce(User(eventBus), { u, e -> on(u, e) })
    }

    private fun createUserEvent(id: UserId): Mono<Event> = Mono.fromCallable({
        val event = UserCreatedEvent(id)
        eventBus.publish(event)
        event
    })
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

class User(private val eventBus: EventBus) {
    companion object {
        private val log = LoggerFactory.getLogger(User::class.java)
    }

    private var userId: UserId? = null
    private var created: LocalDateTime? = null
    private var followedList = LinkedList<UserId>()
    private var followersList = LinkedList<UserId>()
    private var wallList = LinkedList<Post>()
    private var timelineList = LinkedList<Post>()

    val followed: Collection<UserId>
        get() = followedList

    val followers: Collection<UserId>
        get() = followersList

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
        timelineList.add(Post(event.author, event.message, event.timestamp))
    }

    fun post(post: Post) {
        apply(PostSentEvent(userId!!, post.text))
    }

    fun receive(post: Post) {
        apply(PostReceivedEvent(userId!!, post.userId, post.text))
    }

    fun apply(event: Event) {
        eventBus.publish(event)
        on(this, event)
    }

    fun posts(): Flux<Post> = wallList.toFlux()

    fun follow(followedId: UserId) {
        if (!followedList.contains(followedId)) {
            apply(FollowingStartedEvent(id, followedId))
        }
    }

    fun on(event: FollowingStartedEvent) {
        log.debug("on: {}", event)
        followedList.add(event.followedId)
    }

    fun unfollow(followedId: UserId) {
        if (followedList.contains(followedId)) {
            apply(FollowingEndedEvent(id, followedId))
        }
    }

    fun on(event: FollowingEndedEvent) {
        log.debug("on: {}", event)
        followedList.remove(event.followedId)
    }

    fun addFollower(followerId: UserId) {
        if (!followersList.contains(followerId)) {
            apply(FollowerAddedEvent(id, followerId))
        }
    }

    fun on(event: FollowerAddedEvent) {
        log.debug("on: {}", event)
        followersList.add(event.followerId)
    }

    fun removeFollower(followerId: UserId) {
        if (followersList.contains(followerId)) {
            apply(FollowerRemovedEvent(id, followerId))
        }
    }

    fun on(event: FollowerRemovedEvent) {
        log.debug("on: {}", event)
        followersList.remove(event.followerId)
    }
}

data class Post(val userId: UserId, val text: String, val timestamp: LocalDateTime)
