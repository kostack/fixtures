package io.github.kostack.fixtures

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("kostack.fixtures")
data class FixturesProperties(
  val fakerSeed: Long? = null
)
