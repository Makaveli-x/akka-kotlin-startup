package com.yewenhai.iot

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.*
import java.time.Duration

/**
 * 使用一个 Actor 来构建查询，之前的例子中 Actor 都是一个 Domain Object。
 * 这个 Query Actor 是一个 Task 或者 Process。
 *
 * Reason:
 * It is probably worth restating what we said at the beginning of the chapter.
 * By keeping the temporary state that is only relevant to the query itself in a separate actor we keep the group actor implementation very simple.
 * It delegates everything to child actors and therefore does not have to keep state that is not relevant to its core business.
 * Also, multiple queries can now run parallel to each other, in fact, as many as needed. In our case querying an individual device actor is a fast operation,
 * but if this were not the case, for example, because the remote sensors need to be contacted over the network, this design would significantly improve throughput.
 *
 *
 */
class DeviceGroupQuery(
    deviceIdToActor: Map<String, ActorRef<DeviceCommand>>,
    val requestId: Long,
    private val requester: ActorRef<RespondAllTemperature>,
    timeout: Duration,
    timerScheduler: TimerScheduler<DeviceGroupQueryCommand>,
    context: ActorContext<DeviceGroupQueryCommand>
) : AbstractBehavior<DeviceGroupQueryCommand>(context) {

    // 还在等待响应的 DeviceId
    private var stillWaiting: MutableSet<String>

    // 已经响应温度的 DeviceId 和 温度
    private val repliesSoFar: MutableMap<String, TemperatureReading> = HashMap()


    override fun createReceive(): Receive<DeviceGroupQueryCommand> {
        return newReceiveBuilder()
            .onMessage(WrappedRespondTemperature::class.java) { wrappedResponse -> onRespondTemperature(wrappedResponse) }
            .onMessage(DeviceQueryTerminated::class.java) { terminated -> onDeviceTerminated(terminated) }
            .onMessage(CollectionTimeOut::class.java) { onCollectionTimeout() }
            .build()
    }

    /**
     * 响应查询超时
     */
    private fun onCollectionTimeout(): Behavior<DeviceGroupQueryCommand> {
        stillWaiting.forEach { repliesSoFar[it] = DeviceTimedOut.INSTANCE }
        stillWaiting.clear()
        return respondWhenAllCollected()
    }

    /**
     * 响应 Device 终止
     */
    private fun onDeviceTerminated(terminated: DeviceQueryTerminated): Behavior<DeviceGroupQueryCommand> {
        if (stillWaiting.contains(terminated.deviceId)) {
            repliesSoFar[terminated.deviceId] = DeviceNotAvailable.INSTANCE
            stillWaiting.remove(terminated.deviceId)
        }
        return respondWhenAllCollected()
    }

    /**
     * 响应温度结果
     */
    private fun onRespondTemperature(wrappedResponse: WrappedRespondTemperature): Behavior<DeviceGroupQueryCommand> {
        val temperature = wrappedResponse.response.temperature
        if (null == temperature) {
            repliesSoFar[wrappedResponse.response.deviceId] = TemperatureNotAvailable.INSTANCE
        } else {
            repliesSoFar[wrappedResponse.response.deviceId] = Temperature(temperature)
        }
        stillWaiting.remove(wrappedResponse.response.deviceId)
        return respondWhenAllCollected()
    }


    init {
        // 延迟指定 timeout 后，发送 CollectionTimeOut 消息。
        timerScheduler.startSingleTimer(CollectionTimeOut.INSTANCE, timeout)
        // DeviceGroupQuery 只能响应 DeviceGroupQueryCommand，所以需要将 Device 发送出的 RespondTemperature 消息转化为 WrappedRespondTemperature 它实现了 DeviceGroupQueryCommand 接口
        val respondTemperatureAdapter =
            context.messageAdapter(RespondTemperature::class.java) { respond -> WrappedRespondTemperature(respond) }
        deviceIdToActor.forEach { (deviceId, device) ->
            run {
                // 监视 Device 的终止信号
                context.watchWith(device, DeviceQueryTerminated(deviceId))
                // 发送温度查询请求
                device.tell(ReadTemperature(requestId, respondTemperatureAdapter))
            }
        }
        // 等待响应的 DeviceId
        stillWaiting = deviceIdToActor.keys.toHashSet()
    }


    /**
     * 当 stillWaitSet 为 0 时，响应结果并终止 Query Actor
     */
    private fun respondWhenAllCollected(): Behavior<DeviceGroupQueryCommand> {
        if (stillWaiting.size == 0) {
            requester.tell(RespondAllTemperature(requestId, repliesSoFar))
            return Behaviors.stopped()
        }
        return this
    }

    companion object {

        /**
         * 创建一个自带延时功能的 Query
         */
        fun create(
            deviceIdToActor: Map<String, ActorRef<DeviceCommand>>,
            requestId: Long,
            requester: ActorRef<RespondAllTemperature>,
            timeout: Duration
        ): Behavior<DeviceGroupQueryCommand> {
            return Behaviors.setup { context ->
                Behaviors.withTimers { scheduler ->
                    DeviceGroupQuery(
                        deviceIdToActor,
                        requestId,
                        requester,
                        timeout,
                        scheduler,
                        context
                    )
                }
            }
        }

    }
}

enum class CollectionTimeOut : DeviceGroupQueryCommand {
    INSTANCE
}