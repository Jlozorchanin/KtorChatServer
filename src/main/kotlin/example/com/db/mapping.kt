package example.com.db


import example.com.model.User
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object UserTable : IntIdTable("user") {
    val name = varchar("name", 50)
    val username = varchar("username", 50).uniqueIndex()
    val friends = varchar("friends",255)
    val pic = varchar("pic", 255).nullable()

}

object UserPasswordTable : IntIdTable("user_password") {
    val userName = reference("userName", UserTable.username, onDelete = ReferenceOption.CASCADE)
    val password = varchar("password",50)
}



class UserDAO(id: EntityID<Int>) : IntEntity(id) {

    companion object : IntEntityClass<UserDAO>(UserTable)

    var name by UserTable.name
    var username by UserTable.username
    var friends by UserTable.friends
    var pic by UserTable.pic


}
suspend fun <T> suspendTransaction(block: (Transaction) -> T): T =
    newSuspendedTransaction(Dispatchers.IO, statement = block)


fun daoToModel(dao: UserDAO) = User(
    dao.name,
    dao.username,
    dao.friends,
    dao.id.value,
    dao.pic
)


fun createUserPassTable() {
    transaction {
        SchemaUtils.create(UserPasswordTable)
    }

}

fun dropUserPassTable() {
    transaction {
        SchemaUtils.drop(UserPasswordTable)
    }

}


fun createTable() {
    transaction {
        SchemaUtils.create(UserTable)
    }

}

fun dropTable() {
    transaction {
        SchemaUtils.drop(UserTable)
    }

}
