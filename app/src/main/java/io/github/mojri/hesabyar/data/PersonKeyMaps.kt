package io.github.mojri.hesabyar.data

import io.github.mojri.hesabyar.domain.utils.PersonNameNormalizer

internal data class PersonKeyMaps(
  val sourceIdToKey: Map<Long, String>,
  val keyToLocalId: Map<String, Long>
)

internal fun resolvePersonId(
  sourcePersonId: Long?,
  fallbackName: String?,
  maps: PersonKeyMaps
): Long? {
  if (sourcePersonId != null) {
    val key = maps.sourceIdToKey[sourcePersonId]
    val localId = key?.takeIf { it.isNotEmpty() }?.let { maps.keyToLocalId[it] }
    // An id with no usable key (referenced by a loan/transaction but not
    // carried in the persons list) must not orphan the link: fall through to
    // name resolution below instead of returning null outright.
    if (localId != null) return localId
  }
  val fallbackKey =
    fallbackName
      ?.let { PersonNameNormalizer.normalize(PersonNameNormalizer.displayForm(it)) }
      ?.takeIf { it.isNotEmpty() }
  return fallbackKey?.let { maps.keyToLocalId[it] }
}
