use crate::models::{HesabyarError, JalaliDate};

const G_MONTH_DAY_OFFSETS: [i32; 13] = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 0];
const J_MONTH_DAYS: [i32; 12] = [31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29];

pub fn is_jalali_leap_year(year: i32) -> bool {
    let r = year % 33;
    matches!(r, 1 | 5 | 9 | 13 | 17 | 22 | 26 | 30)
}

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

fn timestamp_to_gregorian(timestamp_ms: i64) -> Result<(i32, i32, i32), HesabyarError> {
    let secs = timestamp_ms / 1000;
    let mut days = (secs / 86400) as i32;
    let mut day_secs = secs % 86400;

    let hour = (day_secs / 3600) as i32;
    day_secs %= 3600;
    let minute = (day_secs / 60) as i32;
    let _second = (day_secs % 60) as i32;
    let _minute = minute;
    let _hour = hour;

    // Days since Unix epoch (1970-01-01)
    // Add 719468 to convert to proleptic Gregorian day count
    let g_day_no = days + 719468;

    // Convert to year/month/day
    let mut y = (400 * (g_day_no + 62)) / 146097;
    let mut d = g_day_no - (146097 * y + 3) / 400;
    let mut m = (10 * d + 5) / 304;
    let day = d - (304 * m + 5) / 10 + 1;
    m += 3;
    if m > 12 {
        y += 1;
        m -= 12;
    }

    Ok((y, m, day))
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
    while i < 12 && j_day_no >= J_MONTH_DAYS[i] {
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
}
