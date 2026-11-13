# Kostack Fixtures

A Kotlin/Spring Boot library for managing and loading test data fixtures. It provides a framework for creating reusable fixtures, sharing references between them, purging the database before loading, and triggering events for event-driven setup.

## Installation

Requirements: Java 25 and Spring Boot 4.1. The library is built with Kotlin 2.4.21, Spring Boot 4.1.2 and Spring Shell 4.0.3.

Add the dependency alongside your application's Spring Boot starter in `build.gradle.kts`:

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("io.github.kostack:fixtures:<version>")

    // Optional: enables the `data-fixtures:load` shell command
    implementation(platform("org.springframework.shell:spring-shell-dependencies:4.0.3"))
    implementation("org.springframework.shell:spring-shell-starter-jni")
}
```

Or `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.jetbrains.kotlinx</groupId>
    <artifactId>kotlinx-coroutines-core</artifactId>
</dependency>
<dependency>
    <groupId>io.github.kostack</groupId>
    <artifactId>fixtures</artifactId>
    <version>VERSION</version>
</dependency>
<!-- Optional: enables the `data-fixtures:load` shell command -->
<dependency>
    <groupId>org.springframework.shell</groupId>
    <artifactId>spring-shell-starter-jni</artifactId>
    <version>4.0.3</version>
</dependency>
```

Versions for `kotlinx-coroutines-core` come from your application's Spring Boot dependency management. A more specific starter, such as WebFlux, can supply the Spring dependencies instead of `spring-boot-starter`.

The library brings in only Datafaker (exposed as API). Spring context, Boot auto-configuration, Kotlin coroutines and Spring Shell are `compileOnly`; applications supply these through their own dependencies. Spring Shell is optional: without it on the classpath, the fixture manager still works but the `data-fixtures:load` command is not registered.

## Auto-configuration

Spring Boot discovers `AutoConfiguration` automatically. It registers a `ReferenceRepository`, unless the application already provides one, and a `DataFixtureManager` that collects the application's `AbstractFixture` beans in `@Order` order. A `Purger` bean is optional; it is only needed to drop the database.

When the manager and Spring Shell command API are available, auto-configuration also registers `LoadFixturesCommand`, unless the application already provides that bean. Shell discovers its `@Command` method on the registered bean; the command class does not need `@Component` or component scanning. Application fixtures and purgers still need to be registered as beans, for example with `@Component`.

Progress is logged at `INFO` through Spring's logging (`io.github.kostack.fixtures.DataFixtureManager`), so it follows your application's logging configuration.

---

## Core Concepts

| Component             | Description                                                    |
|-----------------------|----------------------------------------------------------------|
| `AbstractFixture`     | Base class for all fixtures. Implement `load()` to set up data |
| `DataFixtureManager`  | Orchestrates fixture loading, database purging, and events     |
| `ReferenceRepository` | In-memory store for sharing objects between fixtures           |
| `Purger`              | Optional interface for clearing the database before loading    |
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
        val org = referenceRepository.getReference<Organization>("acme-org")
        projectRepository.save(Project(name = "Alpha", organization = org))
    }
}
```

> **Note:** Fixture loading is sequential, so load order matters. Ensure the fixture that sets a reference is loaded before the one that reads it, for example by using Spring's `@Order` on fixture classes (lower values load first).

`ReferenceRepository` uses a `ConcurrentHashMap` for concurrent reads and writes. Registering a name is atomic: if concurrent callers register the same name, one succeeds and the others receive `IllegalArgumentException`. Existing values are preserved. `getReference<T>(name)` returns the reference as `T` and throws `IllegalStateException` if the name does not exist or the stored value is not a `T`. Java callers can use `getReference(name, Type.class)`. Stored objects themselves must be safe for any concurrent use by the application.

Each `loadFixtures()` call resets all references before purging or loading. Run fixture-loading operations one at a time: the thread-safe map does not isolate overlapping runs, and a reset can clear another run's references.

---

## Implementing a Purger

Implement the `Purger` interface to clear your database before fixtures load. It is optional: without one, fixtures still load, but dropping the database fails with `IllegalStateException`.

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
class MyIntegrationTest @Autowired constructor(
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

To only drop the database without loading fixtures (a suspend function, like `loadFixtures`):

```kotlin
dataFixtureManager.dropDatabase()
```

---

## Loading Fixtures via Shell Command

With Spring Shell on the classpath, the library registers the Spring Shell 4 command `data-fixtures:load`. The `dropDatabase` option defaults to `false` and supports both `--dropDatabase` and `-d`:

```shell
# Load fixtures without dropping the database (default)
shell:> data-fixtures:load

# Drop the database, then load all fixtures
shell:> data-fixtures:load --dropDatabase true
shell:> data-fixtures:load -d true
```

When launching an application that uses Shell's JLine terminal on Java 25, enable native access for its JVM:

```shell
java --enable-native-access=ALL-UNNAMED -jar application.jar
```

For IDE launches, add `--enable-native-access=ALL-UNNAMED` to the run configuration's VM options. This project's Gradle test task already includes the flag.

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
│   ├── UserRepository.kt
│   ├── Post.kt
│   └── PostRepository.kt
└── test/kotlin/com/example/
    ├── fixtures/
    │   ├── MongoPurger.kt
    │   ├── UserFixture.kt
    │   └── PostFixture.kt
    └── UserIntegrationTest.kt
```

`MongoPurger.kt` is the purger from [Implementing a Purger](#implementing-a-purger); it is needed because the test loads fixtures with `dropDatabase = true`.

**UserFixture.kt**

```kotlin
import io.github.kostack.fixtures.AbstractFixture
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(1)
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
import io.github.kostack.fixtures.AbstractFixture
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(2) // runs after UserFixture, which sets "default-user"
class PostFixture(
    private val postRepository: PostRepository,
) : AbstractFixture() {

    override suspend fun load() {
        val user = referenceRepository.getReference<User>("default-user")
        postRepository.save(Post(title = faker.lorem().sentence(), author = user))
    }
}
```

**UserIntegrationTest.kt**

```kotlin
import io.github.kostack.fixtures.DataFixtureManager
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class UserIntegrationTest @Autowired constructor(
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

## Development

```shell
./gradlew check
```

`check` runs the tests, enforces 60% JaCoCo coverage and runs the Spotless/ktlint check. Run `./gradlew spotlessApply` to fix formatting. `assemble` and `test` install the `scripts/pre-commit` git hook: if the formatting check fails, it runs `spotlessApply` and aborts the commit so you can stage the fixes.

---

## License

MIT
