package com.paidy.authorizations.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Send}
import akka.pattern._
import akka.util.Timeout
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer.ScoreAddress
import com.paidy.authorizations.actors.ScoreHistoryCacher.ScoreUpdateRequest
import com.paidy.authorizations.actors.ScorerDestination.{ScoreRequest, ScoreResponse}
import com.paidy.domain.Address

import scala.concurrent.duration._
/**
  * Created by yunishiyama on 2017/04/08.
  */
class ScorerDestination extends Actor with ActorLogging {
  import akka.cluster.pubsub.DistributedPubSubMediator.Put
  println(getClass, this.self.path)

  val mediator = DistributedPubSub(context.system).mediator
  val scorer = context.actorOf(AddressFraudProbabilityScorer.props)
  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.system.dispatcher

  // register to the path
  mediator ! Put(self)

  override def receive: Receive = {
    case ScoreRequest(address) =>
      log.info(s"received ${ScoreRequest(address)}")
      (scorer ? ScoreAddress(address))
        .mapTo[Double]
        .map(score => {
          mediator ? Publish("cacher", ScoreUpdateRequest(score, address))
          ScoreResponse(score, address)
        })
        .pipeTo(sender())
  }
}

object ScorerDestination {
  case class ScoreRequest(address: Address)
  case class ScoreResponse(score: Double, address: Address)

  def props: Props = Props(new ScorerDestination)
}