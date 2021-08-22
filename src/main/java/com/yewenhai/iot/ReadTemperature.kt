package com.yewenhai.iot

import akka.actor.typed.ActorRef


class ReadTemperature(val requestId: Long, val replyTo: ActorRef<RespondTemperature>) : DeviceCommand