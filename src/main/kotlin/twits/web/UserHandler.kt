package twits.web

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_UTF8
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters.fromObject
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.badRequest
import org.springframework.web.reactive.function.server.ServerResponse.created
import org.springframework.web.reactive.function.server.ServerResponse.notFound
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import twits.AggregateId
import twits.AggregateType
import twits.Event
import twits.EventRepository
import twits.FollowerAddedEvent
import twits.FollowerRemovedEvent
import twits.FollowingEndedEvent
import twits.FollowingStartedEvent
import twits.Post
import twits.PostId
import twits.PostSentEvent
import twits.TwitService
import twits.UserCreatedEvent
import twits.UserEvent
import twits.UserId

fun ServerResponse.BodyBuilder.json(): ServerResponse.BodyBuilder = contentType(APPLICATION_JSON_UTF8)

@Component
open class UserHandler(private val eventRepository: EventRepository, private val service: TwitService) {

    fun users(req: ServerRequest): Mono<ServerResponse> = eventRepository.findByAggregateType(AggregateType.USER)
            .cast(UserEvent::class.java)
            .groupBy { it.aggregateId.id }
            .map { it.reduce(Mono.empty<UserOutput>(), {p, e -> updateUserOutput(p, e)}).flatMap { it } }
            .flatMap { it }
            .sort { (name1), (name2) -> name1.compareTo(name2) }
            .collectList()
            .flatMap { it -> ok().json().body(fromObject(it)) }

    fun user(req: ServerRequest): Mono<ServerResponse> = req.pathVariable("name").toMono()
            .map { name -> UserId(name) }
            .flatMap { id -> userProjection(id) }
            .flatMap { output -> ok().json().body(fromObject(output)) }
            .switchIfEmpty(notFound().build())

    fun addUser(req: ServerRequest): Mono<ServerResponse> = ok().body(fromObject("created"))

    fun updateUserOutput(projection: Mono<UserOutput>, event: Event): Mono<UserOutput> = when (event) {
        is UserCreatedEvent -> projection.switchIfEmpty(UserOutput(event.id.name, event.timestamp).toMono())
        is FollowerAddedEvent -> projection.map { it.copy(followerCount = it.followerCount + 1) }
        is FollowerRemovedEvent -> projection.map { it.copy(followerCount = it.followerCount - 1) }
        is FollowingStartedEvent -> projection.map { it.copy(followedCount = it.followedCount + 1) }
        is FollowingEndedEvent -> projection.map { it.copy(followedCount = it.followedCount - 1) }
        is PostSentEvent -> projection.map { it.copy(postCount = it.postCount + 1) }
        else -> projection
    }

    private fun userProjection(userId: UserId): Mono<UserOutput> {
        return eventRepository.findByAggregateId(AggregateId(AggregateType.USER, userId))
                .reduce(Mono.empty<UserOutput>(), { p, e -> updateUserOutput(p, e) })
                .flatMap { it }
    }

    companion object {
        private val log = LoggerFactory.getLogger(UserHandler::class.java)
    }

    fun wall(req: ServerRequest) = ok().json().body(
            service.wall(UserId(req.pathVariable("name")))
                    .map { post -> post.toOutput() })

    fun addPost(req: ServerRequest): Mono<ServerResponse> = req.bodyToMono(PostInput::class.java)
            .doOnNext { post -> log.debug("Adding post: {}", post) }
            .flatMap { input -> createPost(req.pathVariable("name"), input) }
            .flatMap { id -> created(req.uri().resolve(id.id.toString())).build() }
            .switchIfEmpty(badRequest().body(fromObject("""{ "error": "Text too long"}""")))

    fun follow(req: ServerRequest): Mono<ServerResponse> {
        val user = req.pathVariable("name")
        val other = req.pathVariable("other")
        log.debug("Set follow relationship from: $user to: $other.")
        service.follow(UserId(user), UserId(other))
        return ok().json().build()
    }

    fun unfollow(req: ServerRequest): Mono<ServerResponse> {
        val user = req.pathVariable("name")
        val other = req.pathVariable("other")
        service.unfollow(UserId(user), UserId(other))
        return ok().json().build()
    }

    private fun createPost(name: String, input: PostInput): Mono<PostId> =
            input.toMono()
                    .filter { it.text.length <= 140 }
                    .flatMap { service.post(UserId(name), it.text) }

    fun timeline(req: ServerRequest) = ok().json().body(
            service.timeline(UserId(req.pathVariable("name")))
                    .map { post -> post.toOutput() })

}

