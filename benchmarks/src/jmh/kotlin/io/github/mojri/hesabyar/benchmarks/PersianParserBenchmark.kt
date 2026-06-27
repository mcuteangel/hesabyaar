package io.github.mojri.hesabyar.benchmarks

import io.github.mojri.hesabyar.api.MoneyDetector
import io.github.mojri.hesabyar.api.PersianAmountParser
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

/**
 * Benchmarks for the Persian natural-language money parsing primitives that
 * power the app's offline transaction entry. These run on every keystroke in
 * the smart-assistant flow, so they sit on a hot path.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
open class PersianParserBenchmark {

    @Benchmark
    fun parseExplicitUnits(): Long =
        PersianAmountParser.parseAmount("1 میلیارد و 140 میلیون و 300 هزار")

    @Benchmark
    fun parseShorthandWithContext(): Long =
        PersianAmountParser.parseAmount("به علی 1 و 140 و 300 قرض دادم")

    @Benchmark
    fun parsePersianNumeralsWithSeparators(): Long =
        PersianAmountParser.parseAmount("لباس خریدم ۵٬۴۰۰٬۰۰۰ تومان")

    @Benchmark
    fun parseNonMoneySentence(): Long =
        PersianAmountParser.parseAmount("کد تایید 567890")

    @Benchmark
    fun detectMoneyPositive(): Boolean =
        MoneyDetector.containsMoney("لباس خریدم 5 میلیون و 400 هزار")

    @Benchmark
    fun detectMoneyNegative(): Boolean =
        MoneyDetector.containsMoney("ساعت 5 و 40 دقیقه")
}
