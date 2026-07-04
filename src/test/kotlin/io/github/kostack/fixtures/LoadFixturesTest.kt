package io.github.kostack.fixtures

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [TestApplication::class])
class LoadFixturesTest {
  @Autowired
  lateinit var loadFixturesCommand: LoadFixturesCommand

  @MockkBean
  lateinit var fixtureService: DataFixtureManager

  @Test
  fun `loadFixtures with dropDatabase to true`() =
    runTest {
      coEvery { fixtureService.loadFixtures(true) } returns Unit

      loadFixturesCommand.loadFixtures(true)

      coVerify(exactly = 1) { fixtureService.loadFixtures(true) }
    }

  @Test
  fun `loadFixtures with dropDatabase false`() =
    runTest {
      coEvery { fixtureService.loadFixtures(false) } returns Unit

      loadFixturesCommand.loadFixtures(false)

      coVerify(exactly = 1) { fixtureService.loadFixtures(false) }
    }

  @Test
  fun `loadFixtures does not call service with true when false is passed`() =
    runTest {
      coEvery { fixtureService.loadFixtures(false) } returns Unit

      loadFixturesCommand.loadFixtures(false)

      coVerify(exactly = 0) { fixtureService.loadFixtures(true) }
      coVerify(exactly = 1) { fixtureService.loadFixtures(false) }
    }
}
