package com.yewenhai.iot

import akka.actor.typed.ActorRef

class RecordTemperature(val requestId: Long, val value: Double, val replyTo: ActorRef<TemperatureRecorded>) : DeviceCommand {


}