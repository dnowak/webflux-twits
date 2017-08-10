package twits

import org.apache.commons.lang3.builder.ToStringBuilder
import org.funktionale.tries.Try
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.toFlux
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList

interface EventRepository {
    fun add(event: Event)
    fun findByAggregateId(aggregateId: AggregateId): Flux<Event>
    fun findByAggregateType(type: AggregateType): Flux<Event>
}

class MemoryEventRepository() : EventRepository {
    companion object {
        private val log = LoggerFactory.getLogger(MemoryEventRepository::class.java)
    }
    private val events = CopyOnWriteArrayList<Event>()

    override fun add(event: Event) {
        log.debug("add: {}", event)
        events.add(event)
    }

    override fun findByAggregateId(aggregateId: AggregateId): Flux<Event> = events.toFlux()
            .filter { event -> event.aggregateId == aggregateId }
    override fun findByAggregateType(type: AggregateType): Flux<Event> = events.toFlux().filter { it.aggregateId.type == type }

    fun clear() {
        events.clear()
    }


}


interface EventListener {
    fun onEvent(event: Event)
}

class EventBus(val listeners: Collection<EventListener>) {
    fun publish(event: Event) {
        listeners.forEach { it.onEvent(event) }
    }
}

enum class AggregateType { USER, POST }
data class AggregateId(val type: AggregateType, val id: Any)

interface Event {
    val aggregateId: AggregateId
    val timestamp: LocalDateTime
}

abstract class UserEvent(val id: UserId) : Event {
    override val aggregateId = AggregateId(AggregateType.USER, id)
    override val timestamp = LocalDateTime.now()!!
    override fun toString(): String {
        return ToStringBuilder.reflectionToString(this)
    }
}

class UserCreatedEvent(id: UserId) : UserEvent(id)

class FollowerAddedEvent(id: UserId, val followerId: UserId) : UserEvent(id)
class FollowerRemovedEvent(id: UserId, val followerId: UserId) : UserEvent(id)

class FollowingStartedEvent(id: UserId, val followedId: UserId) : UserEvent(id)
class FollowingEndedEvent(id: UserId, val followedId: UserId) : UserEvent(id)

class PostSentEvent(id: UserId, val message: String): UserEvent(id)
class PostReceivedEvent(id: UserId, val author: UserId, val message: String): UserEvent(id)

open class PostEvent(val id: PostId) : Event {

    override val aggregateId = AggregateId(AggregateType.POST, id)
    override val timestamp = LocalDateTime.now()!!
}

class PostCreatedEvent(id: PostId, val author: UserId, val text: String): PostEvent(id)

fun <E : Any, T : Any> on(target: T, event: E): T {
    Try({ target.javaClass.getMethod("on", event.javaClass) }).map { m -> m.invoke(target, event) }
    return target
}
