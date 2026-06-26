package io.github.kostack.fixtures

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReferenceRepositoryTest {
  val referenceRepository: ReferenceRepository = ReferenceRepository()

  @BeforeEach
  fun setUp() {
    referenceRepository.reset()
  }

  @Test
  fun `setReference adds new reference`() {
    val referenceName = "testReference"
    val referenceValue = "testValue"

    referenceRepository.setReference(referenceName, referenceValue)

    Assertions.assertEquals(referenceValue, referenceRepository.getReference(referenceName))
  }

  @Test
  fun `setReference throws for duplicate name`() {
    val referenceName = "testReference"
    referenceRepository.setReference(referenceName, "testValue1")

    val exception =
      assertFailsWith<IllegalArgumentException> {
        referenceRepository.setReference(referenceName, "testValue1")
      }

    assertEquals("Reference $referenceName already exists", exception.message)
  }

  @Test
  fun `setReference does not affect other references`() {
    referenceRepository.setReference("testReference1", "value1")
    referenceRepository.setReference("testReference2", "value2")

    assertEquals("value1", referenceRepository.getReference("testReference1"))
    assertEquals("value2", referenceRepository.getReference("testReference2"))
  }
}
