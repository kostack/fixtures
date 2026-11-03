package io.github.kostack.fixtures

import java.util.concurrent.ConcurrentHashMap

class ReferenceRepository {
  private val references = ConcurrentHashMap<String, Any>()

  fun setReference(
    name: String,
    value: Any
  ) {
    require(references.putIfAbsent(name, value) == null) { "Reference $name already exists" }
  }

  fun getReference(name: String): Any = references[name] ?: error("Reference not found: $name")

  fun reset() = references.clear()
}
