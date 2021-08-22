package com.yewenhai.iot

import akka.actor.typed.ActorRef

class RequestAllTemperature(val requestId: Long, val groupId: String, val replyTo: ActorRef<RespondAllTemperature>) : DeviceGroupQueryCommand, DeviceGroupCommand, DeviceCommand {

}