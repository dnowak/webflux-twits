package twits

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class Configuration {
    @Bean
    fun eventRepository() = MemoryEventRepository()

    @Bean
    fun repositoryListener(eventRepository: EventRepository): EventListener = object : EventListener {
        override fun onEvent(event: Event) = eventRepository.add(event)
    }

    @Bean
    fun eventBus(listeners: List<EventListener>) = EventBus(listeners)

    @Bean
    fun userRepository(eventBus: EventBus, eventRepository: EventRepository) = UserRepository(eventBus, eventRepository)

    @Bean
    fun postRepository(eventBus: EventBus) = PostRepository(eventBus)

}