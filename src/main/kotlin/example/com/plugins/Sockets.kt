package example.com.plugins

import example.com.model.User
import example.com.model.UserRepository
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.setSerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

class MainFunc(private val repository: UserRepository){
    private var users = listOf<User>()

    init {
        users = runBlocking { repository.allUsers() }
    }

    private suspend fun getUserFriends(userId: Int): List<String> {
        return repository.getUserFriendsList(userId)
    }

    suspend fun checkPassword(username:String,password: String): Boolean{
        return repository.checkUserPassword(username,password)
    }



    val activeUsers = ConcurrentHashMap<Int, UserSession>()

    suspend fun getActiveFriends(userId: Int){
        val friends = getUserFriends(userId)
        val onlineFriends = mutableListOf<String>()
        friends.forEach {
            if (it.toInt() in activeUsers.keys){
                onlineFriends.add(it)
            }
        }
        activeUsers[userId]?.socket?.send(Frame.Text(mapOf("friends online" to onlineFriends).toString()))

    }

    suspend fun broadcastUserStatus(userId: Int, isOnline: Boolean) {
        val friends = getUserFriends(userId)
        for (friend in friends) {
            if (friend.toInt() in activeUsers.keys){
                activeUsers[friend.toInt()]?.socket?.send(Frame.Text((mapOf("friendId" to userId,"isOnline" to isOnline)).toString()))
            }
        }
    }

    suspend fun handleMessage(userId: Int, message: String, sendTo: Int) {
        try {
            activeUsers.getValue(sendTo).socket.send(Frame.Text((mapOf("msg" to message,"sender" to userId)).toString()))
        } catch (e:NoSuchElementException){
            activeUsers.getValue(userId).socket.send(Frame.Text("There is no user with username $sendTo"))
        } catch (e:Exception){
            activeUsers.getValue(userId).socket.send(Frame.Text(e.localizedMessage))
        }
    }

}

fun Application.configureSockets(repository: UserRepository) {
    val mainClass = MainFunc(repository)
    install(WebSockets) {
        contentConverter =KotlinxWebsocketSerializationConverter(Json)
        pingPeriod = Duration.ofSeconds(35)
        timeout = Duration.ofSeconds(35)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        webSocket("/chat") { // websocketSession
            val username = call.parameters["username"] ?: "unknown"
            val password = call.parameters["password"] ?: "unknown"

            if (username=="unknown" || password=="unknown") {
                this.send(Frame.Text("You should pass your username and password as a query's"))
                close(CloseReason(CloseReason.Codes.NORMAL,"You should pass your username and password as a query's"))
            }
            if(!mainClass.checkPassword(username,password)){
                this.send(Frame.Text("Invalid username or password, for setting up password make post to /reg page"))
                close(CloseReason(CloseReason.Codes.NORMAL,"Invalid username or password, for for setting up password make post to /reg page"))
            }

            val userId = repository.findUser(username).id

            val userSession = UserSession(username, this)
            mainClass.activeUsers[userId!!] = userSession
            mainClass.broadcastUserStatus(userId, true)
            mainClass.getActiveFriends(userId)
            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val message = frame.readText()
                            try {
                                val d = Json.decodeFromString<Data>(message)
                                mainClass.handleMessage(userId, d.msg, d.sendTo)
                            }
                            catch (e:Exception){
                                this.send(Frame.Text(e.localizedMessage))
                            }
                            // Обработка входящего сообщения

                        }
                        else -> TODO()
                    }
                }
            } finally {
                // Удаляем пользователя из активных сессий при отключении
                mainClass.activeUsers.remove(userId)
                mainClass.broadcastUserStatus(userId, false)
            }


        }
    }
}


@Serializable
class Data(val msg: String,val sendTo:Int)

@Serializable
data class UserSession(val username: String, val socket: WebSocketSession)
