package com.paidy.authorizations.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.pattern._
import akka.util.Timeout
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer.ScoreAddress
import com.paidy.authorizations.actors.FraudScoreGateway.ScoreRequest
import com.paidy.authorizations.actors.FraudStatusGateway.ScoreResponse
import com.paidy.domain.Address

import scala.concurrent.duration._

class FraudScoreGateway extends Actor with ActorLogging {
  import akka.cluster.pubsub.DistributedPubSubMediator.Put
  val mediator = DistributedPubSub(context.system).mediator

  val scorer = context.actorOf(AddressFraudProbabilityScorer.props)

  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.system.dispatcher

  override def preStart(): Unit = {
    // register to the path
    mediator ! Put(self)
    log.info(s"${getClass} is starting at ${self.path}" )
  }

  override def receive: Receive = {
    case ScoreRequest(address, originalRequester) =>
      log.info(s"received ${ScoreRequest(address, originalRequester)}")
      (scorer ? ScoreAddress(address))
        .mapTo[Double]
        .map(score => {
          log.info(s"returning score = ${score}")
          ScoreResponse(score, address, originalRequester)
        })
        .pipeTo(sender())
  }
}

object FraudScoreGateway {
  abstract sealed class MsgType
  case class ScoreRequest(address: Address, originalRequester: Option[ActorRef]) extends MsgType


  val name: String = "score-gateway"
  val path: String = "/user/" + name

  def props: Props = Props(new FraudScoreGateway)
}