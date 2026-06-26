package io.github.kostack.fixtures

import net.datafaker.Faker
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractFixture {
  @Autowired
  protected lateinit var referenceRepository: ReferenceRepository

  abstract suspend fun load()

  companion object {
    val faker = Faker()
  }
}
