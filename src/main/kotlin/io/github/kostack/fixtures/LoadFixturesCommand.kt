package io.github.kostack.fixtures

import kotlinx.coroutines.runBlocking
import org.springframework.shell.core.command.annotation.Command
import org.springframework.shell.core.command.annotation.Option

class LoadFixturesCommand(
  private val fixtureService: DataFixtureManager
) {
  @Command(name = ["data-fixtures:load"], description = "Loads fixtures")
  fun loadFixtures(
    @Option(
      longName = "dropDatabase",
      shortName = 'd',
      defaultValue = "false",
      description = "Whether to drop the database before loading fixtures"
    )
    dropDatabase: Boolean
  ) = runBlocking {
    fixtureService.loadFixtures(dropDatabase)
  }
}
