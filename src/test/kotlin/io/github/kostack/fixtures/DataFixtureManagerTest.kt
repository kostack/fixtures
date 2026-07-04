package io.github.kostack.fixtures

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest(classes = [TestApplication::class])
class DataFixtureManagerTest {
  @MockkBean
  lateinit var referenceRepository: ReferenceRepository

  @MockkBean
  lateinit var purger: Purger

  @MockkBean(name = "fixture1")
  lateinit var fixture1: AbstractFixture

  @MockkBean(name = "fixture2")
  lateinit var fixture2: AbstractFixture

  @Autowired
  lateinit var dataFixtureManager: DataFixtureManager

  @Test
  fun `should load all fixtures when dropDatabase is false`() =
    runBlocking {
      coEvery { fixture1.load() } returns Unit
      coEvery { fixture2.load() } returns Unit
      every { referenceRepository.reset() } returns Unit

      dataFixtureManager.loadFixtures(false)

      coVerify(exactly = 1) { fixture1.load() }
      coVerify(exactly = 1) { fixture2.load() }
      coVerify(exactly = 0) { purger.purge() }
      verify(exactly = 1) { referenceRepository.reset() }
    }

  @Test
  fun `should purge database and load fixtures when dropDatabase is true`() =
    runBlocking {
      coEvery { fixture1.load() } returns Unit
      coEvery { fixture2.load() } returns Unit
      coEvery { purger.purge() } returns Unit
      every { referenceRepository.reset() } returns Unit

      dataFixtureManager.loadFixtures(true)

      coVerify(exactly = 1) { purger.purge() }
      coVerify(exactly = 1) { fixture1.load() }
      coVerify(exactly = 1) { fixture2.load() }
      verify(exactly = 1) { referenceRepository.reset() }
    }

  @Test
  fun `should stop loading fixtures if one fails`() =
    runBlocking {
      coEvery { fixture1.load() } throws RuntimeException("Fixture1 load failed")
      coEvery { fixture2.load() } returns Unit
      every { referenceRepository.reset() } returns Unit

      val exception =
        assertFailsWith<RuntimeException> {
          dataFixtureManager.loadFixtures(false)
        }

      assertEquals("Fixture1 load failed", exception.message)

      coVerify(exactly = 1) { fixture1.load() }
      coVerify(exactly = 0) { fixture2.load() }
      verify(exactly = 1) { referenceRepository.reset() }
    }
}
