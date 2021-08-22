package com.yewenhai.iot

import akka.actor.typed.ActorRef

class DeviceRegistered(val device: ActorRef<DeviceCommand>) {

}