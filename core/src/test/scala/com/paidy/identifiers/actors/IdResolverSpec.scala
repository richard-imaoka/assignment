package com.paidy.identifiers.actors

import java.util.UUID

import akka.actor._
import com.paidy.domain.Address
import com.paidy.identifiers.actors.AddressIdManager.{IdResponse, IdRequest}
import com.paidy.identifiers.actors.AddressIdManagerTester.GetAddressIDs
import com.paidy.persistence.MySpec


object AddressIdManagerTester {
  case object GetAddressIDs

  def props = Props( new AddressIdManagerTester )
}

class EchoActor extends Actor {
  override def receive = {
    case _: Any =>
      sender() ! "boom"
  }
}

class AddressIdManagerTester extends AddressIdManager {
  override def receiveCommand = super.receiveCommand orElse {
    case GetAddressIDs =>
      sender() ! existingAddressIDs
  }

  override val mediator: ActorRef = context.actorOf(Props(new EchoActor))
}

class AddressIdManagerSpec extends MySpec(MySpec.config("leveldb", "IdManagerSpec")) {

  "An AddressIdManagerSpec" must {
    "recover existingAddressIds from persistence" in {

      val actor = system.actorOf(AddressIdManagerTester.props)

      val addressID1 = UUID.randomUUID()
      actor ! IdRequest(Address(addressID1, "Minami-Nagasaki", "4-25-9", "Toshima-ku", "Tokyo", ""))

      val addressID2 = UUID.randomUUID()
      actor ! IdRequest(Address(addressID2, "Minami-Nagasaki", "4-26-3", "Toshima-ku", "Tokyo", ""))

      //actor ! GetAddressIDs

      expectMsg(IdResponse(addressID1))
      expectMsg(IdResponse(addressID2))
    }
  }
}
