package controllers

import javax.inject.Inject
import play.api.mvc._

import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import scala.concurrent.{Future, ExecutionContext}
import slick.jdbc.{JdbcProfile, SQLiteProfile}

import models._
import play.api.libs.json.Json

class Application @Inject() (
    usersDB: UsersInDB,
    assignmentsDB: AssignmentsInDB,
    coursesDB: CoursesInDB,
    questionsDB: QuestionaireInDB,
    protected val dbConfigProvider: DatabaseConfigProvider,
    cc: ControllerComponents
)(implicit
    ec: ExecutionContext
) extends AbstractController(cc)
    with HasDatabaseConfigProvider[JdbcProfile] {

  def createSchemas() = {
    usersDB.createSchema
    assignmentsDB.createSchema
    coursesDB.createSchema
    questionsDB.createSchema
  }

  def index() = Action { implicit request =>
    createSchemas()
    Ok(views.html.index())
  }

  def withSessionUser(
      f: User => Future[Result]
  )(implicit request: Request[AnyContent]) = {
    request.session
      .get("username")
      .map(username => usersDB.getUser(username).flatMap(f))
      .getOrElse(Future.successful(Redirect(routes.Application.index())))
  }

  def teacherHome(implicit request: Request[AnyContent]) = {
    withSessionUser(user =>
      for {
        courselist <- coursesDB.getList(user.id)
      } yield (Ok(views.html.teacher(courselist)))
    )
  }

  def studentHome(implicit request: Request[AnyContent]) = {
    withSessionUser(user =>
      for {
        courselist <- coursesDB.getForStudent(user.id)
      } yield (Ok(views.html.student(courselist)))
    )
  }

  def homepage() = Action.async { implicit request =>
    withSessionUser(user =>
      user.status match {
        case "teacher" => teacherHome
        case "student" => studentHome
      }
    )
  }
}
