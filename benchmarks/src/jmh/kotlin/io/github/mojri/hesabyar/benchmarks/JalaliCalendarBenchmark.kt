package io.github.mojri.hesabyar.benchmarks

import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Benchmarks for the Jalali (Persian) calendar conversions. Date conversion
 * runs whenever transactions, reminders and reports are rendered, so the
 * arithmetic-heavy conversion routines are worth tracking.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
open class JalaliCalendarBenchmark {

    @Benchmark
    fun gregorianToJalali(): JalaliCalendarHelper.JalaliDate =
        JalaliCalendarHelper.gregorianToJalali(2024, 6, 27)

    @Benchmark
    fun jalaliToGregorian(): Calendar =
        JalaliCalendarHelper.jalaliToGregorian(1403, 4, 6)
}
