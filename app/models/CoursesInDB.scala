package models

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import org.sqlite.SQLiteException
import org.checkerframework.checker.units.qual.s

case class Course(name: String, code: String, teacherid: Long)
case class Student(course: String, sid: Long)

class CoursesInDB @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: DatabaseExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
    import profile.api._

    private val Courses = TableQuery[CoursesTable]
    private val Students = TableQuery[StudentsTable]

    def createSchema = db.run((Courses.schema ++ Students.schema).createIfNotExists)

    def getList(id: Long): Future[Seq[Course]] = {
        db.run(Courses.filter(crow => crow.teacherid === id).result)
    }

    def isOwner(code: String, id: Long): Future[Boolean] = {
        db.run(Courses.filter(crow => crow.code === code && crow.teacherid === id).result.headOption).flatMap( result =>
            result.map( existres =>
                Future.successful(true)
            ).getOrElse(Future.successful(false))
        )
    }

    def getOne(code: String, id: Long): Future[Option[Course]] = {
        db.run(Courses.filter(crow => crow.code === code && crow.teacherid === id).result.headOption) recoverWith{ case e: SQLiteException => Future.successful(None)}
    }

    def getOneSt(code: String, id: Long): Future[Course] = {
        getForStudent(id).map( courses =>
            courses.filter(_.code == code).head
        )
    }

    def add(name: String, code: String, tid: Long): Future[Boolean] = {
        val newCourse = new Course(name, code, tid)
        db.run(Courses += newCourse).map( addedrows =>
            addedrows > 0
        ) recoverWith{ case e: SQLiteException => Future.successful(false)}

    }

    def getStudentIdsFor(code: String): Future[Seq[Long]] = {
        val q = Students.filter(_.course === code).map(Student => Student.sid)
        db.run(q.result)
    }

    def getForStudent(id: Long): Future[Seq[Course]] = {
        db.run(Students.filter(_.sid === id).map(s => s.course).result).flatMap( codelist =>
            db.run(Courses.result).map( allcourses =>
                allcourses.filter(codelist contains _.code)
            )
        )
    }

    def addStudent(code: String, id: Long) = {
        val newStudent = new Student(code, id)
        db.run(Students += newStudent).map( addedrows =>
            addedrows > 0
        ) recoverWith{ case e: SQLiteException => Future.successful(false)}
    }

    private class CoursesTable(tag: Tag) extends Table[Course](tag, "COURSES"){
        def name = column[String]("NAME")
        def code = column[String]("CODE")
        def teacherid = column[Long]("TEACHERID")
        def pk = primaryKey("coursepk", (code, teacherid))

        def * = (name, code, teacherid) <> (Course.tupled, Course.unapply)
    }

    private class StudentsTable(tag: Tag) extends Table[Student](tag, "STUDENTS"){
        def course = column[String]("COURSE")
        def sid = column[Long]("SID")
        def pk = primaryKey("student_pk", (course, sid))

        def * = (course, sid) <> (Student.tupled, Student.unapply)
    }
}