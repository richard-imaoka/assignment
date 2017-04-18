package com.paidy.identifiers.actors

import java.util.UUID

import akka.actor._
import com.paidy.domain.Address2
import com.paidy.identifiers.actors.IdResolver.{IdFound, IdRequest}
import com.paidy.identifiers.actors.IdResolverTester.GetAddressIDs
import com.paidy.persistence.MySpec


object IdResolverTester {
  case object GetAddressIDs

  def props = Props( new IdResolverTester )
}

class EchoActor extends Actor {
  override def receive = {
    case _: Any =>
      sender() ! "boom"
  }
}

class IdResolverTester extends IdResolverBase {
  override def receiveCommand = super.receiveCommand orElse {
    case GetAddressIDs =>
      sender() ! existingAddressIDs
  }

  override val mediator: ActorRef = context.actorOf(Props(new EchoActor))
}

class IdResolverSpec extends MySpec(MySpec.config("leveldb", "IdResolveSpec")) {

  "An IdResolverSpec" must {
    "recover existingAddressIds from persistence" in {

      val actor = system.actorOf(IdResolverTester.props)

      val addressID1 = UUID.randomUUID()
      actor ! IdRequest(Address2(addressID1, "Minami-Nagasaki", "4-25-9", "Toshima-ku", "Tokyo", ""))

      val addressID2 = UUID.randomUUID()
      actor ! IdRequest(Address2(addressID2, "Minami-Nagasaki", "4-26-3", "Toshima-ku", "Tokyo", ""))

      //actor ! GetAddressIDs

      expectMsg(IdFound(addressID1))
      expectMsg(IdFound(addressID2))
    }
  }
}
