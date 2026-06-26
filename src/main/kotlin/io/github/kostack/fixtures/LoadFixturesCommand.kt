package io.github.kostack.fixtures

import kotlinx.coroutines.runBlocking
import org.springframework.shell.command.annotation.Option
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod

@ShellComponent
class LoadFixturesCommand(
  private val fixtureService: DataFixtureManager
) {
  @ShellMethod(key = ["data-fixtures:load"], value = "Loads fixtures")
  fun loadFixtures(
    @Option(
      longNames = ["dropDatabase"],
      shortNames = ['d'],
      defaultValue = "true",
      description = "Whether to drop the database before loading fixtures"
    )
    dropDatabase: Boolean
  ) = runBlocking {
    fixtureService.loadFixtures(dropDatabase)
  }
}
