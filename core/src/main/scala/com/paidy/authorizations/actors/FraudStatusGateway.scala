package com.paidy.authorizations.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Put, Send, Subscribe}
import akka.pattern._
import akka.util.Timeout
import com.paidy.authorizations.actors.FraudStatusGateway.{ScoreUpdateRequest, StatusRequest, StatusResponse}
import com.paidy.authorizations.actors.FraudScoreGateway.{ScoreRequest, ScoreResponse}
import com.paidy.domain.Address

import scala.collection.immutable.Queue
import scala.concurrent.duration._

/**
  * Created by yunishiyama on 2017/04/05.
  */

object FraudStatusGateway {
  case class StatusResponse(status: Boolean, address: Address)
  case class StatusRequest(address: Address)
  case class ScoreUpdateRequest(score: Double, address: Address)
  def props: Props = Props(new FraudStatusGateway)
}

class FraudStatusGateway extends Actor with ActorLogging{
  log.info(s"${getClass} ${self.path} starting" )

  implicit val timeout = Timeout(5 seconds)
  private implicit val ec = context.dispatcher

  // activate the extension
  private val mediator = DistributedPubSub(context.system).mediator
  mediator ! Put(self)
  mediator ! Subscribe("cacher", self)

  /**
   *  Internal state of the actor, caching score histories
   *  Only update it from inside the receive method
   */
  private var scoreHistory = Map[String, Queue[Double]]()
  private val referHistoricalScoreUpTo = 10

  def takeUpToNlastScores(score: Double, historicalScores: Queue[Double], N: Int): Queue[Double] ={
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
      log.info(s"${this.getClass} received status request: $address")
      val key = address.toString

      // freeze the score history so that it's not updated while waiting for the current score
      val scoreHistoryAsOfRequested = scoreHistory.getOrElse(key, Queue[Double]())
      log.info("score history as of request time:\n", scoreHistoryAsOfRequested)

      val askFut = mediator ? Send(path = "/user/scorer", msg = ScoreRequest(address), localAffinity = false)

      askFut
        .mapTo[ScoreResponse]
        .map(res => {
          log.info(s"${this.getClass} received score response: $address")
          val status = judge(res.score, scoreHistoryAsOfRequested)
          log.info(s"Fraud check status = ${status}, with current score = ${res.score}, and historical scores: ${scoreHistoryAsOfRequested}")
          StatusResponse(status, address)
        })
        .pipeTo(sender())

    case ScoreUpdateRequest(score, address) =>
      log.info(s"${this.getClass} received score update: $address")
      val key = address.toString
      val historicalScores = scoreHistory.getOrElse(key, Queue[Double]())

      log.info(s"${this.getClass} previous historical scores:\n$historicalScores")
      val updatedHistoricalScores = takeUpToNlastScores(score, historicalScores, referHistoricalScoreUpTo)

      log.info(s"${this.getClass} updated historical scores:\n$updatedHistoricalScores")
      scoreHistory = scoreHistory.updated(key, updatedHistoricalScores)
  }
}