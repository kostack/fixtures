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
      assertEquals(acceptedValues.single(), referenceRepository.getReference("shared"))
    } finally {
      start.countDown()
      executor.shutdownNow()
    }
  }
}
