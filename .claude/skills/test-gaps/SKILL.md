# Test Gaps Skill

Identify untested code paths and generate tests to improve coverage.

## Trigger

Use this skill when:
- Asked to "improve test coverage"
- Before shipping a feature
- Coverage report shows gaps
- New code was added without tests

## Workflow

### 1. Generate Coverage Report

```bash
# Add Kover plugin if not present, then:
./gradlew koverHtmlReport

# Report locations:
# server/build/reports/kover/html/index.html
# shared/build/reports/kover/html/index.html
```

### 2. Parse Coverage Data

Extract from the HTML/XML report:
- Files with < target coverage
- Uncovered lines and branches
- Methods with 0% coverage

### 3. Prioritize Gaps

Priority order:
1. **Critical paths**: Authentication, encryption, rate limiting
2. **Route handlers**: API endpoints
3. **Business logic**: Core domain functions
4. **Edge cases**: Error handling, boundary conditions

### 4. Generate Tests

For each gap:

```kotlin
// Template for route test
@Test
fun `test [route description]`() = testApplication {
    // Setup
    val client = createClient { ... }

    // Execute
    val response = client.post("/v1/endpoint") {
        contentType(ContentType.Application.Json)
        setBody("""{"field": "value"}""")
    }

    // Verify
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<ResponseDto>()
    assertEquals(expected, body.field)
}
```

### 5. Verify Coverage Improved

```bash
./gradlew koverHtmlReport
# Check new coverage percentage
```

## Coverage Targets

| Module | Target | Critical Areas |
|--------|--------|----------------|
| server | 80%+ | Routes, RateLimiter, Store |
| shared | 90%+ | DTOs, CryptoBridge interfaces |
| android | 60%+ | ViewModel, Repository |

## Test Generation Patterns

### Route Handler Tests
```kotlin
// Happy path
@Test fun `POST /v1/users creates user successfully`()

// Validation errors
@Test fun `POST /v1/users returns 400 for invalid email`()

// Authorization
@Test fun `POST /v1/users returns 401 without token`()

// Rate limiting
@Test fun `POST /v1/users returns 429 when rate limited`()
```

### Business Logic Tests
```kotlin
// Normal operation
@Test fun `processMessage encrypts and stores`()

// Edge cases
@Test fun `processMessage handles empty content`()
@Test fun `processMessage handles max size content`()

// Error conditions
@Test fun `processMessage throws on invalid conversation`()
```

## Files to Check

Priority files for coverage:
- `server/src/main/kotlin/*/routes/*.kt`
- `server/src/main/kotlin/*/store/*.kt`
- `server/src/main/kotlin/*/service/*.kt`
- `shared/src/commonMain/kotlin/*/dto/*.kt`
