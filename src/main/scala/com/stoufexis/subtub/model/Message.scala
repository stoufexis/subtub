package com.stoufexis.subtub.model

import io.circe.Codec

case class Message(payload: String) derives Codec
