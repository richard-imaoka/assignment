package com.paidy.authorizations.actors

import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Put, Send, Subscribe}
import akka.persistence.PersistentActor
import akka.util.Timeout
import com.paidy.authorizations.actors.FraudScoreGateway.ScoreRequest
import com.paidy.authorizations.actors.FraudStatusGateway.{GetHistoricalScores, ScoreResponse, StatusRequest, StatusResponse}
import com.paidy.domain.Address

import scala.collection.immutable.Queue
import scala.concurrent.duration._

object FraudStatusGateway {
  abstract sealed class MsgType
  case class StatusRequest(address: Address) extends MsgType
  case class ScoreResponse(score: Double, address: Address, originalRequester: Option[ActorRef]) extends MsgType
  case object GetHistoricalScores extends MsgType

  case class StatusResponse(status: Boolean, address: Address)

  def props(addressId: UUID): Props = Props(new FraudStatusGateway(addressId))

  val topic: String = "status-gateway-topic"
  def path(addressID: UUID): String = FraudStatusGatewayParent.path + "/" + addressID.toString
}

class FraudStatusGateway(val addressID: UUID) extends PersistentActor with ActorLogging {

  override val persistenceId = "fraud-status-" + addressID.toString

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
    mediator ! Subscribe(FraudStatusGateway.topic, self)
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

  override def receiveRecover: Receive = {
    case score: Double =>
      historicalScores = takeUpToNlastScores(score, historicalScores, maxSizeOfHistoricalScores)
  }

  override def receiveCommand: Receive = {
    case StatusRequest(address) =>
      log.info(s"${this.getClass} received status request for address =${address}")

      if(historicalScores.size == maxSizeOfHistoricalScores && !judgeByHistoricalScores(historicalScores)) {
        log.info("Return early with status = false, as historical scores had average >= 0.7")
        sender() ! StatusResponse(false, address)
        // ScoreRequest(address, None) ... None means no it will not send back status based on new score, as it's already sent above
        mediator ! Send(path = FraudScoreGateway.path, msg = ScoreRequest(address, None), localAffinity = false)
      }
      else {
        log.info("Asking backend for the current score")
        //Request for a score, and send back the status based on the returned score later
        mediator ! Send(path = FraudScoreGateway.path, msg = ScoreRequest(address, Some(sender())), localAffinity = false)
      }

    case ScoreResponse(score, address, originalRequester) =>
      log.info(s"Received ScoreResponse with score=${score}, address=$address")
      log.info(s"Previous historical scores: $historicalScores")

      persist(score){
        s => {
          historicalScores = takeUpToNlastScores(s, historicalScores, maxSizeOfHistoricalScores)
          log.info(s"Updated historical scores: $historicalScores")
        }
      }

      originalRequester match {
        case Some(requesterRef) =>
          val status = judgeByScore(score)
          log.info(s"Sending back fraud check status = ${status}, with current score = ${score}")
          requesterRef ! StatusResponse(status, address)
        case None =>
          log.info(s"Not sending back status based on score = ${score}, but updating (or already updated) historical scores")
      }

    case GetHistoricalScores =>
      log.info(s"GetHistorialScores received, so sending back historical scores = ${historicalScores}")
      sender() ! historicalScores

  }
}
