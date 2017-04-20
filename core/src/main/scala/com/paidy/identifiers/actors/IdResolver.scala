package com.paidy.identifiers.actors

import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Put, Send}
import akka.pattern._
import akka.persistence.PersistentActor
import akka.util.Timeout
import com.paidy.authorizations.actors.FraudStatusGatewayParent
import com.paidy.authorizations.actors.FraudStatusGatewayParent.CreateChild
import com.paidy.domain.Address2
import com.paidy.identifiers.actors.IdResolver.{IdFound, IdRequest}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object IdResolver {
  case class IdRequest(address: Address2)
  case class IdFound(addressID: UUID)

  def props: Props = Props(new IdResolver)
  val name: String = "id-resolver"
  val path: String = "/user/" + IdResolver.name
}

abstract class IdResolverBase extends PersistentActor with ActorLogging {

  private implicit val timeout = Timeout(5 seconds)
  private implicit val ec = context.system.dispatcher

  override def persistenceId: String = "address-id"

  /**
    * internal state of the actor
    */
  protected var existingAddressIDs: List[UUID] = List[UUID]()

  // activate the extension
  protected val mediator : ActorRef

  override def preStart(): Unit = {
    // register to the path
    mediator ! Put(self)
    log.info(s"${getClass} is starting at ${self.path}" )
  }

  override val receiveRecover = {
    case addressIDString: String =>
      existingAddressIDs = UUID.fromString(addressIDString) :: existingAddressIDs
//    case SnapshotOffer(_, snapshot: List[]) =>
//      existingAddressIDs = snapshot.map(UUID.fromString(_))
  }

  override def receiveCommand = {
    case IdRequest(address) =>
      existingAddressIDs.find( addressID => addressID.equals(address.addressID) ) match {
        case Some(addressID) =>
          log.info(s"existing addressID=${addressID} found.")
          //Assumption is that, if addressID is found, then an actor already exists in the cluster (Akka Cluster) somewhere
          sender() ! IdFound(addressID)

        case None =>
          log.info(s"addressID=${address.addressID} is new, not found in the existing list.")
          existingAddressIDs = address.addressID :: existingAddressIDs

          val idRequestSender = sender()
          //If addressID is not found, you need to create an actor for the addressID
          val askFut = mediator ? Send(path = FraudStatusGatewayParent.path, msg = CreateChild(address.addressID), localAffinity = false)
          askFut.onComplete{
            case Success(_) =>
              log.info(s"actor creation for ${address.addressID} was successful")

              // this actor goes into the persist mode so that incoming commands are stashed
              // until persist's callback is completed
              persist(address.addressID.toString){
                addressIDStirng => {
                  log.info(s"address with ID = ${addressIDStirng} persisted successfully")
                  //existingAddressIDs = address.addressID :: existingAddressIDs
                }
              }

              log.info(s"IdFound(${address.addressID}) is sent back to ${idRequestSender}")
              idRequestSender ! IdFound(address.addressID)

            case Failure(e) =>
              log.error(e, s"actor creation for ${address.addressID} failed")
          }
      }
  }
}

class IdResolver extends IdResolverBase {
  override val mediator = DistributedPubSub(context.system).mediator
}