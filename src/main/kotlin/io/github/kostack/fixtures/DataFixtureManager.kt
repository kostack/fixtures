package io.github.kostack.fixtures

import org.apache.commons.logging.LogFactory
import org.springframework.context.ApplicationEventPublisher

class DataFixtureManager(
  private val fixtures: List<AbstractFixture>,
  private val referenceRepository: ReferenceRepository,
  private val purger: Purger?,
  private val eventPublisher: ApplicationEventPublisher
) {
  suspend fun loadFixtures(dropDatabase: Boolean) {
    referenceRepository.reset()

    if (dropDatabase) dropDatabase()

    eventPublisher.publishEvent(FixtureLoadEvent())

    for (fixture in fixtures) {
      fixture.load()
      log.info("${fixture::class.java.simpleName} loaded")
    }

    log.info("All fixtures loaded (${fixtures.size})")
  }

  suspend fun dropDatabase() {
    val purger = checkNotNull(purger) { "Cannot drop the database: no Purger bean is registered" }
    log.info("Dropping database")
    purger.purge()
  }

  private companion object {
    private val log = LogFactory.getLog(DataFixtureManager::class.java)
  }
}
