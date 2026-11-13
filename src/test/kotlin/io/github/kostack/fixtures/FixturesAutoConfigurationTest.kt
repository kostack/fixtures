package io.github.kostack.fixtures

import kotlinx.coroutines.runBlocking
import net.datafaker.Faker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.Ordered
import org.springframework.shell.core.command.Command
import java.util.function.Supplier
import kotlin.test.assertFailsWith

class FixturesAutoConfigurationTest {
  private val contextRunner =
    ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(FixturesAutoConfiguration::class.java))

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

  @Test
  fun `seeded faker generates the same data on every load`() {
    val names = mutableListOf<String>()

    contextRunner
      .withPropertyValues("kostack.fixtures.faker-seed=42")
      .withBean("nameFixture", AbstractFixture::class.java, Supplier { NameFixture(names) })
      .run { context ->
        val manager = context.getBean(DataFixtureManager::class.java)

        runBlocking {
          manager.loadFixtures(dropDatabase = false)
          manager.loadFixtures(dropDatabase = false)
        }
      }
    contextRunner
      .withPropertyValues("kostack.fixtures.faker-seed=42")
      .withBean("nameFixture", AbstractFixture::class.java, Supplier { NameFixture(names) })
      .run { context ->
        runBlocking { context.getBean(DataFixtureManager::class.java).loadFixtures(dropDatabase = false) }
      }

    assertThat(names).hasSize(3)
    assertThat(names.toSet()).hasSize(1)
  }

  @Test
  fun `backs off when the application provides its own faker`() {
    val faker = Faker()

    contextRunner
      .withBean(Faker::class.java, Supplier { faker })
      .run { context ->
        assertThat(context).hasSingleBean(Faker::class.java)
        assertThat(context.getBean(Faker::class.java)).isSameAs(faker)
      }
  }

  @Test
  fun `failing fixture reports the fixtures loaded before it`() {
    contextRunner
      .withBean("first", AbstractFixture::class.java, Supplier { OrderedFixture(1, "first", mutableListOf()) })
      .withBean("failing", AbstractFixture::class.java, Supplier { FailingFixture() })
      .run { context ->
        val manager = context.getBean(DataFixtureManager::class.java)

        val exception =
          assertFailsWith<FixtureLoadException> { runBlocking { manager.loadFixtures(dropDatabase = false) } }

        assertThat(
          exception
        ).hasMessageStartingWith(
          "Failed to load FailingFixture (loaded before it: OrderedFixture): Reference not found: missing"
        )
      }
  }

  private class NameFixture(
    private val names: MutableList<String>
  ) : AbstractFixture() {
    override suspend fun load() {
      names += faker.name().fullName()
    }
  }

  private class FailingFixture :
    AbstractFixture(),
    Ordered {
    override fun getOrder() = 2

    override suspend fun load() {
      referenceRepository.getReference<String>("missing")
    }
  }
}
