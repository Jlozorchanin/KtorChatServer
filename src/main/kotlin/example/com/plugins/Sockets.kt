package example.com.plugins

import example.com.model.User
import example.com.model.UserRepository
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.websocket.*
import io.ktor.websocket.serialization.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.encodeToString
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.NoSuchElementException

@Serializable
data class SocketAction(
    val event : Events? = Events.TIME,
    val content : String? = ""
)
enum class Events{
    NEW_MSG, CURRENT_ONLINE, SYSTEM_MSG, USER_CHANGE_ONLINE_STATUS, TIME, GET_ONLINE_USER_DETAILS
}

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

    @OptIn(InternalAPI::class)
//    suspend fun getActiveFriendsAndUsers(userId: Int){
//        val friends = getUserFriends(userId)
//        val onlineFriends = mutableListOf<Int>()
//        friends.forEach {
//            if (it.toInt() in activeUsers.keys){
//                onlineFriends.add(it.toInt())
//            }
//        }
//        delay(150)
//        activeUsers[userId]!!.socket.sendSerializedBase(
//                SocketAction(
//                    event = Events.CURRENT_ONLINE,
//                    content = mapOf("u_online" to activeUsers.keys,"f_online" to onlineFriends).toString()
//                ),
//                typeInfo = typeInfo<SocketAction>(),
//                KotlinxWebsocketSerializationConverter(Json), charset = Charsets.UTF_8)
//
//    }


    suspend fun broadcastUserStatus(userId: Int, isOnline: Boolean) {
        val friends = getUserFriends(userId)
        for (session in activeUsers.values) {
            session.socket.sendSerializedBase(
                SocketAction(
                    event = Events.USER_CHANGE_ONLINE_STATUS,
                    content = mapOf(userId to isOnline).toString()
                ),
                typeInfo = typeInfo<SocketAction>(),
                KotlinxWebsocketSerializationConverter(Json), charset = Charsets.UTF_8
            )

        }

    }

    @OptIn(InternalAPI::class)
    suspend fun handleMessage(userId: Int, message: String, sendTo: Int, time : Long) {
        try {
            activeUsers.getValue(sendTo).socket.sendSerializedBase(
                SocketAction(
                    event = Events.NEW_MSG,
                    content = encodeToString(
                        MessageReceiver.serializer(),
                        MessageReceiver(
                            msg = message,
                            sendTo = sendTo,
                            sendFrom = userId,
                            time = time
                        )
                    )
                    ),
                typeInfo = typeInfo<SocketAction>(),
                KotlinxWebsocketSerializationConverter(Json), charset = Charsets.UTF_8
            )

//            activeUsers.getValue(sendTo).socket.send(Frame.Text((mapOf("msg" to message,"sender" to userId)).toString()))

        } catch (e:NoSuchElementException){
            activeUsers.getValue(userId).socket.sendSerializedBase(
                SocketAction(
                    Events.SYSTEM_MSG,
                    "There is no user with username $sendTo"
                ),
                typeInfo = typeInfo<SocketAction>(),
                KotlinxWebsocketSerializationConverter(Json), charset = Charsets.UTF_8
            )

//            activeUsers.getValue(userId).socket.send(Frame.Text("There is no user with username $sendTo"))

        } catch (e:Exception){
            activeUsers.getValue(userId).socket.sendSerializedBase(
                SocketAction(
                    Events.SYSTEM_MSG,
                    e.localizedMessage
                ),
                typeInfo = typeInfo<SocketAction>(),
                KotlinxWebsocketSerializationConverter(Json), charset = Charsets.UTF_8
            )
//            activeUsers.getValue(userId).socket.send(Frame.Text(e.localizedMessage))

        }
    }

}

@OptIn(InternalAPI::class)
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
                this.sendSerializedBase(
                    SocketAction(
                        Events.SYSTEM_MSG,
                        "You should pass your username and password as a query's"
                    ),
                    typeInfo = typeInfo<SocketAction>(),
                    KotlinxWebsocketSerializationConverter(Json), charset = Charsets.UTF_8
                )
                close(CloseReason(CloseReason.Codes.NORMAL,"You should pass your username and password as a query's"))
            }


            if(!mainClass.checkPassword(username,password)){
                this.sendSerializedBase(
                    SocketAction(
                        Events.SYSTEM_MSG,
                        "Invalid password"
                    ),
                    typeInfo = typeInfo<SocketAction>(),
                    KotlinxWebsocketSerializationConverter(Json), charset = Charsets.UTF_8
                )
                close(CloseReason(CloseReason.Codes.NORMAL,"Invalid password"))
            }
            val user = repository.findUser(username)

            val userSession = UserSession(username, this)
            mainClass.activeUsers[user.id!!] = userSession
            mainClass.broadcastUserStatus(user.id, true)
            val job = launch {
                while (true){
                    sendSerializedBase(
                        SocketAction(
                            Events.TIME,
                            "${(System.currentTimeMillis()/1000)}"
                        ),
                        typeInfo = typeInfo<SocketAction>(),
                        KotlinxWebsocketSerializationConverter(Json), charset = Charsets.UTF_8

                    )
                    delay(1000)
                }
            }

            val job2 = launch {
                while (true){
                    sendSerializedBase(
                        SocketAction(
                            Events.CURRENT_ONLINE,
                            "${mainClass.activeUsers.keys}"
                        ),
                        typeInfo = typeInfo<SocketAction>(),
                        KotlinxWebsocketSerializationConverter(Json), charset = Charsets.UTF_8

                    )
                    delay(3000)
                }
            }
            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val message = frame.readText()
                            try {
                                val r = Json.decodeFromString<SocketAction>(message)
                                when(r.event){
                                    Events.NEW_MSG -> {
                                        val msg = r.content?.let { Json.decodeFromString<MessageReceiver>(it) }
                                        mainClass.handleMessage(user.id, msg!!.msg, msg.sendTo, msg.time)
                                    }
                                    Events.CURRENT_ONLINE -> TODO()
                                    Events.SYSTEM_MSG -> TODO()
                                    Events.USER_CHANGE_ONLINE_STATUS -> TODO()
                                    Events.TIME -> TODO()
                                    Events.GET_ONLINE_USER_DETAILS -> {

                                        val response = r.content.toString().replace(",","").dropLast(1).drop(1)
                                        val tokenizer = StringTokenizer(response)
                                        val actualArr = Array(tokenizer.countTokens()) { tokenizer.nextToken() }
                                        this.sendSerializedBase(
                                            SocketAction(
                                                Events.GET_ONLINE_USER_DETAILS,
                                                "users:${
                                                    encodeToString(
                                                    ListSerializer(User.serializer()),
                                                    repository.getOnlineUsersDetails(actualArr)
                                                )
                                                }"

                                            ),
                                            typeInfo = typeInfo<SocketAction>(),
                                            KotlinxWebsocketSerializationConverter(Json), charset = Charsets.UTF_8

                                        )
                                    }
                                    null -> TODO()
                                }


                            }
                            catch (e:Exception){
                                this.sendSerializedBase(
                                    SocketAction(
                                        Events.SYSTEM_MSG,
                                        e.localizedMessage
                                    ),
                                    typeInfo = typeInfo<SocketAction>(),
                                    KotlinxWebsocketSerializationConverter(Json), charset = Charsets.UTF_8
                                )
                            }
                            // Обработка входящего сообщения

                        }
                        else -> TODO()
                    }
                }
            } finally {
                // Удаляем пользователя из активных сессий при отключении
                mainClass.activeUsers.remove(user.id)
                mainClass.broadcastUserStatus(user.id, false)
            }


        }
    }
}


@Serializable
class MessageReceiver(val msg: String, val sendTo:Int, val sendFrom : Int, val time : Long)

@Serializable
data class UserSession(val username: String, val socket: WebSocketSession)
