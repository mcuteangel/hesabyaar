use crate::models::CurrencyUnit;

/// Format a Rial amount as a string with thousand separators.
/// The output uses Persian locale formatting.
pub fn format_number(value: i64) -> String {
    let s = value.to_string();
    let mut result = String::new();
    let len = s.len();
    for (i, c) in s.chars().enumerate() {
        if i > 0 && (len - i) % 3 == 0 {
            result.push(',');
        }
        result.push(c);
    }
    result
}

/// Format a Rial amount using the given currency unit.
pub fn format_currency(rial: i64, unit: CurrencyUnit) -> String {
    match unit {
        CurrencyUnit::Rial => format!("{} \u{0631}\u{06CC}\u{0627}\u{0644}", format_number(rial)),
        CurrencyUnit::Toman => format!(
            "{} \u{062A}\u{0648}\u{0645}\u{0627}\u{0646}",
            format_number(rial / 10)
        ),
    }
}

/// Convert a display value (in the given unit) to Rial.
pub fn to_rial(display_value: i64, unit: CurrencyUnit) -> i64 {
    match unit {
        CurrencyUnit::Rial => display_value,
        CurrencyUnit::Toman => display_value * 10,
    }
}

/// Convert a Rial value to the given display unit.
pub fn from_rial(rial: i64, unit: CurrencyUnit) -> i64 {
    match unit {
        CurrencyUnit::Rial => rial,
        CurrencyUnit::Toman => rial / 10,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_format_number() {
        assert_eq!(format_number(0), "0");
        assert_eq!(format_number(100), "100");
        assert_eq!(format_number(1000), "1,000");
        assert_eq!(format_number(1234567), "1,234,567");
        assert_eq!(format_number(1234567890), "1,234,567,890");
    }

    #[test]
    fn test_to_rial() {
        assert_eq!(to_rial(1000, CurrencyUnit::Rial), 1000);
        assert_eq!(to_rial(1000, CurrencyUnit::Toman), 10000);
    }

    #[test]
    fn test_from_rial() {
        assert_eq!(from_rial(1000, CurrencyUnit::Rial), 1000);
        assert_eq!(from_rial(1000, CurrencyUnit::Toman), 100);
    }
}
