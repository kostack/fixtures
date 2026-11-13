package io.github.kostack.fixtures

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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

    Assertions.assertEquals(referenceValue, referenceRepository.getReference<String>(referenceName))
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
  fun `getReference returns the requested type`() {
    referenceRepository.setReference("count", 42)

    val count: Int = referenceRepository.getReference("count")

    assertEquals(42, count)
    assertEquals(42, referenceRepository.getReference("count", Int::class.javaObjectType))
  }

  @Test
  fun `getReference throws for missing reference`() {
    val exception = assertFailsWith<IllegalStateException> { referenceRepository.getReference<String>("missing") }

    assertEquals("Reference not found: missing", exception.message)
  }

  @Test
  fun `getReference throws for wrong type`() {
    referenceRepository.setReference("name", "value")

    val exception = assertFailsWith<IllegalStateException> { referenceRepository.getReference<Int>("name") }

    assertEquals("Reference name is java.lang.String, not java.lang.Integer", exception.message)
  }

  @Test
  fun `setReference does not affect other references`() {
    referenceRepository.setReference("testReference1", "value1")
    referenceRepository.setReference("testReference2", "value2")

    assertEquals("value1", referenceRepository.getReference<String>("testReference1"))
    assertEquals("value2", referenceRepository.getReference<String>("testReference2"))
  }

  @Test
  fun `concurrent writes to the same name accept exactly one value`() {
    val writerCount = 8
    val ready = CountDownLatch(writerCount)
    val start = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(writerCount)

    try {
      val writes =
        (1..writerCount).map { value ->
          executor.submit(
            Callable {
              ready.countDown()
              check(start.await(5, TimeUnit.SECONDS))
              try {
                referenceRepository.setReference("shared", value)
                value
              } catch (exception: IllegalArgumentException) {
                assertEquals("Reference shared already exists", exception.message)
                null
              }
            }
          )
        }

      assertTrue(ready.await(5, TimeUnit.SECONDS))
      start.countDown()
      val acceptedValues = writes.mapNotNull { it.get(5, TimeUnit.SECONDS) }

      assertEquals(1, acceptedValues.size)
      assertEquals(acceptedValues.single(), referenceRepository.getReference<Int>("shared"))
    } finally {
      start.countDown()
      executor.shutdownNow()
    }
  }
}
