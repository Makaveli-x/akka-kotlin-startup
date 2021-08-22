package com.yewenhai.iot

import akka.actor.typed.ActorRef

class DeviceTerminated(val actorRef: ActorRef<DeviceCommand>, val groupId: String, val deviceId: String) : DeviceGroupCommand {

}