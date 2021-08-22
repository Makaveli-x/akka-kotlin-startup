package com.yewenhai

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import com.yewenhai.iot.*
import org.junit.ClassRule
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class DeviceTest {

    @Test
    fun testReturnDeviceTimedOutIfDeviceDoesNotAnswerInTime() {
        val requester = testKit.createTestProbe(RespondAllTemperature::class.java)
        val device1 = testKit.createTestProbe(DeviceCommand::class.java)
        val device2 = testKit.createTestProbe(DeviceCommand::class.java)
        val deviceIdToActor = mapOf("device1" to device1.ref, "device2" to device2.ref)
        val deviceQuery = testKit.spawn(DeviceGroupQuery.create(deviceIdToActor, 1, requester.ref, Duration.ofMillis(300)))

        deviceQuery.tell(WrappedRespondTemperature(RespondTemperature(0, "device2", 2.0)))


        device1.expectMessageClass(ReadTemperature::class.java)
        device2.expectMessageClass(ReadTemperature::class.java)

        val response = requester.receiveMessage()
        assertEquals(response.requestId, 1)
        assertEquals(response.temperatures, mapOf("device1" to DeviceTimedOut.INSTANCE, "device2" to Temperature(2.0)))

    }

    @Test
    fun testReturnDeviceNotAvailableIfDeviceStopsBeforeAnswering() {
        val requester = testKit.createTestProbe(RespondAllTemperature::class.java)
        val device1 = testKit.createTestProbe(DeviceCommand::class.java)
        val device2 = testKit.createTestProbe(DeviceCommand::class.java)
        val deviceIdToActor = mapOf("device1" to device1.ref, "device2" to device2.ref)
        val deviceQuery = testKit.spawn(DeviceGroupQuery.create(deviceIdToActor, 1, requester.ref, Duration.ofSeconds(3)))

        device2.expectMessageClass(ReadTemperature::class.java)

        deviceQuery.tell(WrappedRespondTemperature(RespondTemperature(0, "device2", 2.0)))
        device1.stop()

        val response = requester.receiveMessage()
        assertEquals(response.requestId, 1)
        assertEquals(response.temperatures, mapOf("device1" to DeviceNotAvailable.INSTANCE, "device2" to Temperature(2.0)))
    }


    @Test
    fun testReturnTemperatureNotAvailableForDevicesWithNoReadings() {
        val requester = testKit.createTestProbe(RespondAllTemperature::class.java)
        val device1 = testKit.createTestProbe(DeviceCommand::class.java)
        val device2 = testKit.createTestProbe(DeviceCommand::class.java)
        val deviceIdToActor = mapOf("device1" to device1.ref, "device2" to device2.ref)
        val deviceQuery = testKit.spawn(DeviceGroupQuery.create(deviceIdToActor, 1, requester.ref, Duration.ofSeconds(3)))

        device1.expectMessageClass(ReadTemperature::class.java)
        device2.expectMessageClass(ReadTemperature::class.java)

        deviceQuery.tell(WrappedRespondTemperature(RespondTemperature(0, "device1", null)))
        deviceQuery.tell(WrappedRespondTemperature(RespondTemperature(0, "device2", 2.0)))

        val response = requester.receiveMessage()
        assertEquals(response.requestId, 1)
        assertEquals(response.temperatures, mapOf("device1" to TemperatureNotAvailable.INSTANCE, "device2" to Temperature(2.0)))

    }


    @Test
    fun testReturnTemperatureValueForWorkingDevices() {
        val requester = testKit.createTestProbe(RespondAllTemperature::class.java)
        val device1 = testKit.createTestProbe(DeviceCommand::class.java)
        val device2 = testKit.createTestProbe(DeviceCommand::class.java)
        val deviceIdToActor = mapOf("device1" to device1.ref, "device2" to device2.ref)

        val deviceQuery = testKit.spawn(DeviceGroupQuery.create(deviceIdToActor, 1, requester.ref, Duration.ofSeconds(3)))

        device1.expectMessageClass(ReadTemperature::class.java)
        device2.expectMessageClass(ReadTemperature::class.java)
        deviceQuery.tell(WrappedRespondTemperature(RespondTemperature(0, "device1", 1.0)))
        deviceQuery.tell(WrappedRespondTemperature(RespondTemperature(0, "device2", 2.0)))

        val response = requester.receiveMessage()
        assertEquals(response.requestId, 1)
        assertEquals(response.temperatures, mapOf("device1" to Temperature(1.0), "device2" to Temperature(2.0)))
    }


    @Test
    fun testListActiveDevicesOneShutsDown() {
        val deviceRegisteredProbe = testKit.createTestProbe(DeviceRegistered::class.java)
        val deviceGroup = testKit.spawn(DeviceGroup.create("group"))

        deviceGroup.tell(RequestTrackDevice("group", "device1", deviceRegisteredProbe.ref()))
        val deviceRegistered1 = deviceRegisteredProbe.receiveMessage()

        deviceGroup.tell(RequestTrackDevice("group", "device2", deviceRegisteredProbe.ref()))
        val deviceRegistered2 = deviceRegisteredProbe.receiveMessage()

        val toShutDown = deviceRegistered1.device

        val deviceListProbe = testKit.createTestProbe(ReplyDeviceList::class.java)
        deviceGroup.tell(RequestDeviceList(0, "group", deviceListProbe.ref))
        val replyDeviceList = deviceListProbe.receiveMessage()
        assertEquals(0, replyDeviceList.requestId)
        assertEquals(setOf("device1", "device2"), replyDeviceList.ids)

        toShutDown.tell(Device.Companion.Passivate.INSTANCE)
        deviceRegisteredProbe.expectTerminated(toShutDown, deviceRegisteredProbe.remainingOrDefault)

        deviceRegisteredProbe.awaitAssert {
            deviceGroup.tell(RequestDeviceList(1L, "group", deviceListProbe.ref))
            val list = deviceListProbe.receiveMessage()
            assertEquals(1, list.requestId)
            assertEquals(setOf("device2"), list.ids)
        }
    }


    @Test
    fun testReplyToRegistrationRequests() {
        val deviceRegisteredProbe = testKit.createTestProbe(DeviceRegistered::class.java)
        val deviceGroup = testKit.spawn(DeviceGroup.create("group"))

        deviceGroup.tell(RequestTrackDevice("group", "device", deviceRegisteredProbe.ref))
        val deviceRegistered1 = deviceRegisteredProbe.receiveMessage()

        deviceGroup.tell(RequestTrackDevice("group", "device2", deviceRegisteredProbe.ref))
        val deviceRegistered2 = deviceRegisteredProbe.receiveMessage()

        assertNotEquals(deviceRegistered1, deviceRegistered2)
    }

    @Test
    fun testReplyWithLatestTemperatureReading() {
        val recordProbe = testKit.createTestProbe(TemperatureRecorded::class.java)
        val readProbe = testKit.createTestProbe(RespondTemperature::class.java)

        val device = testKit.spawn(Device.create("group", "device"))
        device.tell(RecordTemperature(1, 24.0, recordProbe.ref))
        assertEquals(1, recordProbe.receiveMessage().requestId)

        device.tell(ReadTemperature(2, readProbe.ref))
        assertEquals(24.0, readProbe.receiveMessage().temperature)

    }


    @Test
    fun testReplyWithEmptyReadingIfNoTemperatureIsKnown() {
        val probe = testKit.createTestProbe(RespondTemperature::class.java)
        val device = testKit.spawn(Device.create("group", "device"))
        device.tell(ReadTemperature(42, probe.ref))
        val responseTemperature = probe.receiveMessage()
        assertEquals(42, responseTemperature.requestId)
        assertEquals(null, responseTemperature.temperature)
    }


    companion object {
        @ClassRule
        @JvmField
        val testKit: TestKitJunitResource = TestKitJunitResource()
    }
}