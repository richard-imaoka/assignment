package com.paidy.identifiers.actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import com.paidy.authorizations.actors.{FraudStatusGateway, FraudStatusGatewayParent}
import com.paidy.authorizations.actors.FraudStatusGateway.HeartBeatRequest
import com.paidy.identifiers.actors.AddressIdHealthChecker.HeartBeat

import scala.concurrent.duration._
import akka.pattern._
import akka.util.Timeout
import com.paidy.authorizations.actors.FraudStatusGatewayParent.CreateChild

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object AddressIdHealthChecker {
  def props(addressID: UUID): Props = Props(new AddressIdHealthChecker(addressID))

  abstract sealed class MsgType
  case object HeartBeat extends MsgType
}

/**
  * Created by yunishiyama on 2017/04/25.
  */
class AddressIdHealthChecker(addressID: UUID) extends Actor with ActorLogging {

  // activate the extension
  val mediator = DistributedPubSub(context.system).mediator

  override def preStart(): Unit = {
    // register to the path
    log.info(s"${getClass} is starting at ${self.path} for addressID = ${addressID}")


    implicit val timeout: Timeout = 30 second
    implicit val ec: ExecutionContext = context.dispatcher

    context.system.scheduler.schedule(60 second, 120 second){
      val fut = mediator ? Send(path=FraudStatusGateway.path(addressID), msg=HeartBeatRequest, localAffinity=false)

      fut.onComplete {
        case Success(_) =>
          log.info(s"FraudStatusGateway for ${addressID} is live, HeartBeat received")
        case Failure(_) =>
          log.info(s"FraudStatusGateway for ${addressID} seems dead, creating")
          mediator ! Send(path = FraudStatusGatewayParent.path, msg = CreateChild(addressID), localAffinity = false)
      }
    }
  }

  override def receive: Receive = {
    case HeartBeat =>
      log.info(s"FraudStatusGateway for ${addressID} is live, HeartBeat received")
  }
}
