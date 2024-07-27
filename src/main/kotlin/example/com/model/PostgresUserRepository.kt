package example.com.model


import example.com.db.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import java.util.StringTokenizer


class PostgresUserRepository : UserRepository {
    override suspend fun allUsers(): List<User> = suspendTransaction {
        UserDAO.all().map(::daoToModel)
    }


    override suspend fun addUser(user : User): Boolean = suspendTransaction {
        val userEx = runBlocking {findUser(user.username)  }
        if (userEx.id == -1) {
            UserDAO.new {
                name = user.name
                username = user.username
                friends = user.friends
            }
            return@suspendTransaction true
        }
        else {
            return@suspendTransaction false
        }

    }

    override suspend fun removeUser(id: Int,username: String): Boolean = suspendTransaction {
        var rowsDeleted = 0

        rowsDeleted = UserTable.deleteWhere {
            UserTable.id eq id
        }
        UserPasswordTable.deleteWhere {
            UserPasswordTable.userName eq username
        }

        rowsDeleted == 1



    }

    override suspend fun editUser(userId: String,edit:User): Unit = suspendTransaction {
        UserTable.update({ UserTable.id eq userId.toInt() }) {
            it[name] = edit.name
            it[username] = edit.username
            it[friends] = edit.friends
            it[pic] = edit.profileImageUrl
        }

    }

    override suspend fun getUserFriendsList(userId: Int): List<String> = suspendTransaction {
        val localFriends = UserDAO
                .find { UserTable.id eq userId }
                .limit(1)
                .map(::daoToModel)
                .firstOrNull()!!.friends

        val tokenizer = StringTokenizer(localFriends)
        try {
            return@suspendTransaction Array(tokenizer.countTokens()) { tokenizer.nextToken() }.toMutableList()

        } catch (e:Exception){
            println(e.localizedMessage)

        }
        return@suspendTransaction emptyList()
    }

    override suspend fun addFriend(userId: String, id: String): Boolean = suspendTransaction {
        try {
            val localFriends = UserDAO
                .find { UserTable.id eq userId.toInt() }
                .limit(1)
                .map(::daoToModel)
                .firstOrNull()?.friends ?: ""

            val tokenizer = StringTokenizer(localFriends)
            val actualArr = Array(tokenizer.countTokens()) { tokenizer.nextToken() }
            val actualList = actualArr.toMutableList()
            if (id in actualList){
                return@suspendTransaction false
            }

            UserTable.update({UserTable.id eq userId.toInt()}, body = {
                it[friends] = "$localFriends $id"
            })
            return@suspendTransaction true
        } catch (e:Exception){
            return@suspendTransaction false
        }

    }

    override suspend fun removeFriend(userId: String, id : String): Boolean = suspendTransaction{
        val localFriends = UserDAO
            .find { UserTable.id eq userId.toInt() }
            .limit(1)
            .map(::daoToModel)
            .firstOrNull()!!.friends
        val tokenizer = StringTokenizer(localFriends)
        try {
            val actualArr = Array(tokenizer.countTokens()) { tokenizer.nextToken() }
            val actualList = actualArr.toMutableList()
            if (id in actualList){
                actualList.remove(id)
            } else{
                return@suspendTransaction false
            }

            UserTable.update({UserTable.id eq userId.toInt()}, body = {
                it[friends] = actualList.toString().dropLast(1).drop(1).replace(","," ")
            })
            return@suspendTransaction true
        } catch (e:Exception){
            println(e.localizedMessage)
            return@suspendTransaction false
        }


    }


    override suspend fun findUser(username: String): User = suspendTransaction {
        UserDAO
            .find { UserTable.username eq username }
            .limit(1)
            .map(::daoToModel)
            .firstOrNull()?: User("","-1","",-1,"")

    }

    override suspend fun findUser(id: Int): User  = suspendTransaction{
        UserDAO
            .find { UserTable.id eq id }
            .limit(1)
            .map(::daoToModel)
            .firstOrNull()?: User("","-1","",-1,"")

    }

    override suspend fun setUserPassword(user: User, password: String): Unit = suspendTransaction {
        val userCurrent = UserTable.select(UserTable.username eq user.username).singleOrNull()
        if (userCurrent != null) {
            UserPasswordTable.insert {
                it[UserPasswordTable.password] = password
                it[UserPasswordTable.userName] = user.username
            }
        }

    }

    override suspend fun checkUserPassword(username: String, password: String): Boolean = suspendTransaction{
        val user = UserTable.select { UserTable.username eq username }.singleOrNull()
        if (user != null) {
            val truePassword = UserPasswordTable.select { UserPasswordTable.userName eq username }.limit(1)
                .map { it[UserPasswordTable.password] }
                .firstOrNull()
            return@suspendTransaction truePassword == password
        } else {
            return@suspendTransaction false
        }

    }

    override suspend fun changePassword(username: String, oldPassword: String, newPassword: String): Boolean = suspendTransaction{
        val user = UserTable.select { UserTable.username eq username }.singleOrNull()
        if (user != null) {
            val truePassword = UserPasswordTable.select { UserPasswordTable.userName eq username }
                .map { it[UserPasswordTable.password] }
                .singleOrNull()
            if (truePassword != oldPassword){
                return@suspendTransaction false
            } else{
                UserPasswordTable.update({UserPasswordTable.userName eq username },
                    body = {it[password] = newPassword
                 })
                return@suspendTransaction true
            }
        } else {
            println("Пользователь с именем $username не найден.")
            return@suspendTransaction false
        }


    }

    override suspend fun setUserPicture(userId: Int, image: String): Boolean = suspendTransaction{
        try {
            UserTable.update({UserTable.id eq userId}, body = {
                it[pic] = image
            })
            return@suspendTransaction true
        } catch (e:Exception){
            return@suspendTransaction false
        }
    }

}


