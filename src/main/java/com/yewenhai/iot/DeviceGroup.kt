package com.yewenhai.iot

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import java.time.Duration

class DeviceGroup(context: ActorContext<DeviceGroupCommand>, val groupId: String) : AbstractBehavior<DeviceGroupCommand>(context) {
    override fun createReceive(): Receive<DeviceGroupCommand> {
        return newReceiveBuilder()
            .onMessage(RequestTrackDevice::class.java) {request -> onTrackDevice(request)}
            .onMessage(DeviceTerminated::class.java) {request -> onTerminated(request)}
            .onMessage(RequestDeviceList::class.java, {request -> request.groupId == groupId}) {request -> onDeviceList(request)}
            .onMessage(RequestAllTemperature::class.java) {request -> onAllTemperatures(request)}
            .onSignal(PostStop::class.java) {onPostStop()}
            .build()
    }

    /**
     * 查询 group 下所有 device 温度时，创建一个查询 process QueryActor 来处理
     */
    private fun onAllTemperatures(request: RequestAllTemperature) : DeviceGroup {

        context.spawnAnonymous(DeviceGroupQuery.create(deviceIdToActor, request.requestId, request.replyTo, Duration.ofSeconds(3)))

        return this
    }

    private fun onDeviceList(request: RequestDeviceList) : DeviceGroup {
        request.replyTo.tell(ReplyDeviceList(request.requestId, deviceIdToActor.keys))
        return this
    }

    private fun onTerminated(request: DeviceTerminated) : DeviceGroup {
        context.log.info("Device actor for {} has been terminated", request.deviceId)
        deviceIdToActor.remove(request.deviceId)
        return this
    }

    private fun onTrackDevice(request: RequestTrackDevice): DeviceGroup {
        if (groupId != request.groupId) {
            context.log.info("Ignoring TrackDevice request for ${request.groupId}. This actor is responsible for $groupId.")
            return this
        }
        if (deviceIdToActor.containsKey(request.deviceId)) {
            request.replyTo.tell(DeviceRegistered(deviceIdToActor[request.deviceId]!!))
            return this
        }
        val deviceActor = context.spawn(Device.create(request.groupId, request.deviceId), "device-${request.deviceId}")
        // Death Watch facility
        context.watchWith(deviceActor, DeviceTerminated(deviceActor, groupId, request.deviceId))

        deviceIdToActor[request.deviceId] = deviceActor
        context.log.info("Creating device actor for ${request.deviceId}")
        request.replyTo.tell(DeviceRegistered(deviceActor))

        return this
    }

    private val deviceIdToActor: HashMap<String, ActorRef<DeviceCommand>> = HashMap()

    init {
        context.log.info("DeviceGroup $groupId started")
    }

    private fun onPostStop() : DeviceGroup {
        context.log.info("DeviceGroup $groupId stopped")
        return this
    }

    companion object {
        fun create(groupId: String) : Behavior<DeviceGroupCommand> {
            return Behaviors.setup {
                context -> DeviceGroup(context, groupId)
            }
        }
    }
}