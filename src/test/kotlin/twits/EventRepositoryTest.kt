package twits

import org.junit.Assert
import org.junit.Test

class EventRepositoryTest {
    @Test
    fun `finds events by UserId`() {
        //given
        val repository = EventRepository()
        repository.add(UserCreatedEvent(UserId("ala")))
        repository.add(UserCreatedEvent(UserId("ola")))
        repository.add(FollowerAddedEvent(UserId("ala"), UserId("ola")))
        repository.add(UserCreatedEvent(UserId("ula")))
        repository.add(FollowerAddedEvent(UserId("ala"), UserId("ula")))
        repository.add(PostSentEvent(UserId("ula"), "Hello from Ula!"))
        repository.add(PostSentEvent(UserId("ala"), "Hello from Ala!"))

        //when
        val events = repository.findByAggregateId(AggregateId(AggregateType.USER, UserId("ala")))
                .collectList().block()!!
        //then
        Assert.assertTrue(events.all { e -> e.aggregateId == AggregateId(AggregateType.USER, UserId("ala")) })
        Assert.assertEquals(4, events.size)
    }

}