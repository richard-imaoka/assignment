package com.paidy.domain

import java.util.UUID

case class Address(
                    addressID: UUID,
                    line1: String,
                    line2: String,
                    city: String,
                    state: String,
                    zip: String
                  )