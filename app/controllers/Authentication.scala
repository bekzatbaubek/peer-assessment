package controllers

import javax.inject.Inject
import play.api.mvc._

import play.filters.csrf._

import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import scala.concurrent.{Future, ExecutionContext}
import slick.jdbc.{JdbcProfile, SQLiteProfile}

import models._

class Authentication @Inject() (
    userDB: UsersInDB,
    protected val dbConfigProvider: DatabaseConfigProvider,
    cc: ControllerComponents
)(implicit
    ec: ExecutionContext
) extends AbstractController(cc)
    with HasDatabaseConfigProvider[JdbcProfile] {

  def index() = Action { implicit request =>
    Ok(views.html.index())
  }

  def FRedirToHome(username: String)(implicit request: Request[AnyContent]) =
    Future.successful(
      Redirect((routes.Application.homepage())).withSession(
        "username" -> username,
        "csrfToken" -> play.filters.csrf.CSRF.getToken
          .map(_.value)
          .getOrElse("")
      )
    )

  def validateUser() = Action.async { implicit request =>
    request.body.asFormUrlEncoded
      .map { args =>
        val username = args("username").head
        val password = args("password").head
        userDB
          .validate(username, password)
          .flatMap(validUser =>
            if (validUser) FRedirToHome(username)
            else
              Future.successful(
                Redirect(routes.Application.index())
                  .flashing("error" -> "Invalid username/password")
              )
          )
      }
      .getOrElse(
        Future.successful(
          Redirect(routes.Application.index())
            .flashing("error" -> "Invalid username/password")
        )
      )
  }

  def createUser() = Action.async { implicit request =>
    val post = request.body.asFormUrlEncoded
    post
      .map { args =>
        val username = args("username").head
        val password = args("password").head
        val fullname = args("fullname").head
        val status   = args("status").head
        //TODO: Validation
        userDB
          .insert(username, password, fullname, status)
          .flatMap(success =>
            if (success) FRedirToHome(username)
            else
              Future.successful(
                Redirect(routes.Application.index())
                  .flashing("error" -> "User creation failed")
              )
          )
      }
      .getOrElse(
        Future.successful(
          Redirect(routes.Application.index())
            .flashing("error" -> "Invalid username/password")
        )
      )
  }

  def logout = Action {
    Redirect(routes.Application.index()).withNewSession
  }

}
