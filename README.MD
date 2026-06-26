# Kostack Fixtures

A Kotlin/Spring Boot library for managing and loading test data fixtures. It provides a framework for creating reusable fixtures, sharing references between them, purging the database before loading, and triggering events for event-driven setup.

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.kostack:fixtures:<version>")
}
```

Or `pom.xml`:

```xml
<dependency>
    <groupId>io.github.kostack</groupId>
    <artifactId>fixtures</artifactId>
    <version>VERSION</version>
</dependency>
```

---

## Core Concepts

| Component             | Description                                                    |
|-----------------------|----------------------------------------------------------------|
| `AbstractFixture`     | Base class for all fixtures. Implement `load()` to set up data |
| `DataFixtureManager`  | Orchestrates fixture loading, database purging, and events     |
| `ReferenceRepository` | In-memory store for sharing objects between fixtures           |
| `Purger`              | Interface for clearing the database before loading fixtures    |
| `FixtureLoadEvent`    | Spring event published before fixtures are loaded              |
| `LoadFixturesCommand` | Spring Shell command: `data-fixtures:load`                     |

---

## Creating a Fixture

Extend `AbstractFixture` and implement the `load()` suspend function:

```kotlin
import io.github.kostack.fixtures.AbstractFixture
import org.springframework.stereotype.Component

@Component
class UserFixture(
    private val userRepository: UserRepository,
) : AbstractFixture() {

    override suspend fun load() {
        val user = User(
            name = faker.name().fullName(),
            email = faker.internet().emailAddress(),
        )
        userRepository.save(user)

        // Share this user so other fixtures can reference it
        referenceRepository.setReference("admin-user", user)
    }
}
```

- `faker` is a built-in `net.datafaker.Faker` instance available on the companion object.
- `referenceRepository` is auto-wired from the base class.

---

## Sharing References Between Fixtures

Use `ReferenceRepository` to pass objects from one fixture to another.

**Fixture A — sets a reference:**

```kotlin
@Component
class OrganizationFixture(
    private val orgRepository: OrganizationRepository,
) : AbstractFixture() {

    override suspend fun load() {
        val org = orgRepository.save(Organization(name = "Acme Corp"))
        referenceRepository.setReference("acme-org", org)
    }
}
```

**Fixture B — reads that reference:**

```kotlin
@Component
class ProjectFixture(
    private val projectRepository: ProjectRepository,
) : AbstractFixture() {

    override suspend fun load() {
        val org = referenceRepository.getReference("acme-org") as Organization
        projectRepository.save(Project(name = "Alpha", organization = org))
    }
}
```

> **Note:** Fixture loading is sequential, so load order matters. Ensure the fixture that sets a reference is loaded before the one that reads it (e.g., via Spring's `@DependsOn` or by ordering beans).

> **Note:** `setReference` throws `IllegalArgumentException` if the same name is registered twice. `getReference` throws if the name does not exist.

---

## Implementing a Purger

Implement the `Purger` interface to clear your database before fixtures load:

```kotlin
import io.github.kostack.fixtures.Purger
import org.springframework.stereotype.Component

@Component
class MongoPurger(
    private val mongoTemplate: ReactiveMongoTemplate,
) : Purger {

    override suspend fun purge() {
        mongoTemplate.collectionNames
            .flatMap { mongoTemplate.dropCollection(it) }
            .awaitLast()
    }
}
```

The `DataFixtureManager` calls `purger.purge()` automatically when `loadFixtures(dropDatabase = true)` is invoked.

---

## Loading Fixtures Programmatically

Inject `DataFixtureManager` and call `loadFixtures`:

```kotlin
@SpringBootTest
class MyIntegrationTest(
    private val dataFixtureManager: DataFixtureManager,
) {

    @BeforeEach
    fun setUp() = runBlocking {
        dataFixtureManager.loadFixtures(dropDatabase = true)
    }

    @Test
    fun `users are loaded`() {
        // test against seeded data
    }
}
```

To only drop the database without loading fixtures:

```kotlin
dataFixtureManager.dropDatabase()
```

---

## Loading Fixtures via Shell Command

The library registers a Spring Shell command `data-fixtures:load`:

```shell
# Drop the database and load all fixtures (default)
shell:> data-fixtures:load

# Load fixtures without dropping the database
shell:> data-fixtures:load --dropDatabase false
shell:> data-fixtures:load -d false
```

---

## Listening to FixtureLoadEvent

`FixtureLoadEvent` is published right before fixtures start loading. Use it to perform additional setup:

```kotlin
import io.github.kostack.fixtures.FixtureLoadEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class CacheWarmupListener {

    @EventListener
    fun onFixtureLoad(event: FixtureLoadEvent) {
        // e.g. clear caches, reset counters, etc.
    }
}
```

---

## Full Example

```
src/
├── main/kotlin/com/example/
│   ├── User.kt
│   └── UserRepository.kt
└── test/kotlin/com/example/
    ├── fixtures/
    │   ├── UserFixture.kt
    │   └── PostFixture.kt
    └── UserIntegrationTest.kt
```

**UserFixture.kt**

```kotlin
@Component
class UserFixture(
    private val userRepository: UserRepository,
) : AbstractFixture() {

    override suspend fun load() {
        val user = userRepository.save(
            User(
                name = faker.name().fullName(),
                email = faker.internet().emailAddress(),
            )
        )
        referenceRepository.setReference("default-user", user)
    }
}
```

**PostFixture.kt**

```kotlin
@Component
class PostFixture(
    private val postRepository: PostRepository,
) : AbstractFixture() {

    override suspend fun load() {
        val user = referenceRepository.getReference("default-user") as User
        postRepository.save(Post(title = faker.lorem().sentence(), author = user))
    }
}
```

**UserIntegrationTest.kt**

```kotlin
@SpringBootTest
class UserIntegrationTest(
    private val dataFixtureManager: DataFixtureManager,
    private val userRepository: UserRepository,
) {

    @BeforeEach
    fun setUp() = runBlocking {
        dataFixtureManager.loadFixtures(dropDatabase = true)
    }

    @Test
    fun `a user is seeded`() = runBlocking {
        val users = userRepository.findAll().collectList().awaitSingle()
        assertTrue(users.isNotEmpty())
    }
}
```

---

## License

MIT
