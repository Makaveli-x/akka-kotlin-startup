package com.yewenhai.iot

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import java.util.*
import kotlin.collections.HashMap

class DeviceManager(context: ActorContext<DeviceManagerCommand>) : AbstractBehavior<DeviceManagerCommand>(context) {

    override fun createReceive(): Receive<DeviceManagerCommand> {
        return newReceiveBuilder()
            .onMessage(RequestTrackDevice::class.java) {request -> onTrackDevice(request)}
            .onMessage(RequestDeviceList::class.java) {request -> onRequestDeviceList(request)}
            .onMessage(DeviceGroupTerminated::class.java) {request -> onGroupTerminated(request)}
            .onSignal(PostStop::class.java) {onPostStop()}
            .build()
    }


    private fun onTrackDevice(request: RequestTrackDevice): DeviceManager {
        var groupActor = groupIdToActor[request.groupId]
        if (null == groupActor) {
            context.log.info("Creating device group actor for ${request.groupId}")
            groupActor = context.spawn(DeviceGroup.create(request.groupId), "group-${request.groupId}")
            context.watchWith(groupActor, DeviceGroupTerminated(request.groupId))
            groupIdToActor[request.groupId] = groupActor
        }
        groupActor!!.tell(request)
        return this
    }

    private fun onRequestDeviceList(request: RequestDeviceList) : DeviceManager {
        val groupActor = groupIdToActor[request.groupId]
        if (null == groupActor) {
            request.replyTo.tell(ReplyDeviceList(request.requestId, Collections.emptySet()))
            return this
        }
        groupActor.tell(request)
        return this
    }

    private fun onGroupTerminated(request: DeviceGroupTerminated) : DeviceManager{
        context.log.info("Device group actor for ${request.groupId} has been terminated")
        groupIdToActor.remove(request.groupId)
        return this
    }

    private fun onPostStop(): DeviceManager {
        context.log.info("DeviceManager stopped")
        return this
    }


    private val groupIdToActor: HashMap<String, ActorRef<DeviceGroupCommand>> = HashMap()



    companion object {
        fun create(): Behavior<DeviceManagerCommand> {
            return Behaviors.setup {context -> DeviceManager(context)}
        }
    }

    init {
        context.log.info("DeviceManager started")
    }
}