package twit.twit.web

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.web.reactive.function.server.router


@Configuration
class ApiRoutes(val userHandler: UserHandler) {

    @Bean
    fun apiRouter() = router {
        (accept(APPLICATION_JSON) and "/api").nest {
            GET("/users", userHandler::users)
            GET("/users/{name}", userHandler::user)
            PUT("/users/{name}", userHandler::addUser)
            GET("/users/{name}/wall", userHandler::wall)
            GET("/users/{name}/timeline", userHandler::timeline)
            POST("/users/{name}/posts", userHandler::addPost)
            "/users/{name}/followed".nest {
                PUT("/{other}", userHandler::follow)
                POST("/{other}", userHandler::follow)
                DELETE("/{other}", userHandler::unfollow)
            }
        }
    }
}

