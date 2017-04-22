package com.paidy.authorizations.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.pattern._
import akka.util.Timeout
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer.ScoreAddress
import com.paidy.authorizations.actors.FraudScoreGateway.{ScoreRequest, ScoreRequestNoResponse, ScoreResponse}
import com.paidy.authorizations.actors.FraudStatusGateway.ScoreUpdateRequest
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
    case ScoreRequest(address) =>
      log.info(s"received ${ScoreRequest(address)}")
      (scorer ? ScoreAddress(address))
        .mapTo[Double]
        .map(score => {
          log.info(s"returning score = ${score}")
          mediator ? Publish(FraudStatusGateway.topic, ScoreUpdateRequest(score, address))
          ScoreResponse(score, address)
        })
        .pipeTo(sender())

    case ScoreRequestNoResponse(address) =>
      log.info(s"received ${ScoreRequestNoResponse(address)}")
      (scorer ? ScoreAddress(address))
        .mapTo[Double]
        .map(score => {
          log.info(s"returning score = ${score}")
          mediator ? Publish(FraudStatusGateway.topic, ScoreUpdateRequest(score, address))
        })

  }
}

object FraudScoreGateway {
  case class ScoreRequest(address: Address)
  case class ScoreRequestNoResponse(address: Address)
  case class ScoreResponse(score: Double, address: Address)

  val name: String = "score-gateway"
  val path: String = "/user/" + name

  def props: Props = Props(new FraudScoreGateway)
}