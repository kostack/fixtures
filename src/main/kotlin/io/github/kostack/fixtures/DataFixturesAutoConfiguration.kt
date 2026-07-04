package io.github.kostack.fixtures

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@AutoConfiguration
@Import(DataFixturesAutoConfiguration.ShellConfiguration::class)
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

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = ["org.springframework.shell.standard.ShellComponent"])
  class ShellConfiguration {
    @Bean
    @ConditionalOnBean(Purger::class)
    fun loadFixturesCommand(fixtureService: DataFixtureManager) = LoadFixturesCommand(fixtureService)
  }
}
