package io.github.kostack.fixtures

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.shell.core.command.Command

@AutoConfiguration
class AutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  fun referenceRepository() = ReferenceRepository()

  @Bean
  @ConditionalOnMissingBean
  fun dataFixtureManager(
    fixtures: ObjectProvider<AbstractFixture>,
    referenceRepository: ReferenceRepository,
    purger: ObjectProvider<Purger>,
    eventPublisher: ApplicationEventPublisher
  ) = DataFixtureManager(fixtures.orderedStream().toList(), referenceRepository, purger.ifAvailable, eventPublisher)

  @Bean
  @ConditionalOnClass(Command::class)
  @ConditionalOnBean(DataFixtureManager::class)
  @ConditionalOnMissingBean
  fun loadFixturesCommand(fixtureService: DataFixtureManager) = LoadFixturesCommand(fixtureService)
}
