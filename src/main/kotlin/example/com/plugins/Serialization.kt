package example.com.plugins


import example.com.db.createTable
import example.com.db.createUserPassTable
import example.com.db.dropTable
import example.com.model.RegExample
import example.com.model.User
import example.com.model.UserAdv
import example.com.model.UserRepository
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.reflect.*
import kotlinx.serialization.Required
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.encodeToJsonElement

fun Application.configureSerialization(repository: UserRepository) {
    install(ContentNegotiation) {
        json()
    }

    routing {
        route("/createTable") {
            get{
//                createTable()
                createUserPassTable()
                call.respond("BD created successfully")
            }

        }
        route("/dropTable") {
            get{
                dropTable()
                call.respond(HttpStatusCode.OK,"DB dropped successfully")
            }

        }

        route("/reg"){
            post {
                try {
                    val account = call.receive<UserAdv>()
                    try {
                        val isOk = repository.addUser(account.user)
                        if (isOk) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.BadRequest,
                            message = "User with username ${account.user.username} already exists")
                        repository.setUserPassword(account.user,account.password)

                    } catch (ex: IllegalStateException) {
                        call.respond(HttpStatusCode.BadRequest)
                    } catch (ex: JsonConvertException) {
                        call.respond(HttpStatusCode.BadRequest)
                    }
                } catch (e:Exception){
                    call.respond(HttpStatusCode.BadGateway,
                        Json.encodeToJsonElement(RegExample.serializer(),RegExample(message = "To create an account make post req with body like response below",
                        UserAdv(user = User(name = "name",username = "username", friends = " ",id = null,profileImageUrl = null),
                            password = "password"))))
                }


            }
        }

        route("/users") {
            get {
                val users = repository.allUsers()
                call.respond(hashMapOf("users" to users))
            }



            get("/{userId}"){
                val userId = call.parameters["userId"]?: ""
                if (userId == "") {
                    return@get call.respond(HttpStatusCode.BadRequest)
                }
                try {
                    call.respond(repository.findUser(userId.toInt()))
                } catch (e:NumberFormatException){
                    call.respond(repository.findUser(userId))
                } catch (e:Exception){
                    return@get call.respond(HttpStatusCode.BadRequest)
                }



            }



            post("/{userId}/setpic"){
                val userId = call.parameters["userId"]
                val pic = call.parameters["pic"] ?: ""
                if (userId == "" || pic == "") {
                    return@post call.respond(HttpStatusCode.BadRequest,"pic parameter cannot be empty")
                }
                if (repository.setUserPicture(userId!!.toInt(),pic)) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NoContent)

            }
            post ("/edit/{userId}"){
                val userId = call.parameters["userId"]

                val user = call.receive<User>()
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest,"provide user id")
                    return@post
                }
                repository.editUser(userId,user)
                call.respond(HttpStatusCode.NoContent)

            }
            post("/edit/{username}/password"){
                val username = call.parameters["username"]?: ""
                val oldPassword = call.parameters["oldPassword"]?: ""
                val newPassword = call.parameters["newPassword"]?: ""
                if (username == ""){
                    call.respond(HttpStatusCode.BadGateway)
                    return@post
                }
                if (oldPassword == "" || newPassword == "" ){
                    call.respond(HttpStatusCode.BadGateway,"newPassword and oldPassword are required as a query's")
                    return@post
                }
                if (newPassword == oldPassword) {
                    call.respond(HttpStatusCode.BadGateway,"newPassword and oldPassword can't be similar")
                }
                if (repository.changePassword(username,oldPassword,newPassword)) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.BadGateway,"Incorrect password")

            }

            get("/{userId}/friends"){
                val userId = call.parameters["userId"]?.toInt() ?: -1
                if (userId == -1) {
                    return@get call.respond(HttpStatusCode.BadRequest, "User doesn't exist")
                }
                call.respond(repository.getUserFriendsList(userId))
            }

            get("/{userId}/friends/{friendId}"){
                val userId = call.parameters["userId"]?.toInt() ?: -1
                val friendId = call.parameters["friendId"]?.toInt() ?: -1
                if (userId == -1) {
                    return@get call.respond(HttpStatusCode.BadRequest, "User doesn't exist")
                }
                if (friendId == -1) {
                    return@get call.respond(HttpStatusCode.BadRequest,"There is no friend with id $friendId")
                }
                val friendsList = repository.getUserFriendsList(userId)
                if (friendId.toString() in friendsList ){
                    call.respond(repository.findUser(friendId))

                }
                if (friendId.toString() !in friendsList){
                    call.respond(HttpStatusCode.BadGateway,"There is no matching friend with id $friendId")
                }


            }

            post ("/{userId}/friends/{friendId}"){
                val userId = call.parameters["userId"]
                val friendId = call.parameters["friendId"]
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                if (friendId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                if (friendId==userId){
                    call.respond(HttpStatusCode.BadRequest,"You can't friend with your second personality, sorry. CHECK ID's")
                    return@post
                }
                if (repository.addFriend(userId,friendId)){
                    call.respond("done")
                } else {
                    call.respond("It seems that you've already added id$friendId as a friend")
                }

            }

            delete ("/{userId}/friends/{friendId}"){
                val userId = call.parameters["userId"]
                val friendId = call.parameters["friendId"]
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@delete
                }
                if (friendId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@delete
                }
                if (friendId==userId){
                    call.respond(HttpStatusCode.BadRequest,"Good try")
                    return@delete
                }
                if(repository.removeFriend(userId,friendId)){
                    call.respond("done")
                } else{
                    call.respond("It seems that you've already removed id$friendId from your friends list")
                }

            }

            delete("/{userId}") {
                val userId = call.parameters["userId"]
                val userPassword = call.parameters["password"]
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@delete
                }
                if (userId.toInt() == -1) {
                    call.respond(HttpStatusCode.BadRequest,"There is no user with id $userId to delete" )
                }
                val username = repository.findUser(userId).username
                if (repository.removeUser(userId.toInt(),username)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }


}
