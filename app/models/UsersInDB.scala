package models

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import org.mindrot.jbcrypt.BCrypt
import org.sqlite.SQLiteException

case class User(id: Long, username: String, password: String, fullname: String, status: String)

class UsersInDB @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: DatabaseExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
    import profile.api._

    private val Users = TableQuery[UsersTable]

    def createSchema = db.run(Users.schema.createIfNotExists)

    def getUser(username: String): Future[User] = db.run(Users.filter(userrow => userrow.username === username).result.head)

    def getStatus(username: String): Future[String] = db.run(Users.filter(userrow => userrow.username === username).result.head).map( userrow => userrow.status)

    def getStudentById(id: Long): Future[String] = db.run(Users.filter(userrow => userrow.id === id).result.head).map( userrow => userrow.fullname)

    def getAllStudents: Future[Seq[User]] = db.run(Users.filter(userrow => userrow.status === "student").sortBy(_.fullname.desc).result)

    def validate(username: String, password: String): Future[Boolean] = {
        val matches = db.run(Users.filter(userrow => userrow.username === username).result)
        matches.map(userRows => userRows.filter(userRow => BCrypt.checkpw(password, userRow.password)).nonEmpty)
    }

    def insert(username: String, password: String, fullname: String, status: String): Future[Boolean] = {
        val newuser = new User(-1, username, BCrypt.hashpw(password, BCrypt.gensalt()), fullname, status)
        db.run(Users += newuser).map(addedCount => addedCount > 0) recoverWith{ case e: SQLiteException => Future.successful(false)}
    }

    private class UsersTable(tag: Tag) extends Table[User](tag, "USERS") {
        def id = column[Long]("ID", O.AutoInc, O.PrimaryKey)
        def username = column[String]("USERNAME", O.Unique)
        def password = column[String]("PASSWORD")
        def fullname = column[String]("FULLNAME")
        def status = column[String]("STATUS")

        def * = (id, username, password, fullname, status) <> (User.tupled, User.unapply)
    }
}
