package org.icpclive.sniper

import java.io.*
import java.util.*

object SniperMover {
    private const val DEFAULT_SPEED = "0.52"
    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        Util.init()
        Locale.setDefault(Locale.US)
        val `in` = Scanner(System.`in`)
        while (true) {
            println("Select sniper (1-" + Util.snipers.size + ")")
            val sniper = `in`.nextInt()
            val scanner = Scanner(File("coordinates-$sniper.txt"))
            val n = scanner.nextInt()
            println("Select team (1-$n)")
            val needId = `in`.nextInt()
            var point: LocatorPoint? = null
            for (i in 1..n) {
                val id = scanner.nextInt()
                point = LocatorPoint(
                    scanner.nextDouble(),
                    scanner.nextDouble(),
                    scanner.nextDouble()
                )
                if (id == needId) {
                    break
                }
            }
            if (point!!.y > 0) {
                point.x = -point.x
                point.y = -point.y
                point.z = -point.z
            }
            var tilt = Math.atan2(point.y, Math.hypot(point.x, point.z))
            var pan = Math.atan2(-point.x, -point.z)
            pan *= 180 / Math.PI
            tilt *= 180 / Math.PI
            val d = Math.hypot(point.x, Math.hypot(point.y, point.z))
            val mag = 0.5 * d
            val maxmag = 35.0
            val zoom = (mag * 9999 - 1) / (maxmag - 1)
            move(sniper, pan, tilt, zoom.toInt())
        }
    }

    @Throws(Exception::class)
    private fun move(sniper: Int, pan: Double, tilt: Double, zoom: Int) {
        val hostName = Util.snipers[sniper - 1]!!.hostName
        Util.sendGet(
            hostName + "/axis-cgi/com/ptz.cgi?camera=1" +
                    "&tilt=" + tilt +
                    "&pan=" + pan +
                    "&zoom=" + zoom +
                    "&speed=100" +
                    "&timestamp=" + Util.getUTCTime()
        )
        Util.sendGet(
            hostName + "/axis-cgi/com/ptz.cgi?camera=1" +
                    "&speed=" + DEFAULT_SPEED +
                    "&timestamp=" + Util.getUTCTime()
        )
    }
}