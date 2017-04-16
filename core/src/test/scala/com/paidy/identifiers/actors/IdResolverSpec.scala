package com.paidy.identifiers.actors

import java.util.UUID

import akka.actor._
import com.paidy.domain.Address2
import com.paidy.identifiers.actors.IdResolver.IdRequest
import com.paidy.identifiers.actors.IdResolverTester.GetAddressIDs
import com.paidy.persistence.MySpec


object IdResolverTester {
  case object GetAddressIDs
}

class IdResolverTester extends IdResolver{
  override def receiveCommand = super.receiveCommand orElse {
    case GetAddressIDs =>
      sender() ! existingAddressIDs
  }
}

class IdResolverSpec extends MySpec(MySpec.config("leveldb", "IdResolveSpec")) {

  "A IdResolverSpec" must {
    "recover existingAddressIds from persistence" in {
      val actor = system.actorOf(IdResolver.props)

      val addressID1 = UUID.randomUUID()
      actor ! IdRequest(Address2(addressID1, "Minami-Nagasaki", "4-25-9", "Toshima-ku", "Tokyo", ""))

      val addressID2 = UUID.randomUUID()
      actor ! IdRequest(Address2(addressID1, "Minami-Nagasaki", "4-26-3", "Toshima-ku", "Tokyo", ""))

      actor ! GetAddressIDs
      expectMsg(List(addressID1, addressID2))
    }
  }
}
