package com.yewenhai.iot

import akka.actor.typed.ActorRef

class RequestTrackDevice(val groupId: String, val deviceId: String, val replyTo: ActorRef<DeviceRegistered>) : DeviceGroupCommand, DeviceManagerCommand


