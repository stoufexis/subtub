package com.stoufexis.subtub.model

import io.circe.Codec

case class Message(publish_to: StreamId, payload: String) derives Codec
