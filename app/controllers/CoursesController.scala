package controllers

import javax.inject.Inject
import play.api.mvc._

import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import scala.concurrent.{Future, ExecutionContext}
import slick.jdbc.{JdbcProfile, SQLiteProfile}

import models._

class CoursesController @Inject() (
    usersDB: UsersInDB,
    assignmentsDB: AssignmentsInDB,
    coursesDB: CoursesInDB,
    protected val dbConfigProvider: DatabaseConfigProvider,
    cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
    with HasDatabaseConfigProvider[JdbcProfile] {

  def withSessionUser(
      f: User => Future[Result]
  )(implicit request: Request[AnyContent]) = {
    request.session
      .get("username")
      .map(username => usersDB.getUser(username).flatMap(f))
      .getOrElse(Future.successful(Redirect(routes.Application.index())))
  }

  def addCoursePage() = Action.async { implicit request =>
    withSessionUser(user =>
      Future {
        if (user.status == "teacher") Ok(views.html.newcourse())
        else
          Redirect(routes.Application.homepage())
            .flashing("error" -> "Unauthorized")
      }
    )
  }

  def addCourse() = Action.async { implicit request =>
    withSessionUser(user =>
      if (user.status == "teacher") {
        val post = request.body.asFormUrlEncoded
        post
          .map(args => {
            val title = args("title").head
            val code  = args("code").head
            coursesDB
              .add(title, code, user.id)
              .flatMap(added =>
                if (added)
                  Future.successful(
                    Redirect(routes.CoursesController.addCoursePage())
                      .flashing("success" -> "Course added successfully")
                  )
                else
                  Future.successful(
                    Redirect(routes.CoursesController.addCoursePage())
                      .flashing("error" -> "Unable to add course")
                  )
              )
          })
          .getOrElse(
            Future.successful(
              Redirect(routes.CoursesController.addCoursePage())
                .flashing("error" -> "Unable to add course")
            )
          )
      } else
        Future.successful(
          Redirect(routes.Application.homepage())
            .flashing("error" -> "Unauthorized")
        )
    )
  }

  def coursePage(code: String) = Action.async { implicit request =>
    withSessionUser(user =>
      if (user.status == "teacher") {
        coursesDB
          .getOne(code, user.id)
          .flatMap(courseOpt =>
            courseOpt
              .map(course =>
                assignmentsDB
                  .getList(code)
                  .flatMap(assignmentlist =>
                    Future.successful(
                      Ok(views.html.coursepage(course, assignmentlist))
                    )
                  )
              )
              .getOrElse(
                Future.successful(
                  Redirect(routes.Application.homepage())
                    .flashing("error" -> "Unauthorized")
                )
              )
          )
      } else {
        coursesDB
          .getOneSt(code, user.id)
          .flatMap(course =>
            assignmentsDB
              .getList(code)
              .flatMap(assignmentlist =>
                Future.successful(
                  Ok(views.html.stcoursepage(course, assignmentlist))
                )
              )
          )
      }
    )
  }

  def addAssignment(code: String) = Action.async { implicit request =>
    withSessionUser(user =>
      if (user.status == "teacher") {
        coursesDB
          .isOwner(code, user.id)
          .flatMap(isowner =>
            if (isowner) {
              val post = request.body.asFormUrlEncoded
              post
                .map(args => {
                  val title = args("title").head
                  val desc  = args("desc").head
                  assignmentsDB
                    .add(code, title, desc)
                    .flatMap(addedid =>
                      if (addedid != -1) {
                        {
                          coursesDB
                            .getStudentIdsFor(code)
                            .flatMap(studentids =>
                              Future.successful(
                                Redirect(
                                  routes.CoursesController.coursePage(code)
                                ).flashing(
                                  "success" -> "Assignment added successfully"
                                )
                              )
                            )
                        }
                      } else
                        Future.successful(
                          Redirect(routes.CoursesController.coursePage(code))
                            .flashing("error" -> "Unable to add assignment")
                        )
                    )
                })
                .getOrElse(
                  Future.successful(
                    Redirect(routes.CoursesController.coursePage(code))
                      .flashing("error" -> "Unable to add assignment")
                  )
                )
            } else
              Future.successful(
                Redirect(routes.Application.homepage())
                  .flashing("error" -> "Unauthorized")
              )
          )
      } else
        Future.successful(
          Redirect(routes.Application.homepage())
            .flashing("error" -> "Unauthorized")
        )
    )
  }

  def removeAssignment(code: String, id: Long) = Action.async {
    implicit request =>
      withSessionUser(user =>
        if (user.status == "teacher") {
          coursesDB
            .isOwner(code, user.id)
            .flatMap(isowner =>
              if (isowner) {
                assignmentsDB
                  .getByID(id)
                  .flatMap(asmOpt =>
                    asmOpt
                      .map(asm =>
                        assignmentsDB
                          .remove(id)
                          .flatMap(deleted =>
                            if (deleted)
                              Future.successful(
                                Redirect(
                                  routes.CoursesController.coursePage(code)
                                ).flashing(
                                  "success" -> "Assignment removed successfully"
                                )
                              )
                            else
                              Future.successful(
                                Redirect(routes.Application.homepage()).flashing(
                                  "error" -> "Failed to remove assignment"
                                )
                              )
                          )
                      )
                      .getOrElse(
                        Future.successful(
                          Redirect(routes.Application.homepage())
                            .flashing("error" -> "Failed to remove assignment")
                        )
                      ) //No such assignment
                  )
              } else
                Future.successful(
                  Redirect(routes.Application.homepage())
                    .flashing("error" -> "Unauthorized")
                )
            )
        } else
          Future.successful(
            Redirect(routes.Application.homepage())
              .flashing("error" -> "Unauthorized")
          )
      )
  }

  def studentsPage(code: String) = Action.async { implicit request =>
    withSessionUser(user =>
      if (user.status == "teacher") {
        coursesDB
          .isOwner(code, user.id)
          .flatMap(isowner =>
            if (isowner) {
              usersDB.getAllStudents.flatMap(allstudents =>
                coursesDB
                  .getStudentIdsFor(code)
                  .flatMap(enrolledids =>
                    Future.successful(
                      Ok(views.html.addstudents(code, allstudents, enrolledids))
                    )
                  )
              )
            } else
              Future.successful(
                Redirect(routes.Application.homepage())
                  .flashing("error" -> "Unauthorized")
              )
          )
      } else
        Future.successful(
          Redirect(routes.Application.homepage())
            .flashing("error" -> "Unauthorized")
        )
    )
  }

  def addStudent(code: String, id: Long) = Action.async { implicit request =>
    withSessionUser(user =>
      if (user.status == "teacher") {
        coursesDB
          .isOwner(code, user.id)
          .flatMap(isowner =>
            if (isowner) {
              coursesDB
                .addStudent(code, id)
                .flatMap(added =>
                  if (added) {
                    Future.successful(
                      Redirect(routes.CoursesController.studentsPage(code))
                        .flashing("success" -> "Student added successfully")
                    )
                  } else {
                    Future.successful(
                      Redirect(routes.CoursesController.studentsPage(code))
                        .flashing("error" -> "Failed to add student")
                    )
                  }
                )
            } else
              Future.successful(
                Redirect(routes.Application.homepage())
                  .flashing("error" -> "Unauthorized")
              )
          )
      } else
        Future.successful(
          Redirect(routes.Application.homepage())
            .flashing("error" -> "Unauthorized")
        )
    )
  }

  def addSubmissionPage(code: String, asmid: Long) = Action.async {
    implicit request =>
      withSessionUser(user =>
        if (user.status == "student") {
          coursesDB
            .getOneSt(code, user.id)
            .flatMap(course =>
              assignmentsDB
                .getByID(asmid)
                .flatMap(asmOpt =>
                  asmOpt
                    .map(asm =>
                      assignmentsDB
                        .getGroupFor(asmid, user.id)
                        .flatMap(grouplist =>
                          Future.successful(Ok(views.html.newsub(course, asm)))
                        )
                    )
                    .getOrElse(
                      Future.successful(
                        Redirect(routes.Application.homepage())
                          .flashing("error" -> "Failed to fetch assignment")
                      )
                    )
                )
            )
        } else
          Future.successful(
            Redirect(routes.Application.homepage())
              .flashing("error" -> "Unauthorized")
          )
      )
  }

  def addSubmission(code: String, asmid: Long) = Action.async {
    implicit request =>
      withSessionUser(user =>
        if (user.status == "student") {
          coursesDB
            .getOneSt(code, user.id)
            .flatMap(course =>
              assignmentsDB
                .getByID(asmid)
                .flatMap(asmOpt =>
                  asmOpt
                    .map(asm =>
                      request.body.asFormUrlEncoded
                        .map(args => {
                          val subbody = args("subbody").head
                          assignmentsDB
                            .getGroupFor(asmid, user.id)
                            .flatMap(groupopt => {
                              assignmentsDB
                                .addSubmission(
                                  user.id,
                                  groupopt,
                                  asmid,
                                  subbody
                                )
                                .flatMap(added =>
                                  if (added)
                                    Future.successful(
                                      Redirect(
                                        routes.CoursesController
                                          .coursePage(code)
                                      ).flashing(
                                        "success" -> "Submission added successfully"
                                      )
                                    )
                                  else
                                    Future.successful(
                                      Redirect(
                                        routes.CoursesController
                                          .coursePage(code)
                                      ).flashing(
                                        "error" -> "Unable to add submission"
                                      )
                                    )
                                )
                            })

                        })
                        .getOrElse(
                          Future.successful(
                            Redirect(routes.Application.homepage())
                              .flashing("error" -> "Unable to add submission")
                          )
                        )
                    )
                    .getOrElse(
                      Future.successful(
                        Redirect(routes.Application.homepage())
                          .flashing("error" -> "Failed to fetch assignment")
                      )
                    )
                )
            )
        } else
          Future.successful(
            Redirect(routes.Application.homepage())
              .flashing("error" -> "Unauthorized")
          )
      )
  }

  def allSubs(code: String, asmid: Long) = Action.async { implicit request =>
    withSessionUser(user =>
      if (user.status == "teacher") {
        coursesDB
          .getOne(code, user.id)
          .flatMap(courseOpt =>
            courseOpt
              .map(course =>
                assignmentsDB
                  .getByID(asmid)
                  .flatMap(asmOpt =>
                    asmOpt
                      .map(asm =>
                        assignmentsDB
                          .getSubsFor(asmid)
                          .flatMap(subs =>
                            usersDB.getAllStudents.flatMap(students =>
                              assignmentsDB
                                .getGroups(asmid)
                                .flatMap(groups =>
                                  coursesDB
                                    .getStudentIdsFor(code)
                                    .flatMap(enrolledids => {
                                      val nametuples = for {
                                        enrolled <- enrolledids
                                        student  <- students
                                        if (enrolled == student.id)
                                      } yield (student.id, student.fullname)
                                      Future.successful(
                                        Ok(
                                          views.html.allsubs(
                                            course,
                                            asm,
                                            subs,
                                            nametuples
                                          )
                                        )
                                      )
                                    })
                                )
                            )
                          )
                      )
                      .getOrElse(
                        Future.successful(
                          Redirect(routes.Application.homepage())
                            .flashing("error" -> "Failed to fetch assignment")
                        )
                      )
                  )
              )
              .getOrElse(
                Future.successful(
                  Redirect(routes.Application.homepage())
                    .flashing("error" -> "Failed to fetch course")
                )
              )
          )
      } else
        Future.successful(
          Redirect(routes.Application.homepage())
            .flashing("error" -> "Unauthorized")
        )
    )
  }

  def groupPage(code: String, asmid: Long) = Action.async { implicit request =>
    withSessionUser(user =>
      if (user.status == "teacher") {
        coursesDB
          .isOwner(code, user.id)
          .flatMap(isowner =>
            if (isowner) {
              val getglist    = assignmentsDB.getGroups(asmid)
              val getidlist   = coursesDB.getStudentIdsFor(code)
              val getstudents = usersDB.getAllStudents
              for {
                ngrouplist <- getglist
                idlist     <- getidlist
                students   <- getstudents
              } yield {
                val grouplist = ngrouplist.groupBy(_.gname)
                val tupledstudets = students
                  .filter(st => idlist contains st.id)
                  .map(st => (st.id, st.fullname))
                  .toMap
                Ok(views.html.groups(code, asmid, grouplist, tupledstudets))
              }
            } else
              Future.successful(
                Redirect(routes.Application.homepage())
                  .flashing("error" -> "Unauthorized")
              )
          )
      } else
        Future.successful(
          Redirect(routes.Application.homepage())
            .flashing("error" -> "Unauthorized")
        )
    )
  }

  def addGroup(code: String, asmid: Long) = Action.async { implicit request =>
    withSessionUser(user =>
      if (user.status == "teacher") {
        coursesDB
          .isOwner(code, user.id)
          .flatMap(isowner =>
            if (isowner) {
              request.body.asFormUrlEncoded
                .map(args => {

                  val groupname = args("gname").head
                  val students = args.get("students") match {
                    case Some(value) => value.map(vl => vl.toLong)
                    case None        => Seq.empty
                  }
                  if (students.size < 2 || groupname.size == 0)
                    Future.successful(
                      Redirect(routes.CoursesController.groupPage(code, asmid))
                        .flashing("error" -> "Invalid group info")
                    )
                  else {
                    assignmentsDB
                      .addGroup(asmid, groupname, students)
                      .flatMap(success =>
                        Future.successful(
                          Redirect(
                            routes.CoursesController.groupPage(code, asmid)
                          ).flashing("success" -> "Group added successfully!")
                        )
                      )
                  }
                })
                .getOrElse(
                  Future.successful(
                    Redirect(routes.Application.homepage())
                      .flashing("error" -> "Invalid request")
                  )
                )

            } else
              Future.successful(
                Redirect(routes.Application.homepage())
                  .flashing("error" -> "Unauthorized")
              )
          )
      } else
        Future.successful(
          Redirect(routes.Application.homepage())
            .flashing("error" -> "Unauthorized")
        )
    )
  }

  def deleteGroup(code: String, asmid: Long, gname: String) = Action.async {
    implicit request =>
      withSessionUser(user =>
        if (user.status == "teacher") {
          coursesDB
            .isOwner(code, user.id)
            .flatMap(isowner =>
              if (isowner) {
                assignmentsDB
                  .deleteGroup(asmid, gname)
                  .flatMap(deleted =>
                    Future.successful(
                      Redirect(routes.CoursesController.groupPage(code, asmid))
                        .flashing("success" -> "Group deleted successfully!")
                    )
                  )
              } else
                Future.successful(
                  Redirect(routes.Application.homepage())
                    .flashing("error" -> "Unauthorized")
                )
            )
        } else
          Future.successful(
            Redirect(routes.Application.homepage())
              .flashing("error" -> "Unauthorized")
          )
      )
  }
}
