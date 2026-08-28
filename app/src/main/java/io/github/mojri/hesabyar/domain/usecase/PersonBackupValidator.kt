package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.Person

/**
 * Validates the `persons` collection of a backup payload. Extracted from
 * [BackupJsonValidator] so that class stays under the TooManyFunctions limit
 * while the per-collection checks remain discoverable.
 */
object PersonBackupValidator {
  fun validate(
    persons: List<Person>,
    errors: MutableList<String>,
    message: (Int, Array<out Any>) -> String
  ) {
    val seen = mutableSetOf<String>()
    persons.forEachIndexed { i, p ->
      if (p.name.isBlank()) errors.add(message(R.string.backup_validation_person_name_blank, arrayOf(i)))
      val key = p.normalizedName.trim()
      if (key.isEmpty()) {
        errors.add(message(R.string.backup_validation_person_normalized_blank, arrayOf(i)))
      } else if (!seen.add(key)) {
        errors.add(message(R.string.backup_validation_person_duplicate, arrayOf(i)))
      }
    }
  }
}
