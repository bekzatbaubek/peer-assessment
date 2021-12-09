package controllers

import javax.inject.Inject
import play.api.mvc._

import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import scala.concurrent.{Future, ExecutionContext}
import slick.jdbc.{JdbcProfile, SQLiteProfile}

import models._

class QuestionaireController @Inject() (
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

  def withSessionUser(
      f: User => Future[Result]
  )(implicit request: Request[AnyContent]) = {
    request.session
      .get("username")
      .map(username => usersDB.getUser(username).flatMap(f))
      .getOrElse(Future.successful(Redirect(routes.Application.index())))
  }

  def FRedirHomeFlash(msg: (String, String)) = {
    Future.successful(Redirect(routes.Application.homepage()).flashing(msg))
  }

  def qResponcePage(code: String, asmid: Long) = Action.async {
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
                        .getSubsFor(asmid)
                        .flatMap(subs =>
                          usersDB.getAllStudents.flatMap(students =>
                            assignmentsDB
                              .getGroups(asmid)
                              .flatMap(groups => {
                                val temp = for {
                                  sub     <- subs
                                  student <- students
                                  if sub.submiteeid == student.id
                                } yield (student.id, student.fullname, sub.body)
                                val gmates = for {
                                  i <- temp
                                  g <- groups
                                  if (i._1 == g.sid)
                                } yield (
                                  i._1,
                                  g.gname,
                                  students
                                    .filter(
                                      groups
                                        .filter(_.gname == g.gname)
                                        .map(g => g.sid) contains _.id
                                    )
                                    .map(_.fullname)
                                )
                                for {
                                  questions <- questionsDB.getQuestions(asmid)
                                  allmcopts <- questionsDB.getMCOptions()
                                  allrangenums <- questionsDB.getRangeOptions()
                                } yield {
                                  val mcqs = questions.filter(_.qtype == "mcq")
                                  val rangeqs =
                                    questions.filter(_.qtype == "rangeq")
                                  val mcqopts = allmcopts
                                    .filter(mcqs.map(_.id) contains _.qid)
                                    .groupBy(_.qid)
                                    .toMap
                                  val rangeqopts = allrangenums
                                    .filter(rangeqs.map(_.id) contains _.qid)
                                    .groupBy(_.qid)
                                    .toMap
                                  Ok(
                                    views.html.qresponce(
                                      code,
                                      asmid,
                                      questions,
                                      mcqopts,
                                      rangeqopts,
                                      temp,
                                      gmates
                                    )
                                  )
                                }
                              })
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

  def questionairePage(code: String, asmid: Long) = Action.async {
    implicit request =>
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
                          for {
                            questions <- questionsDB.getQuestions(asmid)
                            mcqs      <- questionsDB.getOptionsFor()
                            rangenums <- questionsDB.getRangeOptions()
                          } yield (Ok(
                            views.html.questionaire(
                              course,
                              asm,
                              questions,
                              mcqs,
                              rangenums
                            )
                          ))
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

  def addQuestion(qtype: String, code: String, asmid: Long) = Action.async {
    implicit request =>
      withSessionUser(user =>
        if (user.status == "teacher") {
          coursesDB
            .isOwner(code, user.id)
            .flatMap(isowner =>
              if (isowner) {
                request.body.asFormUrlEncoded
                  .map(args => {
                    qtype match {
                      case "shortq" => addShortQ(code, asmid)
                      case "mcq"    => addMCQ(code, asmid)
                      case "rangeq" => addRangeQ(code, asmid)
                    }
                  })
                  .getOrElse(
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
        } else
          Future.successful(
            Redirect(routes.Application.homepage())
              .flashing("error" -> "Unauthorized")
          )
      )
  }

  def addShortQ(code: String, asmid: Long)(implicit
      request: Request[AnyContent]
  ) = {
    request.body.asFormUrlEncoded
      .map(args => {
        val qbody = args("question").head
        questionsDB
          .addQuestion(asmid, "shortq", qbody)
          .flatMap(isaddedq =>
            if (isaddedq)
              Future.successful(
                Redirect(
                  routes.QuestionaireController
                    .questionairePage(code: String, asmid: Long)
                ).flashing("success" -> "Question added successfully")
              )
            else
              Future.successful(
                Redirect(
                  routes.QuestionaireController
                    .questionairePage(code: String, asmid: Long)
                ).flashing("error" -> "Failed to add question")
              )
          )
      })
      .getOrElse(
        Future.successful(
          Redirect(routes.Application.homepage())
            .flashing("error" -> "Unauthorized")
        )
      )
  }

  def addMCQ(code: String, asmid: Long)(implicit
      request: Request[AnyContent]
  ) = {
    request.body.asFormUrlEncoded
      .map(args => {
        val qbody   = args("question").head
        val optnum  = args("optsnumber").head.toInt
        val answers = { for (i <- 1 to optnum) yield args("answer" + i).head }
        questionsDB
          .addQuestion(asmid, "mcq", qbody)
          .flatMap(isaddedq =>
            if (isaddedq) {
              questionsDB
                .getQid(asmid, qbody)
                .flatMap(qid =>
                  questionsDB
                    .addMCOption(qid, answers)
                    .flatMap(addedoptions =>
                      Future.successful(
                        Redirect(
                          routes.QuestionaireController
                            .questionairePage(code: String, asmid: Long)
                        ).flashing("success" -> "Question added successfully")
                      )
                    )
                )
            } else
              Future.successful(
                Redirect(
                  routes.QuestionaireController
                    .questionairePage(code: String, asmid: Long)
                ).flashing("error" -> "Failed to add question")
              )
          )
      })
      .getOrElse(
        Future.successful(
          Redirect(routes.Application.homepage())
            .flashing("error" -> "Unauthorized")
        )
      )
  }

  def addRangeQ(code: String, asmid: Long)(implicit
      request: Request[AnyContent]
  ) = {
    request.body.asFormUrlEncoded
      .map(args => {
        val qbody    = args("question").head
        val rangenum = args("rangenum").head.toInt
        questionsDB
          .addQuestion(asmid, "rangeq", qbody)
          .flatMap(isaddedq =>
            if (isaddedq) {
              questionsDB
                .getQid(asmid, qbody)
                .flatMap(qid =>
                  questionsDB
                    .addRangeQOpt(qid, rangenum)
                    .flatMap(added =>
                      Future.successful(
                        Redirect(
                          routes.QuestionaireController
                            .questionairePage(code: String, asmid: Long)
                        ).flashing("success" -> "Question added successfully")
                      )
                    )
                )
            } else
              Future.successful(
                Redirect(
                  routes.QuestionaireController
                    .questionairePage(code: String, asmid: Long)
                ).flashing("error" -> "Failed to add question")
              )
          )
      })
      .getOrElse(
        Future.successful(
          Redirect(routes.Application.homepage())
            .flashing("error" -> "Unauthorized")
        )
      )
  }

  def addResponce(code: String, asmid: Long, subid: Long) = Action.async {
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
                      questionsDB
                        .getQuestions(asmid)
                        .flatMap(questions =>
                          request.body.asFormUrlEncoded
                            .map(args => {
                              val questionnumber = args("questionnumber").head
                              val responces = for {
                                question <- questions
                              } yield {
                                questionsDB.addResponce(
                                  asmid,
                                  subid,
                                  question.id,
                                  user.id,
                                  question.qtype,
                                  args("question" + question.id).head
                                )
                              }
                              Future.successful(
                                Redirect(
                                  routes.QuestionaireController
                                    .qResponcePage(code, asmid)
                                )
                              )
                            })
                            .getOrElse(
                              Future.successful(
                                Redirect(routes.Application.homepage())
                                  .flashing("error" -> "Incorrect responce")
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
        } else
          Future.successful(
            Redirect(routes.Application.homepage())
              .flashing("error" -> "Unauthorized")
          )
      )
  }

  def allResponces(code: String, asmid: Long) = Action.async {
    implicit request =>
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
                        .map(asm => {
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
                                        for {
                                          responces <- questionsDB
                                            .getResponces(asmid)
                                          questions <- questionsDB
                                            .getQuestions(asmid)
                                        } yield {
                                          Ok(
                                            views.html.allresponces(
                                              code,
                                              asmid,
                                              responces,
                                              questions,
                                              nametuples,
                                              subs
                                            )
                                          )
                                        }
                                      })
                                  )
                              )
                            )
                        })
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
}
