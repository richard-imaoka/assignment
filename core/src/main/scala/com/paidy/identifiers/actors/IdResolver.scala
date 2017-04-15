package com.paidy.identifiers.actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import akka.pattern._
import com.paidy.authorizations.actors.FraudStatusGateway2
import com.paidy.domain.Address2
import com.paidy.identifiers.actors.IdResolver.{IdFound, IdRequest}

import scala.util.{Failure, Success}

class IdResolver extends Actor with ActorLogging {

  private var identifiers: List[UUID] = List[UUID]()

  // activate the extension
  private val mediator = DistributedPubSub(context.system).mediator

  override def preStart(): Unit = {
    identifiers = List[UUID]()
  }

  override def receive: Receive = {
    case IdRequest(address) =>
      identifiers.find( id => id.equals(address.id) ) match {
        case Some(id) =>
          //Assumption is that, if id is found, then an actor already exists in the cluster (Akka Cluster) somewhere
          sender() ! IdFound(id)
        case None =>
          //If id is not found, you need to create an actor for the id
          val id = UUID.randomUUID()
          val askFut = mediator ? Send(path = "/user/status", msg = FraudStatusGateway2.CreateActorRequest(id), localAffinity = false)
          askFut.onComplete{
            case Success(_) =>
              log.info(s"actor creation for ${id} was successful")
              sender() ! IdFound(id)
            case Failure(e) =>
              log.error(e, s"actor creation for ${id} failed")
          }
      }
  }

}

object IdResolver {
  case class IdRequest(address: Address2)
  case class IdFound(id: UUID)
}