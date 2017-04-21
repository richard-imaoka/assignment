package com.paidy.identifiers.actors

import java.util.UUID

import akka.actor.{ActorLogging, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Put, Send}
import akka.pattern._
import akka.persistence.PersistentActor
import akka.util.Timeout
import com.paidy.authorizations.actors.FraudStatusGatewayParent
import com.paidy.authorizations.actors.FraudStatusGatewayParent.CreateChild
import com.paidy.identifiers.actors.AddressIdManager.{IdRequest, IdResponse}

import scala.concurrent.duration._

object AddressIdManager {
  case object IdRequest
  case class IdResponse(addressID: UUID)

  def props: Props = Props(new AddressIdManager)
  val name: String = "addressid-manager"
  val path: String = "/user/" + name
}

class AddressIdManager extends PersistentActor with ActorLogging {

  private implicit val timeout = Timeout(5 seconds)
  private implicit val ec = context.system.dispatcher

  override def persistenceId: String = "address-id-manager"

  /**
    * internal state of the actor
    */
  protected var existingAddressIDs: List[UUID] = List[UUID]()

  // activate the extension
  val mediator = DistributedPubSub(context.system).mediator

  override def preStart(): Unit = {
    // register to the path
    mediator ! Put(self)
    log.info(s"${getClass} is starting at ${self.path}")
  }

  override val receiveRecover = {
    case addressIDString: String =>
      existingAddressIDs = UUID.fromString(addressIDString) :: existingAddressIDs
//    case SnapshotOffer(_, snapshot: List[]) =>
//      existingAddressIDs = snapshot.map(UUID.fromString(_))
  }

  override def receiveCommand = {
    case IdRequest =>
      log.info(s"IdRequest received.")
      val addressID = UUID.randomUUID()

      // this actor goes into the persist mode so that incoming commands are stashed until persist's callback is completed
      persist(addressID){
        aid => {
          log.info(s"address with ID = ${aid} persisted successfully")
          existingAddressIDs = aid :: existingAddressIDs
        }
      }

      mediator ? Send(path = FraudStatusGatewayParent.path, msg = CreateChild(addressID), localAffinity = false)

      log.info(s"Returning addressID=${addressID} to sender=${sender()}")
      sender() ! IdResponse(addressID)
  }
}