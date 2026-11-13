package io.github.kostack.fixtures

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.Ordered
import org.springframework.shell.core.command.Command
import java.util.function.Supplier
import kotlin.test.assertFailsWith

class AutoConfigurationTest {
  private val contextRunner =
    ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(AutoConfiguration::class.java))

  @Test
  fun `creates fixture manager without purger or fixtures`() {
    contextRunner.run { context ->
      assertThat(context).hasSingleBean(ReferenceRepository::class.java)
      assertThat(context).hasSingleBean(DataFixtureManager::class.java)
    }
  }

  @Test
  fun `loads fixtures without purger but refuses to drop the database`() {
    contextRunner.run { context ->
      val manager = context.getBean(DataFixtureManager::class.java)

      runBlocking { manager.loadFixtures(dropDatabase = false) }
      val exception =
        assertFailsWith<IllegalStateException> { runBlocking { manager.loadFixtures(dropDatabase = true) } }

      assertThat(exception).hasMessage("Cannot drop the database: no Purger bean is registered")
    }
  }

  @Test
  fun `loads fixtures in order`() {
    val loaded = mutableListOf<String>()

    contextRunner
      .withBean("second", AbstractFixture::class.java, Supplier { OrderedFixture(2, "second", loaded) })
      .withBean("first", AbstractFixture::class.java, Supplier { OrderedFixture(1, "first", loaded) })
      .run { context ->
        runBlocking { context.getBean(DataFixtureManager::class.java).loadFixtures(dropDatabase = false) }

        assertThat(loaded).containsExactly("first", "second")
      }
  }

  private class OrderedFixture(
    private val order: Int,
    private val name: String,
    private val loaded: MutableList<String>
  ) : AbstractFixture(),
    Ordered {
    override fun getOrder() = order

    override suspend fun load() {
      loaded += name
    }
  }

  @Test
  fun `backs off when a reference repository already exists`() {
    val referenceRepository = ReferenceRepository()

    contextRunner
      .withBean(
        "customReferenceRepository",
        ReferenceRepository::class.java,
        Supplier { referenceRepository }
      ).run { context ->
        assertThat(context).hasSingleBean(ReferenceRepository::class.java)
        assertThat(context).getBean("customReferenceRepository").isSameAs(referenceRepository)
      }
  }

  @Test
  fun `creates fixture manager and shell command when purger exists`() {
    contextRunner
      .withBean(
        Purger::class.java,
        Supplier {
          object : Purger {
            override suspend fun purge() = Unit
          }
        }
      ).run { context ->
        assertThat(context).hasSingleBean(ReferenceRepository::class.java)
        assertThat(context).hasSingleBean(DataFixtureManager::class.java)
        assertThat(context).hasSingleBean(LoadFixturesCommand::class.java)
      }
  }

  @Test
  fun `skips shell command when spring shell is not on the classpath`() {
    contextRunner
      .withClassLoader(FilteredClassLoader(Command::class.java))
      .withBean(
        Purger::class.java,
        Supplier {
          object : Purger {
            override suspend fun purge() = Unit
          }
        }
      ).run { context ->
        assertThat(context).hasNotFailed()
        assertThat(context).hasSingleBean(DataFixtureManager::class.java)
        assertThat(context).doesNotHaveBean(LoadFixturesCommand::class.java)
      }
  }
}
