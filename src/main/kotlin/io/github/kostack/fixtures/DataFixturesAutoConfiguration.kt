package io.github.kostack.fixtures

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.shell.standard.ShellMethod

@AutoConfiguration
class DataFixturesAutoConfiguration {
  @Bean
  fun referenceRepository() = ReferenceRepository()

  @Bean
  @ConditionalOnBean(Purger::class)
  fun dataFixtureManager(
    fixtures: List<AbstractFixture>,
    referenceRepository: ReferenceRepository,
    purger: Purger,
    eventPublisher: ApplicationEventPublisher
  ) = DataFixtureManager(fixtures, referenceRepository, purger, eventPublisher)

  @Bean
  @ConditionalOnClass(ShellMethod::class)
  @ConditionalOnBean(DataFixtureManager::class)
  @ConditionalOnMissingBean
  fun loadFixturesCommand(fixtureService: DataFixtureManager) = LoadFixturesCommand(fixtureService)
}
