package com.yewenhai.iot

import akka.actor.typed.ActorRef

class RequestDeviceList(val requestId: Long, val groupId: String, val replyTo: ActorRef<ReplyDeviceList>) :
    DeviceGroupCommand, DeviceManagerCommand {

}