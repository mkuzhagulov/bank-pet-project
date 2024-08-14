package bank.actors

import PersistentBankAccount.Response.{BankAccountBalanceUpdatedResponse, GetBankAccountResponse}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import java.util.UUID
import scala.util.Failure

object Bank {
  // commands from Persistent bank account
  import PersistentBankAccount.Command._
  import PersistentBankAccount.Command

  // events
  sealed trait Event
  case class BankAccountCreated(id: String) extends Event

  // state
  case class State(accounts: Map[String, ActorRef[Command]])

  // handlers (commands and events)
  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) =>
    command match {
      case createCmd @ CreateBankAccount(_, _, _, _) =>
        val id = UUID.randomUUID().toString
        val newBankAccount = context.spawn(PersistentBankAccount(id), id)

        Effect
          .persist(BankAccountCreated(id))
          .thenReply(newBankAccount)(_ => createCmd)

      case updateCmd @ UpdateBalance(id, _, _, replyTo) =>
        state.accounts.get(id) match {
          case Some(acc) =>
            Effect.reply(acc)(updateCmd)
          case None =>
            Effect.reply(replyTo)(BankAccountBalanceUpdatedResponse(Failure(new RuntimeException(s"Bank account cannot be found by id: $id"))))
        }

      case getCmd @ GetBankAccount(id, replyTo) =>
        state.accounts.get(id) match {
          case Some(acc) =>
            Effect.reply(acc)(getCmd)
          case None =>
            Effect.reply(replyTo)(GetBankAccountResponse(None))
        }
    }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) =>
    event match {
      case BankAccountCreated(id) =>
        val account = context.child(id)
          .getOrElse(context.spawn(PersistentBankAccount(id), id))
          .asInstanceOf[ActorRef[Command]] // it already has the right type, compilation problem

      state.copy(state.accounts + (id -> account))
    }

  // behaviour
  def apply(): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("bank"),
      emptyState = State(Map.empty[String, ActorRef[Command]]),
      commandHandler = commandHandler(context),
      eventHandler = eventHandler(context)
    )
  }
}
