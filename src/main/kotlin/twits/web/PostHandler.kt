package twits.web

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import twits.AggregateType
import twits.EventRepository
import twits.PostCreatedEvent
import twits.PostEvent

@Component
class PostHandler(private val eventRepository: EventRepository) {

    fun posts(req: ServerRequest): Mono<ServerResponse> {
        fun updatePost(post: Mono<PostOutput>, event: PostEvent): Mono<PostOutput> = when (event) {
            is PostCreatedEvent -> post.switchIfEmpty(PostOutput(event.author.name, event.text, event.timestamp.toString()).toMono())
            else -> post
        }

        val posts: Flux<PostOutput> = eventRepository.findByAggregateType(AggregateType.POST)
                .cast(PostEvent::class.java)
                .groupBy { it.aggregateId.id }
                .map { it.reduce(Mono.empty<PostOutput>(), ::updatePost).flatMap { it } }
                .flatMap { it }

        return ServerResponse.ok().json().body(posts)
    }
}