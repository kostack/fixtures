package io.github.kostack.fixtures.fixture

import io.github.kostack.fixtures.AbstractFixture
import org.springframework.stereotype.Component

@Component
class TestFixture : AbstractFixture() {
  override suspend fun load() { }
}
