package com.yewenhai.iot

class Temperature(val value: Double) : TemperatureReading {

    override fun equals(other: Any?): Boolean {
        if (null == other) {
            return false
        }
        if (this === other) {
            return true
        }
        if (other !is Temperature) {
            return false
        }

        return other.value == value
    }

    override fun hashCode(): Int {
        val temp = java.lang.Double.doubleToLongBits(value)
        return temp.toInt()
    }

    override fun toString(): String {
        return "Temperature {value = $value}"
    }
}

enum class TemperatureNotAvailable : TemperatureReading {
    INSTANCE
}

enum class DeviceNotAvailable : TemperatureReading {
    INSTANCE
}

enum class DeviceTimedOut : TemperatureReading {
    INSTANCE
}