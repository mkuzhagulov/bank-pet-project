package bank.app

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import bank.actors.Bank
import bank.routes.BankRouter
import bank.actors.PersistentBankAccount.Command
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.Http

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object BankApp extends App {
  trait RootCommand
  case class RetrieveBankActor(replyTo: ActorRef[ActorRef[Command]]) extends RootCommand

  def startHttpServer(bank: ActorRef[Command])(implicit system: ActorSystem[_]): Unit = {
    implicit val ec: ExecutionContext = system.executionContext
    val router = new BankRouter(bank)
    val routes = router.routes

    // start the server
    val httpBindingFuture = Http().newServerAt("localhost", 8080).bind(routes)

    // manage the server binding
    httpBindingFuture.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"Server online at http://${address.getHostString}:${address.getPort}")
      case Failure(ex) =>
        system.log.error(s"Failed to bind HTTP server, because: $ex")
        system.terminate()
    }
  }

  val rootBehavior: Behavior[RootCommand] = Behaviors.setup { context =>
    val bankActor = context.spawn(Bank(), "bank")
    Behaviors.receiveMessage {
      case RetrieveBankActor(replyTo) =>
        replyTo ! bankActor
        Behaviors.same
    }
  }

  implicit val system: ActorSystem[RootCommand] = ActorSystem(rootBehavior, "BankSystem")
  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = Timeout(5.seconds)

  val bankAccountFuture: Future[ActorRef[Command]] = system.ask(replyTo => RetrieveBankActor(replyTo))

  bankAccountFuture.foreach(startHttpServer)
}
