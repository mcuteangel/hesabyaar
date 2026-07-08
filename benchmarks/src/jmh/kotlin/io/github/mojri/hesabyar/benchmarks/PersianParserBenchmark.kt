package io.github.mojri.hesabyar.benchmarks

import io.github.mojri.hesabyar.rust.RustBridge.containsMoney
import io.github.mojri.hesabyar.rust.RustBridge.parsePersianAmount
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

/**
 * Benchmarks for the Rust-backed Persian natural-language parsing primitives.
 * These run on every keystroke in the smart-assistant flow, so they sit on a hot path.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
open class PersianParserBenchmark {

    @Benchmark
    fun parseExplicitUnits(): Long =
        parsePersianAmount("1 میلیارد و 140 میلیون و 300 هزار")

    @Benchmark
    fun parseShorthandWithContext(): Long =
        parsePersianAmount("به علی 1 و 140 و 300 قرض دادم")

    @Benchmark
    fun parsePersianNumeralsWithSeparators(): Long =
        parsePersianAmount("لباس خریدم ۵٬۴۰۰٬۰۰۰ تومان")

    @Benchmark
    fun parseNonMoneySentence(): Long =
        parsePersianAmount("کد تایید 567890")

    @Benchmark
    fun detectMoneyPositive(): Boolean =
        containsMoney("لباس خریدم 5 میلیون و 400 هزار")

    @Benchmark
    fun detectMoneyNegative(): Boolean =
        containsMoney("ساعت 5 و 40 دقیقه")
}
