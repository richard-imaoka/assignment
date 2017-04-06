package com.paidy.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer._
import com.paidy.domain.Address
import com.typesafe.config.ConfigFactory
import spray.json._

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val addressFormat = jsonFormat5(Address) //to marshall/unmarshall (i.e. JSON <-> Address) for 5-parameter case class
  implicit val returnJSONFormat = jsonFormat2(ReturnJSON)
}

case class ReturnJSON(status: Boolean, address: Address)

//backend failure -> 500
//timeout -> 5 seconds -> test
//timeout -> 500
//use logger instead of println
//read ip from config
//read port from config

object FraudCheckerServer extends Directives with JsonSupport{

  var scoreHistory = Map[String, Queue[Double]]()

  def getHistoricalScoresUpTp10(score: Double, address: Address, history: Map[String, Queue[Double]]): Queue[Double] = {
    val key = address.toString
    val historicalScores = history.getOrElse(key, Queue[Double]())

    if(historicalScores.size == 10){
      val (_, historical9) = historicalScores.dequeue
      historical9.enqueue(score)
    }
    else
      historicalScores.enqueue(score)
  }

  def judge(score: Double, address: Address, history: Map[String, Queue[Double]]): Boolean = {
    val historicalScores: Queue[Double] = getHistoricalScoresUpTp10(score, address, history)
    val average = historicalScores.foldLeft(0.0)((x, y) => x+y) / historicalScores.size
    println("average: ", average)

    val key = address.toString
    scoreHistory = history.updated(key, historicalScores)

    score < 0.78 && average < 0.7
  }

  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system",ConfigFactory.load())
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val scorer = system.actorOf(AddressFraudProbabilityScorer.props)
    implicit val timeout = Timeout(5 seconds)

    val route =
      path("check") {
        post {
          entity(as[Address]) { address =>
            println("received: ", address)
            val fut = scorer ? ScoreAddress(address)
            onComplete(fut){
              case Success(s) => {
                val score = s.asInstanceOf[Double]
                println("score: ", score)
                val status = judge(score, address, scoreHistory)
                complete(ReturnJSON(status, address))
              }
              case Failure(ex) => {
                failWith(ex) // this doesn't expose the error details to the HTTP client
              }
            }
          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}