package models

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import org.sqlite.SQLiteException

case class Assignment(id: Long, coursecode: String, title: String, description: String)
case class Submission(submiteeid: Long, subgroup: Option[String], assignmentid: Long, body: String)
case class Group(gname: String, asmid: Long, sid: Long)

class AssignmentsInDB @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: DatabaseExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
    import profile.api._

    private val Assignments = TableQuery[AssignmentsTable]
    private val Submissions = TableQuery[SubmissionsTable]
    private val Groups      = TableQuery[GroupsTable]

    def createSchema = db.run((Assignments.schema ++ Submissions.schema ++ Groups.schema).createIfNotExists)   

    def getList(code: String): Future[Seq[Assignment]] = {
        db.run(Assignments.filter(crow => crow.coursecode === code).result)
    }

    def getByID(id: Long): Future[Option[Assignment]] = db.run(Assignments.filter(assignrow => assignrow.id === id).result.headOption)

    def remove(id: Long) = {
        db.run(Assignments.filter(arow => arow.id === id).delete).map( rowsDeleted => 
        rowsDeleted > 0
        )
    }

    def add(code: String, title: String, desc: String): Future[Long] = {
        val newasm = new Assignment(-1, code, title, desc)
        val insertedid = Assignments returning Assignments.map(_.id) += newasm
        db.run(insertedid) recoverWith{case e: SQLiteException => Future.successful(-1)}
    }

    def addSubmission(sid: Long, group: Option[Group], asmid: Long, body: String): Future[Boolean] = {
        val groupname = {
            group match {
                case Some(value) => Some(value.gname)
                case None => None
            }
        }

        db.run(Submissions += new Submission(sid, groupname, asmid, body)).map( addedrows =>
            addedrows > 0
        ) recoverWith{ case e: SQLiteException => Future.successful(false)}
    }

    def getSubsFor(asmid: Long): Future[Seq[Submission]] = {
        db.run(Submissions.filter(_.assignmentid === asmid).result)
    }

    def addGroup(asmid: Long, gname: String, stid: Seq[Long]): Future[Long] = {
        var cnt = 0
        for(sid <- stid) db.run(Groups += new Group(gname, asmid, sid)).map(addedrows => cnt += addedrows)
        Future.successful(cnt.toLong)
    }

    def getGroups(asmid: Long): Future[Seq[Group]] = {
        db.run(Groups.filter(_.asmid === asmid).result)
    }

    def getGroupFor(asmid: Long, sid: Long): Future[Option[Group]] = {
        db.run(Groups.filter(gr => gr.asmid === asmid && gr.sid === sid).result.headOption)
    }

    def deleteGroup(asmid: Long, gname: String): Future[Boolean] = {
        db.run(Groups.filter(g => g.asmid === asmid && g.gname === gname).delete).map( deletedrows =>
            deletedrows > 0
        )
    }

    private class AssignmentsTable(tag: Tag) extends Table[Assignment](tag, "ASSIGNMENTS"){
        def id           = column[Long]("ID", O.PrimaryKey, O.AutoInc)
        def coursecode   = column[String]("COURSECODE")
        def title        = column[String]("TITLE")
        def description  = column[String]("DESCRIPTION")

        def * = (id, coursecode, title, description) <> (Assignment.tupled, Assignment.unapply)
    }

    private class SubmissionsTable(tag: Tag) extends Table[Submission](tag, "SUBMISSIONS"){
        def submiteeid   = column[Long]("SUBMITEEID")
        def subgroup     = column[Option[String]]("SUBGROUP", O.Default(None))
        def assignmentid = column[Long]("ASSIGNMENTID")
        def body         = column[String]("BODY")
        def pk = primaryKey("subspk", (subgroup, assignmentid))
        def fk = foreignKey("subsfk", assignmentid, Assignments)(_.id, onDelete=ForeignKeyAction.Cascade)

        def * = (submiteeid, subgroup, assignmentid, body) <> (Submission.tupled, Submission.unapply)
    }

    private class GroupsTable(tag: Tag) extends Table[Group](tag, "GROUPS"){
        def gname = column[String]("GNAME")
        def asmid = column[Long]("ASSIGNMENTID")
        def sid   = column[Long]("SID")
        def pk    = primaryKey("group_pk", (asmid, sid))
        def fk    = foreignKey("group_fk", asmid, Assignments)(_.id, onDelete=ForeignKeyAction.Cascade)

        def * = (gname, asmid, sid) <> (Group.tupled, Group.unapply)
    }
}