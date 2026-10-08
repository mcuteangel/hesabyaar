use super::money_detector::contains_money;
use super::text_preprocessor::{normalize_money_text, to_ascii_digits};

/// A non-negative decimal number parsed from the input, kept exact.
///
/// The value is `mantissa / 10^scale` (e.g. `2.5` is `{ mantissa: 25, scale: 1 }`).
/// Money is never routed through `f64`: every multiplication and rounding step
/// below is integer arithmetic on `i128`, so large Rial/Toman amounts keep every
/// digit.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Decimal {
    mantissa: i128,
    scale: u32,
}

impl Decimal {
    const ZERO: Self = Self {
        mantissa: 0,
        scale: 0,
    };

    /// Parses `digits[.digits]`. Returns `None` when the number does not fit.
    fn parse(text: &str) -> Option<Self> {
        let (int_part, frac_part) = match text.split_once('.') {
            Some((i, f)) => (i, f),
            None => (text, ""),
        };
        let frac_part = frac_part.trim_end_matches('0');
        let mut mantissa: i128 = 0;
        for c in int_part.chars().chain(frac_part.chars()) {
            let digit = i128::from(c.to_digit(10)?);
            mantissa = mantissa.checked_mul(10)?.checked_add(digit)?;
        }
        let scale = u32::try_from(frac_part.len()).ok()?;
        Some(Self { mantissa, scale })
    }

    fn is_positive(self) -> bool {
        self.mantissa > 0
    }

    fn normalize(&mut self) {
        if self.mantissa == 0 {
            self.scale = 0;
            return;
        }
        while self.mantissa % 10 == 0 && self.scale > 0 {
            self.mantissa /= 10;
            self.scale -= 1;
        }
    }

    /// `self * 10^exp` kept exact as a decimal without intermediate overflow.
    fn times_unit(mut self, exp: u32) -> Option<Self> {
        self.normalize();
        if exp >= self.scale {
            let diff = exp - self.scale;
            let factor = 10_i128.checked_pow(diff)?;
            let mantissa = self.mantissa.checked_mul(factor)?;
            Some(Self { mantissa, scale: 0 })
        } else {
            Some(Self {
                mantissa: self.mantissa,
                scale: self.scale - exp,
            })
        }
    }

    /// Add two non-negative decimals exact.
    fn checked_add(self, other: Self) -> Option<Self> {
        let mut a = self;
        let mut b = other;
        a.normalize();
        b.normalize();

        if a.mantissa == 0 {
            return Some(b);
        }
        if b.mantissa == 0 {
            return Some(a);
        }

        if a.scale < b.scale {
            std::mem::swap(&mut a, &mut b);
        }
        // Now a.scale >= b.scale: b has the smaller scale (fewer fractional digits).
        let scale_diff = a.scale - b.scale;

        // Find the maximum scale increase for b that fits in i128.
        let mut target_diff = scale_diff.min(38);
        let mut b_scaled = None;
        while target_diff > 0 {
            if let Some(f) = 10_i128.checked_pow(target_diff) {
                if let Some(bs) = b.mantissa.checked_mul(f) {
                    b_scaled = Some(bs);
                    break;
                }
            }
            target_diff -= 1;
        }
        let b_mantissa = b_scaled.unwrap_or(b.mantissa);

        // Scale a down to match (b.scale + target_diff), rounding half-up.
        let drop_scale = scale_diff - target_diff;
        let a_mantissa = if drop_scale >= 39 {
            0
        } else if drop_scale > 0 {
            let a_divisor = 10_i128.checked_pow(drop_scale)?;
            let a_quotient = a.mantissa / a_divisor;
            let a_remainder = a.mantissa % a_divisor;
            if a_remainder >= a_divisor / 2 {
                a_quotient.checked_add(1)?
            } else {
                a_quotient
            }
        } else {
            a.mantissa
        };

        let sum = a_mantissa.checked_add(b_mantissa)?;
        let mut res = Self {
            mantissa: sum,
            scale: b.scale + target_diff,
        };
        res.normalize();
        Some(res)
    }

    /// Round half-up to a whole unit using quotient and remainder to prevent overflow.
    /// Scale >= 39 means the fractional part dominates (< 10^-38) and rounds half-up to 0.
    fn round_half_up(self) -> Option<i128> {
        if self.scale == 0 {
            Some(self.mantissa)
        } else if self.scale >= 39 {
            Some(0)
        } else {
            let divisor = 10_i128.checked_pow(self.scale)?;
            let quotient = self.mantissa / divisor;
            let remainder = self.mantissa % divisor;
            let half = divisor / 2;
            if remainder >= half {
                quotient.checked_add(1)
            } else {
                Some(quotient)
            }
        }
    }

    /// Whole part only (fraction truncated), matching the old `f64 as i64`.
    /// Scale >= 39 means the fractional part dominates (< 10^-38) and the
    /// integer part is 0 since mantissa fits in i128 (< 10^39).
    fn truncated(self) -> Option<i128> {
        if self.scale == 0 {
            Some(self.mantissa)
        } else if self.scale >= 39 {
            Some(0)
        } else {
            let divisor = 10_i128.checked_pow(self.scale)?;
            Some(self.mantissa / divisor)
        }
    }
}

const MAX_TOMAN: i64 = i64::MAX / 10;

/// Narrows an exact total to `i64`. An amount that does not fit or would
/// overflow subsequent Toman->Rial conversion (`* 10`) is not usable,
/// so it maps to `0` ("no amount") instead of wrapping or panicking.
fn to_amount(total: Option<i128>) -> i64 {
    total
        .and_then(|t| i64::try_from(t).ok())
        .filter(|&t| (0..=MAX_TOMAN).contains(&t))
        .unwrap_or(0)
}

#[derive(Debug, Clone, PartialEq)]
pub enum Token {
    Number(Decimal),
    Unit(UnitType),
    Invalid,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum UnitType {
    Billion,
    Million,
    Thousand,
    Tuman,
}

impl UnitType {
    pub fn exp(self) -> u32 {
        match self {
            Self::Billion => 9,
            Self::Million => 6,
            Self::Thousand => 3,
            Self::Tuman => 0,
        }
    }

    pub fn multiplier(self) -> i64 {
        match self {
            Self::Billion => 1_000_000_000,
            Self::Million => 1_000_000,
            Self::Thousand => 1_000,
            Self::Tuman => 1,
        }
    }

    pub fn lower(self) -> Option<UnitType> {
        match self {
            Self::Billion => Some(Self::Million),
            Self::Million => Some(Self::Thousand),
            Self::Thousand => Some(Self::Tuman),
            Self::Tuman => None,
        }
    }
}

const UNIT_WORDS: &[(&str, UnitType)] = &[
    (
        "\u{0645}\u{06CC}\u{0644}\u{06CC}\u{0648}\u{0646} \u{062A}\u{0648}\u{0645}\u{0627}\u{0646}",
        UnitType::Million,
    ), // میلیون تومان
    (
        "\u{0645}\u{06CC}\u{0644}\u{06CC}\u{0627}\u{0631}\u{062F}",
        UnitType::Billion,
    ), // میلیارد
    (
        "\u{0645}\u{06CC}\u{0644}\u{06CC}\u{0648}\u{0646}",
        UnitType::Million,
    ), // میلیون
    (
        "\u{0645}\u{0644}\u{06CC}\u{0648}\u{0646}",
        UnitType::Million,
    ), // ملیون
    ("\u{0647}\u{0632}\u{0627}\u{0631}", UnitType::Thousand), // هزار
    ("\u{062A}\u{0648}\u{0645}\u{0627}\u{0646}", UnitType::Tuman), // تومان
    ("\u{062A}\u{0648}\u{0645}\u{0646}", UnitType::Tuman),    // تومن
];

fn tokenize(text: &str) -> Vec<Token> {
    let mut tokens = Vec::new();
    let chars: Vec<char> = text.chars().collect();
    let mut i = 0;

    while i < chars.len() {
        if chars[i].is_whitespace() {
            i += 1;
            continue;
        }

        if chars[i].is_ascii_digit() {
            let start = i;
            while i < chars.len() && chars[i].is_ascii_digit() {
                i += 1;
            }
            // Consume optional decimal fraction
            if i < chars.len() && chars[i] == '.' {
                let frac_start = i + 1;
                i += 1;
                while i < chars.len() && chars[i].is_ascii_digit() {
                    i += 1;
                }
                if i == frac_start {
                    // No digits after decimal, rewind past the dot
                    i = frac_start - 1;
                }
            }
            let num_str: String = chars[start..i].iter().collect();
            match Decimal::parse(&num_str) {
                Some(num) => tokens.push(Token::Number(num)),
                None => tokens.push(Token::Invalid),
            }
            continue;
        }

        // Try to match unit words
        let remaining: String = chars[i..].iter().collect();
        let mut matched = false;
        for (word, unit_type) in UNIT_WORDS {
            if remaining.starts_with(word) {
                tokens.push(Token::Unit(*unit_type));
                i += word.chars().count();
                matched = true;
                break;
            }
        }
        if !matched {
            i += 1;
        }
    }

    tokens
}

fn interpret_with_units(tokens: &[Token]) -> i64 {
    if tokens.iter().any(|t| matches!(t, Token::Invalid)) {
        return 0;
    }
    let mut total = Decimal::ZERO;
    let mut current_num: Option<Decimal> = None;
    let mut last_unit: Option<UnitType> = None;

    for token in tokens {
        match token {
            Token::Number(n) => current_num = Some(*n),
            Token::Unit(u) => {
                if let Some(n) = current_num.filter(|n| n.is_positive()) {
                    let term = match n.times_unit(u.exp()) {
                        Some(t) => t,
                        None => return 0,
                    };
                    total = match total.checked_add(term) {
                        Some(t) => t,
                        None => return 0,
                    };
                }
                last_unit = Some(*u);
                current_num = None;
            }
            Token::Invalid => return 0,
        }
    }

    if let Some(n) = current_num.filter(|n| n.is_positive()) {
        let exp = last_unit.and_then(|u| u.lower()).map_or(0, |u| u.exp());
        let term = match n.times_unit(exp) {
            Some(t) => t,
            None => return 0,
        };
        total = match total.checked_add(term) {
            Some(t) => t,
            None => return 0,
        };
    }

    to_amount(total.round_half_up())
}

fn interpret_shorthand(tokens: &[Token]) -> i64 {
    if tokens.iter().any(|t| matches!(t, Token::Invalid)) {
        return 0;
    }
    let numbers: Vec<Decimal> = tokens
        .iter()
        .filter_map(|t| {
            if let Token::Number(n) = t {
                Some(*n)
            } else {
                None
            }
        })
        .collect();

    if numbers.is_empty() {
        return 0;
    }
    if numbers.len() == 1 {
        return to_amount(numbers[0].truncated());
    }

    let unit_steps_exp = [
        UnitType::Billion.exp(),
        UnitType::Million.exp(),
        UnitType::Thousand.exp(),
    ];
    // `numbers.len() <= 3` is the supported shorthand shape: the first number
    // maps to the largest remaining unit. For >3 numbers we clamp the unit
    // index so we never index out of bounds (and never underflow `3 - len`).
    let start_idx = (3_usize).saturating_sub(numbers.len());

    let mut total = Decimal::ZERO;
    for (i, num) in numbers.iter().enumerate() {
        let idx = (start_idx + i).min(unit_steps_exp.len() - 1);
        let term = match num.times_unit(unit_steps_exp[idx]) {
            Some(t) => t,
            None => return 0,
        };
        total = match total.checked_add(term) {
            Some(t) => t,
            None => return 0,
        };
    }
    to_amount(total.round_half_up())
}

fn interpret_bare_last(tokens: &[Token]) -> i64 {
    tokens
        .iter()
        .rev()
        .find_map(|t| {
            if let Token::Number(n) = t {
                Some(to_amount(n.truncated()))
            } else {
                None
            }
        })
        .unwrap_or(0)
}

/// Parse a Persian amount sentence and return the amount in Toman.
#[uniffi::export]
pub fn parse_amount(sentence: &str, shorthand_mode: bool) -> i64 {
    if !contains_money(sentence) {
        return 0;
    }

    let normalized = normalize_money_text(sentence);
    let cleaned = normalized.replace(" \u{0648} ", " ");
    let ascii = to_ascii_digits(&cleaned);
    let tokens = tokenize(&ascii);

    if tokens.is_empty() || tokens.iter().any(|t| matches!(t, Token::Invalid)) {
        return 0;
    }

    let has_units = tokens.iter().any(|t| matches!(t, Token::Unit(_)));
    if has_units {
        interpret_with_units(&tokens)
    } else if shorthand_mode {
        interpret_shorthand(&tokens)
    } else {
        interpret_bare_last(&tokens)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_with_units() {
        assert_eq!(parse_amount("5 میلیون تومان", true), 5_000_000);
        assert_eq!(parse_amount("450 هزار تومن", true), 450_000);
        assert_eq!(parse_amount("2 میلیارد", true), 2_000_000_000);
    }

    #[test]
    fn test_parse_shorthand() {
        // parse_amount requires a money keyword to pass contains_money() gate
        assert_eq!(parse_amount("۵۰۰ تومن", true), 500);
        assert_eq!(parse_amount("۵۰۰۰۰۰ تومان", true), 500_000);
        // Bare numbers without money keywords return 0
        assert_eq!(parse_amount("۵۰۰", true), 0);
    }

    #[test]
    fn test_large_amounts_keep_every_digit() {
        // 2^53 + 1: an f64 intermediate would round this to ...992.
        assert_eq!(
            parse_amount("9007199254740993 تومان", true),
            9_007_199_254_740_993
        );
        assert_eq!(parse_amount("9999999999 تومان", true), 9_999_999_999);
        assert_eq!(parse_amount("9999 میلیارد تومان", true), 9_999_000_000_000);
    }

    #[test]
    fn test_mixed_units() {
        assert_eq!(
            parse_amount("2 میلیارد و 300 میلیون و 50 هزار تومان", true),
            2_300_050_000
        );
        // A trailing number with no unit takes the next unit down.
        assert_eq!(parse_amount("3 میلیون و 200", true), 3_200_000);
        // An explicit unit on the trailing number overrides the fallback.
        assert_eq!(parse_amount("3 میلیون و 200 تومن", true), 3_000_200);
    }

    #[test]
    fn test_fractional_units_are_exact() {
        assert_eq!(parse_amount("2.5 میلیون تومان", true), 2_500_000);
        assert_eq!(parse_amount("۱.۲ میلیارد تومان", true), 1_200_000_000);
        // Rounded half-up to a whole Toman.
        assert_eq!(parse_amount("1.2345 هزار تومان", true), 1_235);
        // Fractional components accumulate exact without premature per-term rounding:
        // 1234.5 + 1234.5 = 2469.0 -> 2469 (not 1235 + 1235 = 2470).
        assert_eq!(parse_amount("1.2345 هزار و 1.2345 هزار تومان", true), 2_469);
        // Zeros in fractional part reduce scale cleanly without overflow.
        assert_eq!(
            parse_amount(
                "1.00000000000000000000000000000000000000 میلیارد تومان",
                true
            ),
            1_000_000_000
        );
        // Large scale difference does not drop the finer-scale addend:
        assert_eq!(
            parse_amount(
                "2 میلیون و 1.00000000000000000000000000000000000001 میلیون تومان",
                true
            ),
            3_000_000
        );
    }

    #[test]
    fn test_overflow_is_not_an_amount() {
        assert_eq!(parse_amount("99999999999999 میلیارد تومان", true), 0);
        assert_eq!(
            parse_amount("999999999999999999999999999999999999999999 تومان", true),
            0
        );
        // Unrepresentable number in a multi-token sentence rejects the whole amount.
        assert_eq!(
            parse_amount(
                "100 میلیون و 999999999999999999999999999999999999999999 هزار تومان",
                true
            ),
            0
        );
        // Toman values above i64::MAX / 10 would overflow Rial conversion (* 10).
        assert_eq!(parse_amount("922337203685477581 تومان", true), 0);
        assert_eq!(
            parse_amount("922337203685477580 تومان", true),
            922_337_203_685_477_580
        );
    }

    #[test]
    fn test_decimal_parse() {
        assert_eq!(
            Decimal::parse("2.5"),
            Some(Decimal {
                mantissa: 25,
                scale: 1
            })
        );
        assert_eq!(Decimal::parse("12").map(|d| d.truncated()), Some(Some(12)));
        assert_eq!(
            Decimal::parse("12.9").map(|d| d.truncated()),
            Some(Some(12))
        );
    }

    #[test]
    fn test_parse_no_money() {
        assert_eq!(parse_amount("سلام دنیا", true), 0);
    }
}
