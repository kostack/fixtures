package io.github.kostack.fixtures

import org.fusesource.jansi.Ansi.ansi
import org.springframework.context.ApplicationEventPublisher

class DataFixtureManager(
  private val fixtures: List<AbstractFixture>,
  private val referenceRepository: ReferenceRepository,
  private val purger: Purger,
  private val eventPublisher: ApplicationEventPublisher
) {
  suspend fun loadFixtures(dropDatabase: Boolean) {
    referenceRepository.reset()

    if (dropDatabase) {
      dropDatabase()
    }

    eventPublisher.publishEvent(FixtureLoadEvent())

    for (fixture in fixtures) {
      fixture.load()
      val name = fixture::class.java.simpleName
      println(ansi().fgBrightBlue().a("$name loaded.").reset())
    }

    println(ansi().fgGreen().a("All DataFixtures loaded.").reset())
  }

  suspend fun dropDatabase() = purger.purge()
}
