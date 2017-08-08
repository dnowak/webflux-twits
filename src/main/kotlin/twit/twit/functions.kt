package twit.twit

import org.funktionale.tries.Try

fun <E : Any, T : Any> on(target: T, event: E): T {
    Try({ target.javaClass.getMethod("on", event.javaClass) }).map { m -> m.invoke(target, event) }
    return target
}
