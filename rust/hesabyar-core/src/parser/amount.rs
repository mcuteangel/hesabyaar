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

    /// `self * multiplier`, rounded half-up to a whole unit.
    fn times_rounded(self, multiplier: i64) -> Option<i128> {
        if multiplier <= 0 {
            return Some(0);
        }

        let mut m = multiplier;
        let mut m_scale: u32 = 0;
        while m % 10 == 0 {
            m /= 10;
            m_scale += 1;
        }

        let common = self.scale.min(m_scale);
        let rem_scale = self.scale - common;
        let rem_m_scale = m_scale - common;

        let product = self.mantissa.checked_mul(i128::from(m))?;

        if rem_scale == 0 {
            let factor = 10_i128.checked_pow(rem_m_scale)?;
            product.checked_mul(factor)
        } else if rem_scale >= 39 {
            Some(0)
        } else {
            let divisor = 10_i128.checked_pow(rem_scale)?;
            let quotient = product / divisor;
            let remainder = product % divisor;
            let half = divisor / 2;
            if remainder >= half {
                quotient.checked_add(1)
            } else {
                Some(quotient)
            }
        }
    }

    /// Whole part only (fraction truncated), matching the old `f64 as i64`.
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
    let mut total: Option<i128> = Some(0);
    let mut current_num: Option<Decimal> = None;
    let mut last_unit: Option<UnitType> = None;

    for token in tokens {
        match token {
            Token::Number(n) => current_num = Some(*n),
            Token::Unit(u) => {
                if let Some(n) = current_num.filter(|n| n.is_positive()) {
                    total = total
                        .zip(n.times_rounded(u.multiplier()))
                        .and_then(|(t, add)| t.checked_add(add));
                }
                last_unit = Some(*u);
                current_num = None;
            }
            Token::Invalid => {}
        }
    }

    if let Some(n) = current_num.filter(|n| n.is_positive()) {
        let multiplier = last_unit
            .and_then(|u| u.lower())
            .map(|u| u.multiplier())
            .unwrap_or(1);
        total = total
            .zip(n.times_rounded(multiplier))
            .and_then(|(t, add)| t.checked_add(add));
    }

    to_amount(total)
}

fn interpret_shorthand(tokens: &[Token]) -> i64 {
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

    let unit_steps = [
        UnitType::Billion.multiplier(),
        UnitType::Million.multiplier(),
        UnitType::Thousand.multiplier(),
    ];
    // `numbers.len() <= 3` is the supported shorthand shape: the first number
    // maps to the largest remaining unit. For >3 numbers we clamp the unit
    // index so we never index out of bounds (and never underflow `3 - len`).
    let start_idx = (3_usize).saturating_sub(numbers.len());

    let mut total: Option<i128> = Some(0);
    for (i, num) in numbers.iter().enumerate() {
        let idx = (start_idx + i).min(unit_steps.len() - 1);
        total = total
            .zip(num.times_rounded(unit_steps[idx]))
            .and_then(|(t, add)| t.checked_add(add));
    }
    to_amount(total)
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
        // Zeros in fractional part reduce scale cleanly without overflow.
        assert_eq!(
            parse_amount(
                "1.00000000000000000000000000000000000000 میلیارد تومان",
                true
            ),
            1_000_000_000
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
