package com.paidy.authorizations.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.pattern._
import akka.util.Timeout
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer.ScoreAddress
import com.paidy.authorizations.actors.MiddleMan.ScoreRequest

import scala.concurrent.duration._
/**
  * Created by yunishiyama on 2017/04/08.
  */
class ScorerDestination extends Actor with ActorLogging {
  import akka.cluster.pubsub.DistributedPubSubMediator.Put

  val mediator = DistributedPubSub(context.system).mediator
  val scorer = context.actorOf(AddressFraudProbabilityScorer.props)
  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.system.dispatcher

  // register to the path
  mediator ! Put(self)

  override def receive: Receive = {
    case ScoreRequest(address) =>
      (scorer ? ScoreAddress(address)) pipeTo sender()
  }
}

object ScorerDestination {
  def props: Props = Props(new ScorerDestination)
}