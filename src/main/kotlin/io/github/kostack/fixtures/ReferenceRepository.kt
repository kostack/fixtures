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

  fun <T : Any> getReference(
    name: String,
    type: Class<T>
  ): T {
    val value =
      references[name] ?: error(
        "Reference not found: $name (available: ${references.keys.sorted().joinToString().ifEmpty { "none" }}). " +
          "Make sure the fixture that sets it loads first, for example with @Order."
      )
    check(type.isInstance(value)) {
      "Reference $name is ${value::class.java.name}, not ${type.name}"
    }
    return type.cast(value)
  }

  inline fun <reified T : Any> getReference(name: String): T = getReference(name, T::class.javaObjectType)

  fun reset() = references.clear()
}
