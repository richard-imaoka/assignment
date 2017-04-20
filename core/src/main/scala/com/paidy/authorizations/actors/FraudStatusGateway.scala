package com.paidy.authorizations.actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Put, Send, Subscribe}
import akka.pattern._
import akka.util.Timeout
import com.paidy.authorizations.actors.FraudScoreGateway.{ScoreRequest, ScoreRequestNoResponse, ScoreResponse}
import com.paidy.authorizations.actors.FraudStatusGateway.{ScoreUpdateRequest, StatusRequest, StatusResponse}
import com.paidy.domain.Address

import scala.collection.immutable.Queue
import scala.concurrent.duration._

object FraudStatusGateway {
  case class StatusResponse(status: Boolean, address: Address)
  case class StatusRequest(address: Address)
  case class ScoreUpdateRequest(score: Double, address: Address)

  def props(addressId: UUID): Props = Props(new FraudStatusGateway(addressId))

  def path(addressID: UUID): String = FraudStatusGatewayParent.path + "/" + addressID.toString
}

class FraudStatusGateway(val addressID: UUID) extends Actor with ActorLogging {

  private implicit val timeout = Timeout(5 seconds)
  private implicit val ec = context.dispatcher

  // activate the extension
  private val mediator = DistributedPubSub(context.system).mediator

  /**
    * Internal state of the actor, caching score histories
    * Only update it from inside the receive method
    */
  private var historicalScores: Queue[Double] = Queue[Double]()
  private val maxSizeOfHistoricalScores: Int = 10 // historicalScores are kept up to this size

  override def preStart(): Unit = {
    mediator ! Put(self)
    mediator ! Subscribe("cacher", self)
    log.info(s"${getClass} is starting at ${self.path}")
  }

  def takeUpToNlastScores(score: Double, historicalScores: Queue[Double], N: Int): Queue[Double] = {
    if (historicalScores.size < N)
      historicalScores.enqueue(score)
    else if (historicalScores.size == N) {
      val (_, historicalUpToNminus1) = historicalScores.dequeue
      historicalUpToNminus1.enqueue(score) //N - 1 + new element = N elements
    }
    else {
      //historicalScores.size > N, but this should never happen...
      log.warning(s"historical scores had ${historicalScores.size} elements, this should have never happened")
      historicalScores.slice(0, N).enqueue(score) //slice(0,N) returns N-1 sized queue
    }
  }

  // assumption is that it is called only when historicalScores.size = maxSizeOfHistoricalScores
  // as historicalScores is controlled in takeUpTpNLastScores()
  def judgeByHistoricalScores(historicalScores: Queue[Double]): Boolean = {
    val average = historicalScores.foldLeft(0.0)((x, y) => x + y) / historicalScores.size
    average < 0.7
  }

  def judgeByScore(score: Double): Boolean = score < 0.78

  override def receive: Receive = {
    case StatusRequest(address) =>
      log.info(s"${this.getClass} received status request for address =${address}")

      if (historicalScores.size < maxSizeOfHistoricalScores) {
        // If no sufficient history, ask for a new score
        val askFut = mediator ? Send(path = "/user/scorer", msg = ScoreRequest(address), localAffinity = false)
        askFut
          .mapTo[ScoreResponse]
          .map(res => {
            log.info(s"${this.getClass} received score response: $address")
            val status = judgeByScore(res.score)
            log.info(s"Fraud check status = ${status}, with current score = ${res.score}")
            StatusResponse(status, address)
          })
          .pipeTo(sender())
      }
      else {
        // If there are sufficient number of historical scores, 1. return first, then 2. tell a new score request to update historical scores
        if (historicalScores != maxSizeOfHistoricalScores)
          log.warning(s"Fraud status is calculated from historical scores with size = ${historicalScores.size}, although expected size = ${maxSizeOfHistoricalScores}")

        val status = judgeByHistoricalScores(historicalScores)
        log.info(s"Fraud check status = ${status} from historical scores: ${historicalScores}")

        // 1. return first
        sender() ! StatusResponse(status, address)

        // 2. tell a new score request to update historical scores
        mediator ! Send(path = "/user/scorer", msg = ScoreRequestNoResponse(address), localAffinity = false)
      }

    case ScoreUpdateRequest(score, address) =>
      log.info(s"${this.getClass} received score update: $address")
      log.info(s"${this.getClass} previous historical scores:\n$historicalScores")

      historicalScores = takeUpToNlastScores(score, historicalScores, maxSizeOfHistoricalScores)
      log.info(s"${this.getClass} updated historical scores:\n$historicalScores")

  }
}
