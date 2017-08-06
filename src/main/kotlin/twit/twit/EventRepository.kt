package twit.twit

import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.toFlux
import java.time.LocalDateTime
import java.util.LinkedList

class EventRepository() {
    companion object {
        private val log = LoggerFactory.getLogger(EventRepository::class.java)
    }
    private val events = LinkedList<Event>()

    fun add(event: Event) {
        log.debug("add: {}", event)
        events.add(event)
    }

    fun findByAggregateId(id: AggregateId): Flux<Event> = events.toFlux().filter { it.aggregateId == id }
    fun findByAggregateType(type: AggregateType): Flux<Event> = events.toFlux().filter { it.aggregateId.type == type }
}


interface EventListener {
    fun onEvent(event: Event)
}

class EventBus(val listeners: Collection<EventListener>) {
    fun publish(event: Event) {
        listeners.forEach { it.onEvent(event) }
    }
}

interface Event {
    val aggregateId: AggregateId
    val timestamp: LocalDateTime
}

open class UserEvent(val id: UserId) : Event {
    override val aggregateId = AggregateId(AggregateType.USER, id)
    override val timestamp = LocalDateTime.now()!!

}

open class PostEvent(val id: PostId) : Event {
    override val aggregateId = AggregateId(AggregateType.POST, id)
    override val timestamp = LocalDateTime.now()!!

}

class UserCreatedEvent(id: UserId) : UserEvent(id)

class PostSentEvent(id: UserId, val message: String): UserEvent(id)
class PostReceivedEvent(id: UserId, val author: String, val message: String): UserEvent(id)

class PostCreatedEvent(id: PostId, val publisher: UserId, val text: String): PostEvent(id)

enum class AggregateType { USER, POST }
data class AggregateId(val type: AggregateType, val id: Any)

