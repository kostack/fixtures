package io.github.kostack.fixtures

class FixtureLoadException(
  message: String,
  cause: Throwable
) : RuntimeException(message, cause)
