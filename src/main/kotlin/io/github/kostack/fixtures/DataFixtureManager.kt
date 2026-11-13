package io.github.kostack.fixtures

import net.datafaker.Faker
import org.apache.commons.logging.LogFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.util.ClassUtils
import java.util.Random
import kotlin.coroutines.cancellation.CancellationException

class DataFixtureManager(
  private val fixtures: List<AbstractFixture>,
  private val referenceRepository: ReferenceRepository,
  private val purger: Purger?,
  private val eventPublisher: ApplicationEventPublisher,
  private val faker: Faker? = null,
  private val fakerSeed: Long? = null
) {
  suspend fun loadFixtures(dropDatabase: Boolean) {
    referenceRepository.reset()
    reseedFaker()

    if (dropDatabase) dropDatabase()

    eventPublisher.publishEvent(FixtureLoadEvent())

    val loaded = mutableListOf<String>()
    for (fixture in fixtures) {
      val name = ClassUtils.getUserClass(fixture).simpleName
      try {
        fixture.load()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        val loadedSoFar = loaded.joinToString().ifEmpty { "none" }
        throw FixtureLoadException(
          "Failed to load $name (loaded before it: $loadedSoFar): ${exception.message}",
          exception
        )
      }
      loaded += name
      log.info("$name loaded")
    }

    log.info("All fixtures loaded (${fixtures.size})")
  }

  suspend fun dropDatabase() {
    val purger = checkNotNull(purger) { "Cannot drop the database: no Purger bean is registered" }
    log.info("Dropping database")
    purger.purge()
  }

  private fun reseedFaker() {
    if (faker == null || fakerSeed == null) return
    val random = faker.random().randomInternal
    if (random is Random) {
      random.setSeed(fakerSeed)
    } else {
      log.warn("Cannot apply Faker seed: the Faker bean does not use java.util.Random")
    }
  }

  private companion object {
    private val log = LogFactory.getLog(DataFixtureManager::class.java)
  }
}
