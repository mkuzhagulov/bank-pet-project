package bank.routes

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import bank.actors.PersistentBankAccount.Command._
import bank.actors.PersistentBankAccount.Response.{BankAccountBalanceUpdatedResponse, BankAccountCreatedResponse, GetBankAccountResponse}
import bank.actors.PersistentBankAccount.{Command, Response}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class BankAccountCreationRequest(user: String, currency: String, balance: Double) {
  def toCommand(replyTo: ActorRef[Response]): Command = CreateBankAccount(user, currency, balance, replyTo)
}
case class BankAccountUpdateRequest(currency: String, amount: Double) {
  def toCommand(id: String, replyTo: ActorRef[Response]): Command = UpdateBalance(id, currency, amount, replyTo)
}
case class FailureResponse(reason: String)

class BankRouter(bank: ActorRef[Command])(implicit system: ActorSystem[_]) {
  implicit val timeout: Timeout = Timeout(5.seconds)

  def createBankAccount(request: BankAccountCreationRequest): Future[Response] = {
    bank.ask(replyTo => request.toCommand(replyTo))
  }

  def getBankAccount(id: String): Future[Response] = {
    bank.ask(replyTo => GetBankAccount(id, replyTo))
  }

  def updateBankAccount(id: String, request: BankAccountUpdateRequest): Future[Response] =
    bank.ask(replyTo => request.toCommand(id, replyTo))

  val routes =
    pathPrefix("bank") {
      pathEndOrSingleSlash {
        post {
          entity(as[BankAccountCreationRequest]) { request =>

            onSuccess(createBankAccount(request)) {
              case BankAccountCreatedResponse(id) =>
                respondWithHeader(Location(s"/bank/$id")) {
                  complete(StatusCodes.Created)
                }
            }
          }
        }
      } ~
        path(Segment) { id =>
          get {
            onSuccess(getBankAccount(id)) {
              case GetBankAccountResponse(Some(account)) =>
                complete(account)
              case GetBankAccountResponse(None) =>
                complete(StatusCodes.NotFound, FailureResponse(s"Bank account $id cannot be found."))
            }
          } ~
            put {
              entity(as[BankAccountUpdateRequest]) { request =>
                onSuccess(updateBankAccount(id, request)) {
                  case BankAccountBalanceUpdatedResponse(Success(account)) =>
                    complete(account)
                  case BankAccountBalanceUpdatedResponse(Failure(ex)) =>
                    complete(StatusCodes.BadRequest, FailureResponse(s"${ex.getMessage}"))
                }
              }
            }
        }
    }
}
