package io.github.kostack.fixtures

import net.datafaker.Faker
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.shell.core.command.Command
import java.util.Random

@AutoConfiguration
@EnableConfigurationProperties(FixturesProperties::class)
class FixturesAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  fun referenceRepository() = ReferenceRepository()

  @Bean
  @ConditionalOnMissingBean
  fun fixturesFaker(properties: FixturesProperties) = properties.fakerSeed?.let { Faker(Random(it)) } ?: Faker()

  @Bean
  @ConditionalOnMissingBean
  fun dataFixtureManager(
    fixtures: ObjectProvider<AbstractFixture>,
    referenceRepository: ReferenceRepository,
    purger: ObjectProvider<Purger>,
    eventPublisher: ApplicationEventPublisher,
    faker: Faker,
    properties: FixturesProperties
  ) = DataFixtureManager(
    fixtures.orderedStream().toList(),
    referenceRepository,
    purger.ifAvailable,
    eventPublisher,
    faker,
    properties.fakerSeed
  )

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(Command::class)
  class ShellConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun loadFixturesCommand(fixtureService: DataFixtureManager) = LoadFixturesCommand(fixtureService)
  }
}
