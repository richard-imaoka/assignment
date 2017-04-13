package com.paidy.authorizations.actors

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import com.paidy.domain.Address

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random


object AddressFraudProbabilityScorer {
  def props: Props = Props(new AddressFraudProbabilityScorer())

  case class ScoreAddress(address: Address)

}

class AddressFraudProbabilityScorer()
  extends Actor {

  import AddressFraudProbabilityScorer._

  implicit val timeout = Timeout(5.seconds)
  implicit val dispatcher = context.dispatcher

  def receive: Receive = {
    case ScoreAddress(address) =>
      scoreAddress(address) pipeTo sender()
  }

  private def scoreAddress(address: Address): Future[Double] = {
    if (simulateServiceFailure) {
      Future.failed(new Exception(s"Could not score address (address=$address)"))
    }
    Future.successful {
      val addressScore = Random.nextDouble()
      simulateServiceLatency()
      addressScore
    }
  }

  private def simulateServiceFailure = {
    Random.nextDouble() >= 0.98 // 2% of all scoring requests fail
  }

  private def simulateServiceLatency() = {
    Thread.sleep(Random.nextInt(7) * 1000)
  }
}