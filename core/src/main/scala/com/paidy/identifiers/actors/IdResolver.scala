package com.paidy.identifiers.actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import akka.pattern._
import akka.persistence.PersistentActor
import com.paidy.authorizations.actors.FraudStatusGateway2
import com.paidy.domain.Address2
import com.paidy.identifiers.actors.IdResolver.{IdFound, IdRequest}

import scala.util.{Failure, Success}

class IdResolver extends PersistentActor with ActorLogging {

  private var identifiers: List[UUID] = List[UUID]()

  // activate the extension
  private val mediator = DistributedPubSub(context.system).mediator

  override def preStart(): Unit = {
    identifiers = List[UUID]()
  }

  override def receive: Receive = {
    case IdRequest(address) =>
      identifiers.find( addressID => addressID.equals(address.addressID) ) match {
        case Some(addressID) =>
          //Assumption is that, if addressID is found, then an actor already exists in the cluster (Akka Cluster) somewhere
          sender() ! IdFound(addressID)
        case None =>
          //If addressID is not found, you need to create an actor for the addressID
          val addressID = UUID.randomUUID()
          val askFut = mediator ? Send(path = "/user/status", msg = FraudStatusGateway2.CreateActorRequest(addressID), localAffinity = false)
          askFut.onComplete{
            case Success(_) =>
              log.info(s"actor creation for ${addressID} was successful")
              sender() ! IdFound(addressID)
            case Failure(e) =>
              log.error(e, s"actor creation for ${addressID} failed")
          }
      }
  }

}

object IdResolver {
  case class IdRequest(address: Address2)
  case class IdFound(addressID: UUID)
}