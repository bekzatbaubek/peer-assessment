package models

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import org.sqlite.SQLiteException
import org.checkerframework.checker.units.qual.s

case class Question(id: Long, asmid: Long, qtype: String, question: String)
case class MCSingle(qid: Long, option: String)
case class Range(qid: Long, mxnum: Long)
case class Responce(asmid: Long, subid: Long, qid: Long, sid: Long, qtype: String, responce: String)


class QuestionaireInDB @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: DatabaseExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
    import profile.api._

    private val Questions = TableQuery[QuestionsTable]
    private val MCSingles = TableQuery[MCTable]
    private val RangeQs = TableQuery[RangeQTable]
    private val Responces = TableQuery[ResponceTable]

    def createSchema = db.run((Questions.schema ++ MCSingles.schema ++ RangeQs.schema ++ Responces.schema).createIfNotExists)

    def addQuestion(asmid: Long, qtype: String, question: String) = {
        val newq = new Question(-1, asmid, qtype, question)
        db.run(Questions += newq).map( addedrows => addedrows > 0 ) recoverWith{ case e: SQLiteException => Future.successful(false)}
    }

    def getQuestions(asmid: Long): Future[Seq[Question]] = {
        db.run(Questions.filter(_.asmid === asmid).result)
    }

    def getMCOptions(): Future[Seq[MCSingle]] = {
        db.run(MCSingles.result)
    }

    def getRangeOptions(): Future[Seq[Range]] = {
        db.run(RangeQs.result)
    }

    def getQid(asmid: Long, question: String): Future[Long] = {
        db.run(Questions.filter(qrow => qrow.asmid === asmid && qrow.question === question).result.head).map( q =>
            q.id
        )
    }

    def addMCOption(qid: Long, ans: Seq[String]) = {
        var cnt = 0
        for(singleans <- ans) db.run(MCSingles += new MCSingle(qid, singleans)).map(addedrows => cnt += addedrows) recoverWith{ case e: SQLiteException => Future.successful(0)}
        Future.successful(cnt)
    }

    def getOptionsFor(): Future[Seq[MCSingle]] = {
        db.run(MCSingles.result)
    }

    def addRangeQOpt(qid: Long, ans: Long) = {
        var cnt = 0
        db.run(RangeQs += new Range(qid, ans)).map(addedrows => cnt += addedrows) recoverWith{ case e: SQLiteException => Future.successful(0)}
        Future.successful(cnt)
    }

    def addResponce(asmid: Long, subid: Long, qid: Long, sid: Long, qtype: String, responce: String): Future[Boolean] = {
        db.run(Responces += new Responce(asmid, subid, qid, sid, qtype, responce)).map(addedrows => addedrows >0) recoverWith{ case e: SQLiteException => Future.successful(false) }
    }

    def getResponces(asmid: Long): Future[Seq[Responce]] = {
        db.run(Responces.filter(_.asmid === asmid).result)
    }

    private class QuestionsTable(tag: Tag) extends Table[Question](tag, "QUESTIONS"){
        def id = column[Long]("ID", O.AutoInc, O.PrimaryKey)
        def asmid = column[Long]("ASSIGNMENTID")
        def qtype = column[String]("TYPE")
        def question = column[String]("QUESTION")
        
        def * = (id, asmid, qtype, question) <> (Question.tupled, Question.unapply)
    }

    private class MCTable(tag: Tag) extends Table[MCSingle](tag, "MCQOPTIONS"){
        def qid = column[Long]("QID")
        def option = column[String]("OPTION")
        def pk = primaryKey("mc_pk", (qid, option))
        def fk = foreignKey("mc_fk", qid, Questions)(_.id, onDelete=ForeignKeyAction.Cascade)

        def * = (qid, option) <> (MCSingle.tupled, MCSingle.unapply)
    }
    
    private class RangeQTable(tag: Tag) extends Table[Range](tag, "RANGEQS"){
        def qid = column[Long]("QID")
        def mxnum = column[Long]("MXNUM")
        def pk = primaryKey("range_pk", (qid, mxnum))
        def fk = foreignKey("range_fk", qid, Questions)(_.id, onDelete=ForeignKeyAction.Cascade)

        def * = (qid, mxnum) <> (Range.tupled, Range.unapply)
    }


    //asmid: Long, subid: Long, qid: Long, sid: Long, qtype: String, responce: String

    private class ResponceTable(tag: Tag) extends Table[Responce](tag, "RESPONCES"){
        def asmid = column[Long]("ASMID")
        def subid = column[Long]("SUBID")
        def qid = column[Long]("QID")
        def sid = column[Long]("SID")
        def qtype = column[String]("QTYPE")
        def responce = column[String]("RESPONCE")

        def pk = primaryKey("res_pk", (subid, qid, sid))

        def * = (asmid, subid, qid, sid, qtype, responce) <> (Responce.tupled, Responce.unapply)
    }
}