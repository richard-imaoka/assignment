package com.paidy.identifiers.actors

import java.util.UUID

import akka.actor.{ActorLogging, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import akka.pattern._
import akka.persistence.{PersistentActor, SnapshotOffer}
import akka.util.Timeout
import com.paidy.authorizations.actors.FraudStatusGateway2
import com.paidy.domain.Address2
import com.paidy.identifiers.actors.IdResolver.{IdFound, IdRequest}

import scala.util.{Failure, Success}
import scala.concurrent.duration._

object IdResolver {
  case class IdRequest(address: Address2)
  case class IdFound(addressID: UUID)

  def props: Props = Props(new IdResolver)
}

class IdResolver extends PersistentActor with ActorLogging {

  private implicit val timeout = Timeout(5 seconds)
  private implicit val ec = context.system.dispatcher

  override def persistenceId: String = "address-id"

  /**
    * internal state of the actor
    */
  protected var existingAddressIDs: List[UUID] = List[UUID]()

  // activate the extension
  private val mediator = DistributedPubSub(context.system).mediator

  override val receiveRecover = {
    case addressIDString: String =>
      existingAddressIDs = UUID.fromString(addressIDString) :: existingAddressIDs

//    case SnapshotOffer(_, snapshot: List[]) =>
//      existingAddressIDs = snapshot.map(UUID.fromString(_))
  }

  override def receiveCommand= {
    case IdRequest(address) =>
      existingAddressIDs.find( addressID => addressID.equals(address.addressID) ) match {
        case Some(addressID) =>
          log.info(s"existing addressID=${addressID} found")
          //Assumption is that, if addressID is found, then an actor already exists in the cluster (Akka Cluster) somewhere
          sender() ! IdFound(addressID)

        case None =>
          //If addressID is not found, you need to create an actor for the addressID
          val addressID = UUID.randomUUID()
          val askFut = mediator ? Send(path = "/user/status", msg = FraudStatusGateway2.CreateActorRequest(addressID), localAffinity = false)
          askFut.onComplete{
            case Success(_) =>
              log.info(s"actor creation for ${addressID} was successful")

              // this actor goes into the persist mode so that incoming commands are stashed
              // until persist's callback is completed
              persist(addressID.toString){
                addressIDStirng => {
                  log.info(s"address with ID = ${addressIDStirng} persisted successfully")
                  existingAddressIDs = addressID :: existingAddressIDs
                }
              }

              sender() ! IdFound(addressID)
            case Failure(e) =>
              log.error(e, s"actor creation for ${addressID} failed")
          }
      }
  }

}