package com.omb9.glucosehero.util

import kotlin.math.abs
import kotlin.math.floor

/**
 * Largest-Triangle-Three-Buckets downsampling (Sveinn Steinarsson).
 *
 * Each output point except the first and last is chosen as the sample in its
 * bucket that forms the largest triangle with the previously selected point
 * and the average of the next bucket. That prefers peaks and valleys over a
 * naive stride, which can skip a spike that lands between kept indices.
 *
 * Time is O(n) in the input length. The output length is min(n, threshold)
 * (or n when [threshold] is not a useful cap).
 */
object Lttb {

    data class Point(val x: Double, val y: Double)

    fun downsample(points: List<Point>, threshold: Int): List<Point> =
        downsample(points, threshold, { it.x }, { it.y })

    /**
     * Returns a subset of [points] with at most [threshold] items, preserving
     * original objects (and therefore timestamps / identities).
     *
     * - Empty input or [threshold] <= 0: [points] unchanged.
     * - n <= [threshold]: [points] unchanged (including n == threshold).
     * - [threshold] == 1: first point only.
     * - [threshold] == 2: first and last.
     */
    fun <T> downsample(
        points: List<T>,
        threshold: Int,
        xOf: (T) -> Double,
        yOf: (T) -> Double,
    ): List<T> {
        val n = points.size
        if (n == 0 || threshold <= 0 || threshold >= n) return points
        if (threshold == 1) return listOf(points.first())
        if (threshold == 2) return listOf(points.first(), points.last())

        val sampled = ArrayList<T>(threshold)
        sampled.add(points.first())

        val every = (n - 2).toDouble() / (threshold - 2)
        var previousIndex = 0

        for (i in 0 until threshold - 2) {
            val avgRangeStart = floor((i + 1) * every).toInt() + 1
            val avgRangeEnd = (floor((i + 2) * every).toInt() + 1).coerceAtMost(n)
            val avgRangeLength = avgRangeEnd - avgRangeStart

            var avgX: Double
            var avgY: Double
            if (avgRangeLength <= 0) {
                val fallback = points[n - 1]
                avgX = xOf(fallback)
                avgY = yOf(fallback)
            } else {
                avgX = 0.0
                avgY = 0.0
                var k = avgRangeStart
                while (k < avgRangeEnd) {
                    avgX += xOf(points[k])
                    avgY += yOf(points[k])
                    k++
                }
                avgX /= avgRangeLength
                avgY /= avgRangeLength
            }

            val rangeOffs = floor(i * every).toInt() + 1
            val rangeTo = (floor((i + 1) * every).toInt() + 1).coerceAtMost(n - 1)

            val pointAx = xOf(points[previousIndex])
            val pointAy = yOf(points[previousIndex])

            var maxArea = -1.0
            var nextIndex = rangeOffs.coerceIn(1, n - 2)
            var j = rangeOffs
            while (j < rangeTo) {
                val px = xOf(points[j])
                val py = yOf(points[j])
                val area = abs(
                    (pointAx - avgX) * (py - pointAy) - (pointAx - px) * (avgY - pointAy),
                )
                if (area > maxArea) {
                    maxArea = area
                    nextIndex = j
                }
                j++
            }

            sampled.add(points[nextIndex])
            previousIndex = nextIndex
        }

        sampled.add(points.last())
        return sampled
    }
}
