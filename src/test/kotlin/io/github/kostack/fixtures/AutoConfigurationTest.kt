package io.github.kostack.fixtures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.shell.core.command.Command
import java.util.function.Supplier

class AutoConfigurationTest {
  private val contextRunner =
    ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(AutoConfiguration::class.java))

  @Test
  fun `creates reference repository without requiring purger`() {
    contextRunner.run { context ->
      assertThat(context).hasSingleBean(ReferenceRepository::class.java)
      assertThat(context).doesNotHaveBean(DataFixtureManager::class.java)
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
