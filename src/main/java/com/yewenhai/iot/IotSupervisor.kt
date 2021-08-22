package com.yewenhai.iot

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

class IotSupervisor(context: ActorContext<Unit>) : AbstractBehavior<Unit>(context) {


    init {
        context.log.info("Iot Application started.")
    }

    companion object {
        fun create() : Behavior<Unit> {
            return Behaviors.setup {context -> IotSupervisor(context) }
        }
    }


    override fun createReceive(): Receive<Unit> {
        return newReceiveBuilder().onSignal(PostStop::class.java) { onPostStop() }.build()
    }

    private fun onPostStop() : Behavior<Unit> {
        context.log.info("Iot Application Stopped.")
        return this
    }
}