package edu.ucsc.vesper.http.core

import edu.ucsc.refactor._
import edu.ucsc.refactor.spi._
import edu.ucsc.refactor.util.Commit
import edu.ucsc.vesper.http.config.Configuration
import edu.ucsc.vesper.http.domain.Models._

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/**
 * @author hsanchez@cs.ucsc.edu (Huascar A. Sanchez)
 */
trait Interpreter extends Configuration with VesperConversions with CommandFlattener {

  implicit def executionContext = ExecutionContext.Implicits.global

  val parser: Parser    = CommandParser()
  val storage: Storage  = VesperStorage()

  val Curator     = 0
  val Reviewer    = 1

  private def emitRoles(who:Role, question:ExactRole): Future[Option[Result]] = {
    def f(x: Int): Boolean = if(x == Reviewer) true else false
    def g(x: Int): Boolean = if(x == Curator)  true else false


    val askQuestion: Future[mutable.Buffer[String]] = Future {
      var result: mutable.Buffer[String] = mutable.Buffer.empty[String]
      question.value  match {
        case "curators"   => result ++ club.filter {case (k,v) => g(v)}.keySet.toList
        case "reviewers"  => result ++ club.filter {case (k,v) => f(v)}.keySet.toList
        case "everybody"  => result ++ passwords.keySet.toList
        case _            => result += "Unknown query!"
      }
    }

    def produceResult(answer: mutable.Buffer[String]) = Future {
      var result: Option[Result] = None
      if(who.id != Curator) {
        result = Some(Result(failure = Some(Failure("You are not authorized to see these resources."))))
      } else {
        answer.toList match {
          case first :: rest =>
            if(first == "Unknown query!") {
              result = Some(Result(failure = Some(Failure(first))))
            } else {
              result = Some(Result(info = Some(Info(answer.toList))))
            }
          case Nil => result = Some(Result(info = Some(Info(List("No roles found!")))))
        }

      }

      result

    }

    val queried = for {
      answer <- askQuestion
      result <- produceResult(answer)
    } yield {
      result
    }

    queried
  }

  private[core] def collectSource(code: Code): Future[Source] = Future {
    asSource(code)
  }

  private[core] def collectIssues(refactorer: Refactorer, source: Source): Future[mutable.Set[Issue]] = Future {
    val introspector: Introspector = refactorer.getIntrospector(source)
    asScalaSet(introspector.detectIssues())
  }


  private[core] def createChange(refactorer: Refactorer, request: ChangeRequest): Future[Change] = Future {
    refactorer.createChange(request)
  }

  private[core] def applyChange(refactorer: Refactorer, change: Change): Future[Commit] = Future {
    refactorer.apply(change)
  }

  private def evalInspect(refactorer: Refactorer, inspect: Inspect): Future[Option[Result]] = {
    try {

      def verifySource(source: Source): Future [Option[Result]] = {
        try {
          val introspector: Introspector      = refactorer.getIntrospector
          val problems: Seq[String]           = asScalaBuffer(introspector.verifySource(source))
          val result: Future [Option[Result]] = if(problems.isEmpty) Future(Some(Result(warnings = Some(List())))) else {

            val warnings = problems.map(x => Warning(x))
            Future(Some(Result(warnings = Some(warnings.toList))))
          }
          result
        } catch {
          case e: RuntimeException =>
            Future(Some(Result(warnings = Some(List(Warning(e.getMessage))))))
        }
      }

      val inspected = for {
        vesperSource  <- collectSource(inspect.source)
        result        <- verifySource(vesperSource)
      } yield {
        result
      }

      inspected

    } catch {
      case e: Exception =>
        Future(Some(Result(failure = Some(Failure(e.getMessage)))))
    }

  }

  private def createRemoveChangeRequest(what: String, selection: SourceSelection): ChangeRequest = {
    what match {
      case "class"      => ChangeRequest.deleteClass(selection)
      case "method"     => ChangeRequest.deleteMethod(selection)
      case "field"      => ChangeRequest.deleteField(selection)
      case "parameter"  => ChangeRequest.deleteParameter(selection)
      case "region"     => ChangeRequest.deleteRegion(selection)
      case _=> throw new NoSuchElementException(what + " was not found")
    }
  }

  private def removeMember(refactorer:Refactorer, what: String, where: List[Int], source: Source): Future[Option[Result]] = {
    try {
      val createRequest: Future[ChangeRequest] = Future {
        val select: SourceSelection = new SourceSelection(source, where(0), where(1))
        createRemoveChangeRequest(what, select)
      }

      def produceResult(change: Change, commit: Commit): Future[Option[Result]] = Future {
        var result: Option[Result]  = None
        commit != null && commit.isValidCommit match {
          case true =>  result = Some(Result(draft = Some(asDraft(commit))))
          case false => result = Some(Result(failure = Some(Failure(change.getErrors.mkString(" ")))))
        }

        result
      }


      val removed = for {
        request  <- createRequest
        change   <- createChange(refactorer, request)
        commit   <- applyChange(refactorer, change)
        result   <- produceResult(change, commit)
      } yield {
        result
      }

      removed

    } catch {
      case e: Exception =>
        Future(Some(Result(failure = Some(Failure(e.getMessage)))))
    }
  }

  private def evalRemove(refactorer: Refactorer, delete: Remove): Future[Option[Result]] = {
    val what:String           = delete.what
    val where:List[Int]       = delete.where
    val vesperSource: Source  = asSource(delete.source)

    removeMember(refactorer, what, where, vesperSource)
  }

  private def createRenameChangeRequest(what: String, name: String, selection: SourceSelection): ChangeRequest = {
    what match {
      case "class"      => ChangeRequest.renameClassOrInterface(selection, name)
      case "method"     => ChangeRequest.renameMethod(selection, name)
      case "field"      => ChangeRequest.renameField(selection, name)
      case "parameter"  => ChangeRequest.renameParameter(selection, name)
      case "member"     => ChangeRequest.renameSelectedMember(selection, name)
      case _=> throw new NoSuchElementException(what + " was not found")
    }
  }

  private def renameMember(refactorer: Refactorer, what: String, name: String, where: List[Int], source: Source): Future[Option[Result]] = {
    try {

      val createRequest: Future[ChangeRequest] = Future {
        val select: SourceSelection = new SourceSelection(source, where(0), where(1))
        createRenameChangeRequest(what, name, select)
      }

      def produceResult(change: Change, commit: Commit): Future[Option[Result]] = Future {
        var result: Option[Result]  = None
        commit != null && commit.isValidCommit match {
          case true =>  result = Some(Result(draft = Some(asDraft(commit))))
          case false => result = Some(Result(failure = Some(Failure(change.getErrors.mkString(" ")))))
        }

        result
      }


      val renamed = for {
        request  <- createRequest
        change   <- createChange(refactorer, request)
        commit   <- applyChange(refactorer, change)
        result   <- produceResult(change, commit)
      } yield {
        result
      }

      renamed

    } catch {
      case e: Exception => Future(Some(Result(failure = Some(Failure(e.getMessage)))))
    }
  }

  private def evalRename(refactorer: Refactorer, rename: Rename): Future[Option[Result]] = {
    val what:String           = rename.what
    val where:List[Int]       = rename.where
    val name: String          = rename.to
    val vesperSource: Source  = asSource(rename.source)

    renameMember(refactorer, what, name, where, vesperSource)
  }

  private def evalOptimize(refactorer: Refactorer, optimize: Optimize): Future[Option[Result]] = {

    def createRequest(source: Source): Future[ChangeRequest] = Future {
      ChangeRequest.optimizeImports(source)
    }

    def produceResult(change: Change, commit: Commit): Future[Option[Result]] = Future {
      var result: Option[Result]  = None
      commit != null && commit.isValidCommit match {
        case true =>  result = Some(Result(draft = Some(asDraft(commit))))
        case false => result = Some(Result(failure = Some(Failure(change.getErrors.mkString(" ")))))
      }

      result
    }


    val optimized = for {
      source   <- collectSource(optimize.source)
      request  <- createRequest(source)
      change   <- createChange(refactorer, request)
      commit   <- applyChange(refactorer, change)
      result   <- produceResult(change, commit)
    } yield {
      result
    }

    optimized
  }

  private def evalFormat(refactorer: Refactorer, format: Format): Future[Option[Result]] = {

    def createRequest(source: Source): Future[ChangeRequest] = Future {
      ChangeRequest.reformatSource(source)
    }

    def produceResult(change: Change, commit: Commit): Future[Option[Result]] = Future {
      var result: Option[Result]  = None
      commit != null && commit.isValidCommit match {
        case true =>  result = Some(Result(draft = Some(asFormattedDraft(commit))))
        case false => result = Some(Result(failure = Some(Failure(change.getErrors.mkString(" ")))))
      }

      result
    }

    val reformatted = for {
      source   <- collectSource(format.source)
      request  <- createRequest(source)
      change   <- createChange(refactorer, request)
      commit   <- applyChange(refactorer, change)
      result   <- produceResult(change, commit)
    } yield {
      result
    }

    reformatted
  }

  private def evalCleanup(refactorer: Refactorer, cleanup: Cleanup): Future[Option[Result]] = {

    def detectIssues(source: Source): mutable.Set[Issue] = {
      val introspector: Introspector = refactorer.getIntrospector(source)
      val result:mutable.Set[Issue] = asScalaSet(introspector.detectIssues())
      result
    }

    def createCommit(issue: Issue): Commit = {
      val change: Change = refactorer.createChange(ChangeRequest.forIssue(issue))
      val commit: Commit = refactorer.apply(change)

      commit
    }

    def batchChanges(source: Source): Future [mutable.Stack[Commit]] = Future {
      val Q: mutable.Queue[Source] = new mutable.Queue[Source]
      val S: mutable.Stack[Commit] = new mutable.Stack[Commit]

      Q += source

      while(Q.nonEmpty){
        val src: Source = Q.dequeue()

        val issues: mutable.Set[Issue] = detectIssues(src).filter(
          i => Names.hasAvailableResponse(i.getName)
            && (Names.from(i.getName).isSame(Refactoring.DEDUPLICATE)
            || Names.from(i.getName).isSame(Refactoring.DELETE_UNUSED_IMPORTS)))

        for(i <- issues){
          val c: Commit = createCommit(i)
          if(c != null && c.isValidCommit && Q.isEmpty) {
            S.push(c)
            Q.enqueue(c.getSourceAfterChange)
          }
        }
      }

      S
    }

    def produceResult(vesperSource: Source, stack:mutable.Stack[Commit]): Future[Option[Result]] = Future {
      Some(
        Result(
          draft = Some(
            asFormattedDraft(
              stack.pop(),
              cause       = "Full cleanup",
              description = "Reformatted code and also removed code redundancies"
            )
          )
        )
      )
    }

    val cleanedUp = for {
      source  <- collectSource(cleanup.source)
      commits <- batchChanges(source)
      result  <- produceResult(source, commits)
    } yield {
      result
    }

    cleanedUp
  }

  private def evalDeduplicate(refactorer: Refactorer, deduplicate: Deduplicate): Future[Option[Result]] = {

    def filterNonDeduplicateIssues(issues:mutable.Set[Issue]): Future[mutable.Set[Issue]] = Future {
       issues.filter(i => Names.hasAvailableResponse(i.getName) && Names.from(i.getName).isSame(Refactoring.DEDUPLICATE))
    }


    def applyChanges(filteredIssues:mutable.Set[Issue]): Future[mutable.Buffer[Commit]] = Future {
      val commits: mutable.Buffer[Commit] = mutable.Buffer.empty[Commit]
      for(i <- filteredIssues){
        val change: Change = refactorer.createChange(ChangeRequest.forIssue(i))
        val commit: Commit = refactorer.apply(change)
        commits += commit
      }

      commits
    }


    def produceResult(commits: mutable.Buffer[Commit]): Future[Option[Result]] = Future {
      var result: Option[Result]  = None

      for(c <- commits){
        c != null && c.isValidCommit match {
          case true   =>  result = Some(Result(draft = Some(asDraft(c))))
          case false  =>  result = Some(Result(failure = Some(Failure("invalid commit"))))
        }
      }

      result
    }

    val deduplicated = for {
      source   <- collectSource(deduplicate.source)
      issues   <- collectIssues(refactorer, source)
      fIssues  <- filterNonDeduplicateIssues(issues)
      changes  <- applyChanges(fIssues)
      result   <- produceResult(changes)
    } yield {
      result
    }

    deduplicated

  }


  private def evalFind(who: Role, find: Find) : Future[Option[Result]] = {

    val answer = flatten(find)

    answer match {
      case role: ExactRole              => emitRoles(who, role)
      case all: AllInSet                => storage.findAll()
      case any: AnyInSet                => storage.find(any)
      case exact: ExactlyOne            => storage.find(exact)
      case exactlyAll: ExactlyAllInSet  => storage.find(exactlyAll)
      case _                            => Future(Some(Result(failure = Some(Failure("Unknown command")))))
    }
  }


  private def evalPersist(who:Auth, persist: Persist): Future[Option[Result]] = {
    storage.persist(persist.source)
  }

  private[core] def unknownCommand(): Future[Option[Result]] = Future{Some(Result(failure = Some(Failure("Unknown command!"))))}

  def eval(membership: Membership, command: String) : Future[Option[Result]] = eval(membership, parser.parse(command))

  def eval(membership: Membership, command: Command): Future[Option[Result]] = {
    val who: Auth               = membership.auth
    val what: Role              = membership.role

    val environment: Refactorer = Vesper.createRefactorer()

    val answer = flatten(command)

    answer match {
      case inspect:Inspect          => evalInspect(environment, inspect)
      case remove:Remove            => evalRemove(environment, remove)
      case rename:Rename            => evalRename(environment, rename)
      case optimize:Optimize        => evalOptimize(environment, optimize)
      case format:Format            => evalFormat(environment, format)
      case deduplicate:Deduplicate  => evalDeduplicate(environment, deduplicate)
      case cleanup:Cleanup          => evalCleanup(environment, cleanup)
      case find: Find               => evalFind(what, find)
      case persist: Persist         => evalPersist(who, persist)
      case _                        => unknownCommand()
    }
  }
}


case class VesperInterpreter() extends Interpreter

