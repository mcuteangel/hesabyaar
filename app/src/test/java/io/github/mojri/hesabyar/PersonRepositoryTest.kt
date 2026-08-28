package io.github.mojri.hesabyar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AppDatabase
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.HesabyarRepository
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Person
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PersonRepositoryTest {
  private lateinit var database: AppDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database =
      Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    database.accountDao().insertAllBlocking(listOf(AccountEntity.DEFAULT_ACCOUNT))
  }

  @After
  fun tearDown() {
    database.close()
  }

  private fun createRepository(): HesabyarRepository =
    HesabyarRepository(
      database.transactionDao(),
      database.loanDao(),
      database.installmentDao(),
      database.paymentHistoryDao(),
      database.categoryDao(),
      database.bankLoanDao(),
      database.accountDao(),
      database.personDao(),
      database
    )

  private fun person(name: String) =
    Person(
      name = name,
      normalizedName =
        io.github.mojri.hesabyar.domain.utils.PersonNameNormalizer
          .normalize(name)
    )

  @Test
  fun upsertPersonDeduplicatesNormalizedNamesAndKeepsFirstDisplayForm() =
    runTest {
      val repo = createRepository()

      val first = repo.upsertPerson(person("علی"))
      val arabicVariant = repo.upsertPerson(person("علي"))
      val spacedVariant = repo.upsertPerson(person("  علی  "))

      assertEquals("Variants must collapse to the first row", first.id, arabicVariant.id)
      assertEquals("Variants must collapse to the first row", first.id, spacedVariant.id)
      assertEquals("First display spelling wins", "علی", arabicVariant.name)

      val all = database.personDao().getAllPersonsIncludingArchivedBlocking()
      assertEquals("Only one stored row", 1, all.size)
      assertEquals("علی", all.single().normalizedName)
    }

  @Test
  fun upsertPersonFillsContactDetailsWithoutOverwritingName() =
    runTest {
      val repo = createRepository()
      val created = repo.upsertPerson(person("رضا"))

      val updated = repo.upsertPerson(person("رضا").copy(phone = "0912", notes = "همکار"))

      assertEquals(created.id, updated.id)
      assertEquals("0912", updated.phone)
      assertEquals("همکار", updated.notes)
    }

  @Test
  fun renamePersonSyncsLoansAndTransactionsPersonNames() =
    runTest {
      val repo = createRepository()
      val person = repo.upsertPerson(person("علی"))

      val loanId =
        repo.insertLoan(
          Loan(
            personName = "علی",
            personId = person.id,
            type = LoanType.DEBTOR,
            originalAmount = 5_000L,
            remainingAmount = 5_000L,
            description = "test"
          )
        )
      repo.insertTransaction(
        Transaction(
          type = TransactionType.EXPENSE,
          categoryId = seedCategoryId(repo),
          amount = 1_000L,
          description = "test",
          personName = "علی",
          personId = person.id
        )
      )

      val renamed = repo.renamePerson(person.id, "علی رضایی")

      assertTrue(renamed)
      assertEquals("علی رضایی", database.loanDao().getLoanById(loanId)?.personName)
      assertEquals(
        "transactions.personName must sync too (D3)",
        "علی رضایی",
        database
          .transactionDao()
          .getAllTransactionsBlocking()
          .single()
          .personName
      )
      val stored = requireNotNull(database.personDao().getPersonById(person.id))
      assertEquals("علی رضایی", stored.name)
    }

  @Test
  fun renamePersonRejectsCollisionWithAnotherPersonsNormalizedName() =
    runTest {
      val repo = createRepository()
      val ali = repo.upsertPerson(person("علی"))
      val reza = repo.upsertPerson(person("رضا"))

      val renamed = repo.renamePerson(ali.id, "  رضا ")

      assertFalse("Rename onto another person's key must be refused", renamed)
      assertEquals("علی", requireNotNull(database.personDao().getPersonById(ali.id)).name)
      assertNotNull(database.personDao().getPersonById(reza.id))
    }

  @Test
  fun renamePersonRejectsBlankNameAndUnknownId() =
    runTest {
      val repo = createRepository()
      val person = repo.upsertPerson(person("علی"))

      assertFalse(repo.renamePerson(person.id, "   "))
      assertFalse(repo.renamePerson(999L, "معتبر"))
    }

  @Test
  fun upsertPersonRejectsNameThatNormalizesToEmpty() =
    runTest {
      val repo = createRepository()
      // ZWSP-only name: displayForm strips zero-width, so display is empty
      // and either the blank-check or the empty-key check rejects it.
      try {
        repo.upsertPerson(person("\u200B\u200C\u200D"))
        fail("expected IllegalArgumentException for zero-width-only name")
      } catch (e: IllegalArgumentException) {
        val msg = e.message ?: ""
        assertTrue(msg.contains("normalizes to empty") || msg.contains("Person name is blank"))
      }
      val all = repo.getAllPersonsIncludingArchived()
      assertTrue("no row should be inserted for an empty-key name", all.isEmpty())
    }

  @Test
  fun deletePersonRemovesRowButKeepsDenormalizedNames() =
    runTest {
      val repo = createRepository()
      val person = repo.upsertPerson(person("علی"))
      val loanId =
        repo.insertLoan(
          Loan(
            personName = "علی",
            personId = person.id,
            type = LoanType.DEBTOR,
            originalAmount = 5_000L,
            remainingAmount = 5_000L,
            description = "test"
          )
        )

      repo.deletePerson(person.copy(id = person.id))

      assertNull(database.personDao().getPersonById(person.id))
      assertEquals(
        "Display name survives on the loan row (D3)",
        "علی",
        database.loanDao().getLoanById(loanId)?.personName
      )
    }

  private suspend fun seedCategoryId(repo: HesabyarRepository): Long =
    repo.insertCategory(
      Category(
        name = "Loans",
        key = "Loans",
        icon = "HistoryEdu",
        color = 0xFF9C27B0L,
        type = CategoryType.BOTH
      )
    )
}
