package example.com.model

import kotlinx.serialization.Serializable
import javax.annotation.processing.Messager

//enum class Priority {
//    Low, Medium, High, Vital
//}
//
//@Serializable
//data class Task(
//    val name: String,
//    val description: String,
//    val priority: Priority
//)

@Serializable
data class User(
    val name: String,
    val username: String,
    val friends : String = "",
    val id : Int,
    val profileImageUrl : String? = "-"
)
@Serializable
data class UserAdv(
    val user : User,
    val password : String
)

@Serializable
data class RegExample( // for /reg invalid requests
    val message : String,
    val response : UserAdv
)