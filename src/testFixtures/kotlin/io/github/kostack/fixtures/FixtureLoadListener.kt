package io.github.kostack.fixtures

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class FixtureLoadListener {
  @EventListener(FixtureLoadEvent::class)
  fun onFixtureLoad(event: FixtureLoadEvent) {
    println("Fixture load event received")
  }
}
