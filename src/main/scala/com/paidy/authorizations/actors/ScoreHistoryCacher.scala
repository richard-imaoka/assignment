package com.paidy.authorizations.actors

import akka.actor.{Actor, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.pattern._
import akka.util.Timeout
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer.ScoreAddress
import com.paidy.authorizations.actors.ScoreHistoryCacher.{ScoreUpdateRequest, StatusRequest, StatusResponse}
import com.paidy.authorizations.actors.ScorerDestination.{ScoreRequest, ScoreResponse}
import com.paidy.domain.Address

import scala.collection.immutable.Queue
import scala.concurrent.duration._

/**
  * Created by yunishiyama on 2017/04/05.
  */

object ScoreHistoryCacher {
  case class StatusResponse(status: Boolean, address: Address)
  case class StatusRequest(address: Address)
  case class ScoreUpdateRequest(score: Double, address: Address)
  def props: Props = Props(new ScoreHistoryCacher)
}

class ScoreHistoryCacher extends Actor {

  // activate the extension
  val mediator = DistributedPubSub(context.system).mediator

  val referHistoricalScoreUpTo = 10

  /**
   *  Internal state of the actor, caching score histories
   *  Only update it from inside the receive method
   */
  var scoreHistory = Map[String, Queue[Double]]()

  val scorer = context.actorOf(AddressFraudProbabilityScorer.props)
  implicit val timeout = Timeout(5 seconds)
  implicit val excecutionContext = context.dispatcher

  def judgeByHistoricalScores(historicalScores: Queue[Double]): Boolean = {
    if(historicalScores.size < 10)
      true
    else {
      val average = historicalScores.foldLeft(0.0)((x, y) => x+y) / historicalScores.size
      average < 0.7
    }
  }

  def judgeByScore(score: Double): Boolean = score < 0.78

  def judge(score: Double, historicalScores: Queue[Double]): Boolean = {
    judgeByScore(score) && judgeByHistoricalScores(historicalScores)
  }

  override def receive: Receive = {
    case StatusRequest(address) =>
      println("middleman received scoring request: ", address)

      val key = address.toString

      // freeze the score history so that it's not updated while waiting for the current score
      val scoreHistoryAsOfRequested = scoreHistory.getOrElse(key, Queue[Double]())

      val message = ScoreAddress(address)
      val askFut = scorer ? message
      askFut
        .mapTo[ScoreResponse]
        .map(res => StatusResponse(judge(res.score, scoreHistoryAsOfRequested), address))
        .pipeTo(sender())
      //mediator ! Send(path = "/user/scorer", msg = ScoreAddress(address), localAffinity = false)

    case ScoreUpdateRequest(score, address) =>
      val key = address.toString
      val historicalScores = scoreHistory.getOrElse(key, Queue[Double]())

      val N: Int = referHistoricalScoreUpTo - 1
      val updatedHistoricalScores =
        if (historicalScores.size < N)
          historicalScores.enqueue(score)
        else if (historicalScores.size == N) {
          val (_, historicalN) = historicalScores.dequeue
          historicalN.enqueue(score)
        }
        else {
          //historicalScores.size > N, but this should never happen...
          historicalScores.enqueue(score)
        }
      scoreHistory.updated(key, updatedHistoricalScores)
  }
}