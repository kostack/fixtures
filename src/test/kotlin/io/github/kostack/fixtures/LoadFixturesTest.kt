package io.github.kostack.fixtures

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandRegistry
import org.springframework.shell.core.command.DefaultCommandParser
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertNotNull

@SpringBootTest(classes = [TestApplication::class])
class LoadFixturesTest {
  @Autowired
  lateinit var loadFixturesCommand: LoadFixturesCommand

  @Autowired
  lateinit var commandRegistry: CommandRegistry

  @MockkBean
  lateinit var fixtureService: DataFixtureManager

  @ParameterizedTest
  @CsvSource(
    "data-fixtures:load, true",
    "data-fixtures:load --dropDatabase false, false",
    "data-fixtures:load -d false, false"
  )
  fun `shell command parses database option`(
    input: String,
    dropDatabase: Boolean
  ) {
    coEvery { fixtureService.loadFixtures(dropDatabase) } returns Unit
    val command = assertNotNull(commandRegistry.getCommandByName("data-fixtures:load"))
    val parsedInput = DefaultCommandParser(commandRegistry).parse(input)

    command.execute(CommandContext(parsedInput, commandRegistry, PrintWriter(StringWriter()), mockk()))

    coVerify(exactly = 1) { fixtureService.loadFixtures(dropDatabase) }
  }

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
