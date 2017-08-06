package twit.twit.web

import org.springframework.http.MediaType.APPLICATION_JSON_UTF8
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters.fromObject
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import twit.twit.AggregateId
import twit.twit.AggregateType
import twit.twit.EventRepository
import twit.twit.UserCreatedEvent
import twit.twit.UserId
import java.time.LocalDateTime

fun ServerResponse.BodyBuilder.json() = contentType(APPLICATION_JSON_UTF8)

@Component
open class UserHandler(private val eventRepository: EventRepository) {
    fun findAll(req: ServerRequest) = ServerResponse.ok().json().body(fromObject("find all"))
    fun findOne(req: ServerRequest) = req.pathVariable("name").toMono()
            .flatMap { name -> findOne(name) }
            .flatMap { projection -> projection.toOutput() }
            .flatMap { output -> ServerResponse.ok().json().body(fromObject(output)) }
            .switchIfEmpty(ServerResponse.notFound().build())

    //    ServerResponse.ok().body(fromObject("find: ${req.pathVariable("name")}"))
    fun create(req: ServerRequest) = ServerResponse.ok().body(fromObject("created"))

    /*
    fun findOne(req: ServerRequest) = ServerResponse.ok().json().body(repository.findOne(req.pathVariable("login")))

    fun findAll(req: ServerRequest) = ServerResponse.ok().json().body(repository.findAll())

    fun findStaff(req: ServerRequest) = ServerResponse.ok().json().body(repository.findByRole(Role.STAFF))

    fun findOneStaff(req: ServerRequest) = ServerResponse.ok().json().body(repository.findOneByRole(req.pathVariable("login"), Role.STAFF))

    fun create(req: ServerRequest) = repository.save(req.bodyToMono<User>()).flatMap {
        ServerResponse.created(URI.create("/api/user/${it.login}")).json().body(it.toMono())
    }
    */

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

