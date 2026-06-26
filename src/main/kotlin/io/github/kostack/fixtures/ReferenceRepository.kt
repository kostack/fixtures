package io.github.kostack.fixtures

import org.springframework.stereotype.Component

@Component
class ReferenceRepository {
  private val references = mutableMapOf<String, Any>()

  fun setReference(
    name: String,
    value: Any
  ) {
    if (references.containsKey(name)) throw IllegalArgumentException("Reference $name already exists")
    references[name] = value
  }

  fun getReference(name: String): Any = references[name] ?: error("Reference not found: $name")

  fun reset() = references.clear()
}
