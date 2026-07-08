use crate::models::{HesabyarError, JalaliDate};

const G_MONTH_DAY_OFFSETS: [i32; 13] = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 0];
const J_MONTH_DAYS: [i32; 12] = [31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29];

// ===========================================================================
// Packed Jalali Date (i64)
//
// For FFI safety, Jalali dates are packed into a single i64:
//
//   bits 63..16 : year   (i64 >> 16)
//   bits 15..8  : month  (i64 >> 8) & 0xFF
//   bits  7..0  : day    i64 & 0xFF
//
// Example: 1403/01/01
//   packed = (1403 << 16) | (1 << 8) | 1 = 92_027_905
//
// To decode on the Kotlin/JVM side:
//   val year  = (packed shr 16).toInt()          // 1403
//   val month = ((packed shr 8) and 0xFF).toInt() // 1
//   val day   = (packed and 0xFF).toInt()         // 1
//
// Error sentinel: packed == 0 means the conversion failed.
// ===========================================================================

#[uniffi::export]
pub fn is_jalali_leap_year(year: i32) -> bool {
    let r = year % 33;
    matches!(r, 1 | 5 | 9 | 13 | 17 | 22 | 26 | 30)
}

#[uniffi::export]
pub fn get_jalali_days_in_month(year: i32, month: i32) -> i32 {
    if (1..=6).contains(&month) {
        31
    } else if (7..=11).contains(&month) {
        30
    } else if is_jalali_leap_year(year) {
        30
    } else {
        29
    }
}

pub fn gregorian_to_jalali(timestamp_ms: i64) -> Result<JalaliDate, HesabyarError> {
    let (gy, gm, gd) = timestamp_to_gregorian(timestamp_ms)?;
    gregorian_to_jalali_date(gy, gm, gd)
}

/// FFI-safe wrapper: returns packed i64 (year << 16 | month << 8 | day).
/// On error, returns 0 (representing an invalid date).
pub fn gregorian_to_jalali_packed(timestamp_ms: i64) -> i64 {
    match gregorian_to_jalali(timestamp_ms) {
        Ok(jd) => ((jd.year as i64) << 16) | ((jd.month as i64) << 8) | (jd.day as i64),
        Err(_) => 0,
    }
}

fn is_leap_gregorian(year: i64) -> bool {
    (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}

fn timestamp_to_gregorian(timestamp_ms: i64) -> Result<(i32, i32, i32), HesabyarError> {
    let secs = timestamp_ms / 1000;
    let days = secs / 86400;

    // Convert days-since-epoch to year/month/day by walking year-by-year.
    // This is the exact inverse of gregorian_to_timestamp().
    let mut year: i64 = 1970;
    let mut remaining = days;

    // Handle negative timestamps (pre-epoch) by walking backward
    if remaining < 0 {
        year = 1969;
        remaining += 1; // Dec 31, 1969 is day -1
        while remaining < 0 {
            year -= 1;
            let days_in_year: i64 = if is_leap_gregorian(year) { 366 } else { 365 };
            remaining += days_in_year;
        }
    } else {
        loop {
            let days_in_year: i64 = if is_leap_gregorian(year) { 366 } else { 365 };
            if remaining < days_in_year {
                break;
            }
            remaining -= days_in_year;
            year += 1;
        }
    }

    let month_days: [i64; 12] = if is_leap_gregorian(year) {
        [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    } else {
        [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    };

    let mut month = 0usize;
    while month < 12 && remaining >= month_days[month] {
        remaining -= month_days[month];
        month += 1;
    }

    Ok((year as i32, (month + 1) as i32, (remaining + 1) as i32))
}

pub fn gregorian_to_jalali_date(
    g_year: i32,
    g_month: i32,
    g_day: i32,
) -> Result<JalaliDate, HesabyarError> {
    let gy = g_year - 1600;
    let gm = g_month - 1;
    let gd = g_day - 1;

    let mut g_day_no = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400;
    g_day_no += G_MONTH_DAY_OFFSETS[gm as usize];
    if gm > 1 && ((g_year % 4 == 0 && g_year % 100 != 0) || (g_year % 400 == 0)) {
        g_day_no += 1;
    }
    g_day_no += gd;

    let mut j_day_no = g_day_no - 79;
    let j_np = j_day_no / 12053;
    j_day_no %= 12053;
    let mut jy = 979 + 33 * j_np + 4 * (j_day_no / 1461);
    j_day_no %= 1461;
    if j_day_no >= 366 {
        jy += (j_day_no - 1) / 365;
        j_day_no = (j_day_no - 1) % 365;
    }

    let mut i = 0;
    while i < 11 && j_day_no >= J_MONTH_DAYS[i] {
        j_day_no -= J_MONTH_DAYS[i];
        i += 1;
    }

    Ok(JalaliDate {
        year: jy,
        month: (i + 1) as i32,
        day: (j_day_no + 1) as i32,
    })
}

pub fn jalali_to_gregorian(j_year: i32, j_month: i32, j_day: i32) -> Result<i64, HesabyarError> {
    if j_year < 1 || j_month < 1 || j_month > 12 || j_day < 1 {
        return Err(HesabyarError::CalendarError {
            detail: format!("Invalid Jalali date: {}/{}/{}", j_year, j_month, j_day),
        });
    }
    // Validate day against month-specific limits
    let max_day = get_jalali_days_in_month(j_year, j_month);
    if j_day > max_day {
        return Err(HesabyarError::CalendarError {
            detail: format!(
                "Invalid Jalali day {} for month {} (max {})",
                j_day, j_month, max_day
            ),
        });
    }
    let jy = j_year - 979;
    let jm = j_month - 1;
    let jd = j_day - 1;

    let mut j_day_no = 365 * jy + (jy / 33) * 8 + (jy % 33 + 3) / 4;
    for i in 0..jm {
        j_day_no += J_MONTH_DAYS[i as usize];
    }
    j_day_no += jd;

    let mut g_day_no = j_day_no + 79;
    let mut gy = 1600 + 400 * (g_day_no / 146097);
    g_day_no %= 146097;

    let mut leap = true;
    if g_day_no >= 36525 {
        g_day_no -= 1;
        gy += 100 * (g_day_no / 36524);
        g_day_no %= 36524;
        if g_day_no >= 365 {
            g_day_no += 1;
        } else {
            leap = false;
        }
    }
    gy += 4 * (g_day_no / 1461);
    g_day_no %= 1461;
    if g_day_no >= 366 {
        leap = false;
        g_day_no -= 1;
        gy += g_day_no / 365;
        g_day_no %= 365;
    }

    let g_days_in_month = [
        31,
        if leap { 29 } else { 28 },
        31, 30, 31, 30, 31, 31, 30, 31, 30, 31,
    ];

    let mut i = 0;
    while i < 12 && g_day_no >= g_days_in_month[i] {
        g_day_no -= g_days_in_month[i];
        i += 1;
    }

    let gm = (i + 1) as i32;
    let gd = (g_day_no + 1) as i32;

    // Convert Gregorian date to epoch milliseconds
    gregorian_to_timestamp(gy, gm, gd)
}

/// FFI-safe wrapper: returns epoch milliseconds (i64).
/// On error, returns i64::MIN (a value that cannot be a valid timestamp).
pub fn jalali_to_gregorian_packed(j_year: i32, j_month: i32, j_day: i32) -> i64 {
    jalali_to_gregorian(j_year, j_month, j_day).unwrap_or(i64::MIN)
}

fn gregorian_to_timestamp(year: i32, month: i32, day: i32) -> Result<i64, HesabyarError> {
    // Compute days since 1970-01-01
    let y = year as i64;
    let m = month as i64;
    let d = day as i64;

    let mut days = 365 * (y - 1970) + (y - 1969) / 4 - (y - 1901) / 100 + (y - 1601) / 400;
    let month_days = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334];
    days += month_days[(m - 1) as usize];
    if m > 2 && ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) {
        days += 1;
    }
    days += d - 1;

    Ok(days * 86400 * 1000)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_known_jalali_date() {
        // 2024-03-20 = 1403/01/01 (Nowruz)
        let result = gregorian_to_jalali_date(2024, 3, 20).unwrap();
        assert_eq!(result.year, 1403);
        assert_eq!(result.month, 1);
        assert_eq!(result.day, 1);
    }

    #[test]
    fn test_known_gregorian_date() {
        // 1403/01/01 = 2024-03-20
        let ts = jalali_to_gregorian(1403, 1, 1).unwrap();
        let result = timestamp_to_gregorian(ts).unwrap();
        assert_eq!(result.0, 2024);
        assert_eq!(result.1, 3);
        assert_eq!(result.2, 20);
    }

    #[test]
    fn test_gregorian_2025_nowruz_roundtrip() {
        // 2025-03-20 = 1403/12/30 (day before Nowruz 1404). Leap-year boundary must not
        // overflow to an invalid month 13.
        let jd = gregorian_to_jalali_date(2025, 3, 20).unwrap();
        assert_eq!(jd.year, 1403);
        assert_eq!(jd.month, 12);
        assert_eq!(jd.day, 30);
        // Round-trips back to the same Gregorian date.
        let ts = jalali_to_gregorian(1403, 12, 30).unwrap();
        let g = timestamp_to_gregorian(ts).unwrap();
        assert_eq!((g.0, g.1, g.2), (2025, 3, 20));
    }

    #[test]
    fn test_leap_year() {
        assert!(is_jalali_leap_year(1403)); // 1403 % 33 = 1
        assert!(!is_jalali_leap_year(1402)); // 1402 % 33 = 0
    }

    #[test]
    fn test_days_in_month() {
        assert_eq!(get_jalali_days_in_month(1403, 1), 31);
        assert_eq!(get_jalali_days_in_month(1403, 7), 30);
        assert_eq!(get_jalali_days_in_month(1403, 12), 30); // 1403 is leap
        assert_eq!(get_jalali_days_in_month(1402, 12), 29); // 1402 is not leap
    }

    // =====================================================================
    // FFI-safe packed function tests
    // =====================================================================

    #[test]
    fn test_gregorian_to_jalali_packed() {
        // 2024-03-20 = 1403/01/01
        // Packed: (1403 << 16) | (1 << 8) | 1 = 92027905
        let packed = gregorian_to_jalali_packed(1710950400000); // approx 2024-03-20
        let year = (packed >> 16) as i32;
        let month = ((packed >> 8) & 0xFF) as i32;
        let day = (packed & 0xFF) as i32;
        assert_eq!(year, 1403);
        assert_eq!(month, 1);
        assert!(day >= 1 && day <= 2, "day should be 1 or 2 for approx March 20, got {}", day);
    }

    #[test]
    fn test_gregorian_to_jalali_packed_invalid() {
        // Negative timestamp should still produce a valid date (no panic)
        let packed = gregorian_to_jalali_packed(-1);
        // Should not be 0 (which means error)
        assert_ne!(packed, 0);
    }

    #[test]
    fn test_jalali_to_gregorian_packed() {
        let ts = jalali_to_gregorian_packed(1403, 1, 1);
        assert!(ts > 0);
    }

    #[test]
    fn test_jalali_to_gregorian_packed_invalid() {
        // Invalid date should return i64::MIN (no panic)
        let ts = jalali_to_gregorian_packed(0, 0, 0);
        assert_eq!(ts, i64::MIN);
    }
}
