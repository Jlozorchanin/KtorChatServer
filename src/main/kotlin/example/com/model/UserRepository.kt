package example.com.model



interface UserRepository {
    suspend fun allUsers(): List<User>
    suspend fun addUser(user: User) : Boolean
    suspend fun removeUser(id: Int,username:String): Boolean
    suspend fun editUser(userId: String,edit: User)
    suspend fun getUserFriendsList(userId:Int): List<String>
    suspend fun addFriend(userId:String, id : String) : Boolean
    suspend fun removeFriend(userId:String, id :String) : Boolean
    suspend fun findUser(username:String): User
    suspend fun findUser(id : Int) : User
    suspend fun getOnlineUsersDetails(users : Array<String>) : List<User>
    suspend fun setUserPassword(user:User, password: String)
    suspend fun checkUserPassword(username: String, password: String) : Boolean
    suspend fun changePassword(username:String, oldPassword: String, newPassword: String) : Boolean
    suspend fun setUserPicture(userId: Int, image: String) : Boolean
}