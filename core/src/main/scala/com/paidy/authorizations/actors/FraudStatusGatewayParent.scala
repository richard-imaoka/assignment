package com.paidy.authorizations.actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Put
import com.paidy.authorizations.actors.FraudStatusGatewayParent.{ChildCreated, CreateChild}

object FraudStatusGatewayParent {
  case class CreateChild(addressID: UUID)
  case class ChildCreated(addressID: UUID)

  def props: Props = Props(new FraudStatusGatewayParent)

  val name: String = "fraud-status-parent"
  val path: String = "/user/" + name
}

class FraudStatusGatewayParent extends Actor with ActorLogging {

  val mediator = DistributedPubSub(context.system).mediator

  override def preStart(): Unit = {
    log.info(s"${getClass} is starting at ${self.path}" )
    // register to the path
    mediator ! Put(self)
  }

  override def receive : Receive = {
    case CreateChild(addressID) =>
      log.info(s"received CreateChild(${addressID})")
      context.actorOf(FraudStatusGateway2.props(addressID), addressID.toString)
      sender() ! ChildCreated(addressID)
    case x: Any =>
      log.info("what the hell??")
  }
}
