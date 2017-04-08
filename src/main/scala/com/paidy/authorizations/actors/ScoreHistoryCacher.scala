package com.paidy.authorizations.actors

import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer.ScoreAddress
import com.paidy.authorizations.actors.ScoreHistoryCacher.ScoreRequest
import com.paidy.domain.Address

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Created by yunishiyama on 2017/04/05.
  */

object ScoreHistoryCacher {
  def props: Props = Props(new ScoreHistoryCacher)
  case class ScoreRequest(address: Address)
}

class ScoreHistoryCacher extends Actor {

  /**
   *  Internal state of the actor, caching score histories
   *  Only update it from inside the receive method
   */
  var scoreHistory = Map[String, Queue[Double]]()

  val scorer = context.actorOf(AddressFraudProbabilityScorer.props)
  implicit val timeout = Timeout(5 seconds)
  implicit val excecutionContext = context.dispatcher

  def updateHistoricalScores(score: Double, key: String, history: Map[String, Queue[Double]]): Unit = {
    val historicalScores: Queue[Double] = getHistoricalScoresUpTp10(score, key, history)
    scoreHistory = history.updated(key, historicalScores)
  }

  def getHistoricalScoresUpTp10(score: Double, key: String, history: Map[String, Queue[Double]]): Queue[Double] = {
    val historicalScores = history.getOrElse(key, Queue[Double]())

    if(historicalScores.size == 10){
      val (_, historical9) = historicalScores.dequeue
      historical9.enqueue(score)
    }
    else
      historicalScores.enqueue(score)
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
    case ScoreRequest(address) =>
      println("middleman received scoring request: ", address)

      // We use Future by the ask pattern, as Scorer only returns double,
      // but we need to tie it up to the address (in closure, as callback of future onComplete)
      val fut = scorer ? ScoreAddress(address)
      val scoreRequestor = sender()

      fut.onComplete{
        case Success(data) =>
          println("middleman received score: ", data)

          val score = data.asInstanceOf[Double]
          val key = address.toString
          val historicalScores = getHistoricalScoresUpTp10(score, key, scoreHistory)
          val status = judge(score, historicalScores)

          scoreHistory = scoreHistory.updated(key, historicalScores)
          scoreRequestor ! status

        case Failure(exception) =>
          println("Middleman failed to get a response from scorer")
          println(exception)
      }
  }
}