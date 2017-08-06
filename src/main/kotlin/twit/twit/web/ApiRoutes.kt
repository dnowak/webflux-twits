package twit.twit.web

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.web.reactive.function.server.router


@Configuration
class ApiRoutes(val userHandler: UserHandler, val postHandler: PostHandler) {

    @Bean
    fun apiRouter() = router {
        (accept(APPLICATION_JSON) and "/api").nest {
            "/users".nest {
                GET("/", userHandler::findAll)
                "/{name}".nest {
                    GET("/", userHandler::findOne)
                    PUT("/", userHandler::create)
                    GET("/posts", postHandler::findAll)
                    POST("/posts", postHandler::create)
                    GET("/timeline", postHandler::timeline)
                    /*
                    "/followed".nest {
                        PUT("/{other}", followedHandler::add)
                        DELETE("/{other}", followedHandler::delete)
                    }
                    */
                }
            }
        }
    }
}

