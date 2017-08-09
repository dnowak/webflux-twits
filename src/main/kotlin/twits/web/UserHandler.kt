package twits.web

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_UTF8
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters.fromObject
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import twits.AggregateId
import twits.AggregateType
import twits.EventRepository
import twits.Post
import twits.PostId
import twits.TwitService
import twits.UserCreatedEvent
import twits.UserId
import twits.on
import java.time.LocalDateTime

fun ServerResponse.BodyBuilder.json() = contentType(APPLICATION_JSON_UTF8)

@Component
open class UserHandler(private val eventRepository: EventRepository, private val service: TwitService) {
    data class PostOutput(val author: String, val text: String, val timestamp: String)

    fun Post.toOutput(): PostOutput = PostOutput(userId.name, text, timestamp.toString())

    data class PostInput(val text: String)

    fun users(req: ServerRequest): Mono<ServerResponse> = ServerResponse.ok().json().body(fromObject("find all"))

    fun user(req: ServerRequest): Mono<ServerResponse> = req.pathVariable("name").toMono()
            .map { name -> UserId(name) }
            .flatMap { id -> userProjection(id) }
            .flatMap { projection -> projection.toOutput() }
            .flatMap { output -> ServerResponse.ok().json().body(fromObject(output)) }
            .switchIfEmpty(ServerResponse.notFound().build())

    fun addUser(req: ServerRequest): Mono<ServerResponse> = ServerResponse.ok().body(fromObject("created"))

    private fun userProjection(userId: UserId) = eventRepository
            .findByAggregateId(AggregateId(AggregateType.USER, userId))
            .reduce(UserProjection(), { p, e -> on(p, e) })

    data class UserOutput(val name: String, val created: LocalDateTime)
    class UserProjection() {
        var id: UserId? = null
        var created: LocalDateTime? = null

        fun on(event: UserCreatedEvent) {
            id = event.id
            created = event.timestamp
        }

        fun toOutput(): Mono<UserOutput> = when (id != null) {
            true -> Mono.just(UserOutput(id!!.name, created!!))
            false -> Mono.empty()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(UserHandler::class.java)
    }

    fun wall(req: ServerRequest) = ServerResponse.ok().json().body(
            service.wall(UserId(req.pathVariable("name")))
                    .map { post -> post.toOutput() })

    fun addPost(req: ServerRequest) = req.bodyToMono(PostInput::class.java)
            .doOnNext { post -> log.debug("Adding post: {}", post) }
            .flatMap { input -> createPost(req.pathVariable("name"), input) }
            .flatMap { id -> ServerResponse.created(req.uri().resolve(id.id.toString())).build() }

    fun follow(req: ServerRequest): Mono<ServerResponse>  {
        val user = req.pathVariable("name")
        val other = req.pathVariable("other")
        log.debug("Set follow relationship from: $user to: $other.")
        service.follow(UserId(user), UserId(other))
        return ServerResponse.ok().json().build()
    }
    
    fun unfollow(req: ServerRequest): Mono<ServerResponse> {
        val user = req.pathVariable("name")
        val other = req.pathVariable("other")
        service.unfollow(UserId(user), UserId(other))
        return ServerResponse.ok().json().build()
    }

    private fun createPost(name: String, input: PostInput): Mono<PostId> =
            service.post(UserId(name), input.text)

    fun timeline(req: ServerRequest) = ServerResponse.ok().json().body(
            service.timeline(UserId(req.pathVariable("name")))
                    .map { post -> post.toOutput() })

}

