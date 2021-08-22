package com.yewenhai.iot

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

class Device(context: ActorContext<DeviceCommand>, val groupId: String, val deviceId: String) : AbstractBehavior<DeviceCommand>(context) {

    private var lastTemperatureReading : Double? = null

    override fun createReceive(): Receive<DeviceCommand> {
        return newReceiveBuilder()
            .onMessage(ReadTemperature::class.java) { request -> onReadTemperature(request) }
            .onMessage(RecordTemperature::class.java) {request -> onRecordTemperature(request)}
            .onMessage(Passivate::class.java) { Behaviors.stopped()}
            .onSignal(PostStop::class.java) {onPostStop()}
            .build()
    }

    private fun onRecordTemperature(request: RecordTemperature) : Behavior<DeviceCommand> {
        context.log.info("Recorded temperature reading ${request.value} with ${request.requestId}")
        lastTemperatureReading = request.value
        request.replyTo.tell(TemperatureRecorded(request.requestId))
        return this
    }

    private fun onReadTemperature(request: ReadTemperature): Behavior<DeviceCommand> {
        request.replyTo.tell(RespondTemperature(request.requestId, deviceId, lastTemperatureReading))
        return this
    }

    private fun onPostStop(): Device {
        context.log.info("Device actor $groupId-$deviceId stopped")
        return this
    }

    companion object {
        fun create(groupId: String, deviceId: String): Behavior<DeviceCommand> {
            return Behaviors.setup {context -> Device(context, groupId, deviceId)}
        }

        enum class Passivate : DeviceCommand {
            INSTANCE
        }
    }


    init {
        context.log.info("Device actor $groupId-$deviceId started")
    }
}