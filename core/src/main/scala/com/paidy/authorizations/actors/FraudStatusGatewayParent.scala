package com.paidy.authorizations.actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging}
import com.paidy.authorizations.actors.FraudStatusGatewayParent.{ChildCreated, CreateChild}

object FraudStatusGatewayParent {
  case class CreateChild(addressID: UUID)
  case class ChildCreated(addressID: UUID)
}

class FraudStatusGatewayParent extends Actor with ActorLogging {
  override def receive : Receive = {
    case CreateChild(addressID) =>
      context.actorOf(FraudStatusGateway2.props(addressID))
      sender() ! ChildCreated(addressID)
  }
}
