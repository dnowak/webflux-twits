package twit.twit.web

import org.funktionale.tries.Try
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters.fromObject
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse.created
import org.springframework.web.reactive.function.server.ServerResponse.notFound
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import twit.twit.AggregateId
import twit.twit.AggregateType
import twit.twit.EventRepository
import twit.twit.PostId
import twit.twit.TwitService
import twit.twit.UserCreatedEvent
import twit.twit.UserId
import java.time.LocalDateTime


@Component
open class PostHandler(private val eventRepository: EventRepository, private val service: TwitService) {
    companion object {
        private val log = LoggerFactory.getLogger(PostHandler::class.java)
    }
    fun findAll(req: ServerRequest) = ok().json().body(service.wall(UserId(req.pathVariable("name"))))
    fun findOne(req: ServerRequest) = req.pathVariable("name").toMono()
            .flatMap { name -> findOne(name) }
            .flatMap { projection -> projection.toOutput() }
            .flatMap { output -> ok().json().body(fromObject(output)) }
            .switchIfEmpty(notFound().build())

    //    ServerResponse.ok().body(fromObject("find: ${req.pathVariable("name")}"))
    fun create(req: ServerRequest) = req.bodyToMono(PostInput::class.java)
            .doOnNext { post -> log.debug("Adding post: {}", post)}
            .flatMap { input -> createPost(req.pathVariable("name"), input) }
            .flatMap { id -> created(req.uri().resolve(id.id.toString())).build() }

    private fun createPost(name: String, input: PostHandler.PostInput): Mono<PostId> =
            service.post(UserId(name), input.text)


    //    ServerResponse.ok().body(fromObject("created"))
    fun timeline(req: ServerRequest) = ok().json().body(fromObject("find all"))

    /*
    fun findOne(req: ServerRequest) = ServerResponse.ok().json().body(repository.findOne(req.pathVariable("login")))

    fun findAll(req: ServerRequest) = ServerResponse.ok().json().body(repository.findAll())

    fun findStaff(req: ServerRequest) = ServerResponse.ok().json().body(repository.findByRole(Role.STAFF))

    fun findOneStaff(req: ServerRequest) = ServerResponse.ok().json().body(repository.findOneByRole(req.pathVariable("login"), Role.STAFF))

    fun create(req: ServerRequest) = repository.save(req.bodyToMono<User>()).flatMap {
        ServerResponse.created(URI.create("/api/user/${it.login}")).json().body(it.toMono())
    }
    */

    data class PostInput(val text: String) {

    }

    private fun findOne(name: String) = eventRepository
            .findByAggregateId(AggregateId(AggregateType.USER, name))
            .reduceWith({ UserProjection() }, { p, e -> on(p, e) })

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
}

fun <E : Any, T : Any> on(target: T, event: E): T {
    
    Try ({target.javaClass.getMethod("on", event.javaClass)}).map { m -> m.invoke(target, event) }
    return target
}