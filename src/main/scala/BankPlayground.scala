import actors.Bank
import actors.PersistentBankAccount.Command._
import actors.PersistentBankAccount.Response._
import actors.PersistentBankAccount.Response
import akka.NotUsed
import akka.actor.typed.{ActorSystem, Behavior, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object BankPlayground extends App {
  val rootBehavior: Behavior[NotUsed] = Behaviors.setup { context =>
    val bank = context.spawn(Bank(), "bank")
    val logger = context.log

    val responseHandler = context.spawn(Behaviors.receiveMessage[Response]{
      case BankAccountCreatedResponse(id) =>
        logger.info(s"successfully created bank account $id")
        Behaviors.same
      case GetBankAccountResponse(maybeBankAccount) =>
        logger.info(s"Account details: $maybeBankAccount")
        Behaviors.same
    }, "replyHandler")

    implicit val timeout: Timeout = Timeout(2.seconds)
    implicit val scheduler: Scheduler = context.system.scheduler
    implicit val ec: ExecutionContext = context.executionContext

    // test 1
    // bank ! CreateBankAccount("marat kuzhagulov", "USD", 100000, responseHandler)

    // test 2
    // bank ! GetBankAccount("74ec57ac-4bc8-48a7-848c-e77570eb01cc", responseHandler)

    Behaviors.empty
  }

  val system = ActorSystem(rootBehavior, "BankActorSystem")
}
