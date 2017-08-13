package twits.web

import twits.Post
import java.time.LocalDateTime

data class PostInput(val text: String)

data class UserOutput(val name: String,
                      val created: LocalDateTime,
                      val followedCount: Int = 0,
                      val followerCount: Int = 0,
                      val postCount: Int = 0)

data class PostOutput(val author: String, val text: String, val timestamp: String)

fun Post.toOutput(): PostOutput = PostOutput(userId.name, text, timestamp.toString())
