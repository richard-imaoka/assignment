package com.paidy.identifiers.actors

import java.util.UUID

import akka.actor.{ActorLogging, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Put, Send}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout
import com.paidy.authorizations.actors.FraudStatusGatewayParent
import com.paidy.authorizations.actors.FraudStatusGatewayParent.CreateChild
import com.paidy.identifiers.actors.AddressIdManager.{GetAllAddressIDs, IdRequest, IdResponse}

import scala.concurrent.duration._

object AddressIdManager {
  abstract sealed class MsgType
  case object IdRequest extends MsgType
  case object GetAllAddressIDs extends MsgType

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

  override val receiveRecover : Receive = {
    case addressID: UUID =>
      existingAddressIDs = addressID :: existingAddressIDs
    case SnapshotOffer(_, snapshot: List[UUID]) =>
      existingAddressIDs = snapshot
    case RecoveryCompleted =>
      existingAddressIDs.foreach{
        aid => {
          log.info(s"creating health checker for ${aid}")
          context.actorOf(AddressIdHealthChecker.props(aid), aid.toString)
        }
      }
  }

  override def receiveCommand : Receive = {
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

      mediator ! Send(path = FraudStatusGatewayParent.path, msg = CreateChild(addressID), localAffinity = false)
      log.info(s"Returning addressID=${addressID} to sender=${sender()}")
      sender() ! IdResponse(addressID)

      log.info(s"creating health checker for ${addressID}")
      context.actorOf(AddressIdHealthChecker.props(addressID), addressID.toString)

    case GetAllAddressIDs =>
      log.info(s"GetAllAddressIDs request received, so returning existing address IDS = ${existingAddressIDs}")
      sender() ! existingAddressIDs
  }
}