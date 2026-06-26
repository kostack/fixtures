package io.github.kostack.fixtures

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PurgerImpl : Purger {
  override suspend fun purge() {
    log.warn("Attempting to drop all collections reactively...")
  }

  companion object {
    private val log = LoggerFactory.getLogger(PurgerImpl::class.java)
  }
}
