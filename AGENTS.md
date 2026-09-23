# Bookie Agent Guide

This file contains general instructions for coding agents. Tech-stack-specific guidance is in:
- **Backend (Java/Spring)**: Guidelines in this file
- **Frontend (React)**: See `frontend/AGENTS.md`

Architecture and system details are in `ARCHITECTURE.md` (an index) and the topic docs under
`docs/architecture/` — read only the topic doc for the area you are changing.

## Backend Code Style (Java)

- Java code must follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) — this includes always using braces for all block statements, even single-line `if`/`else`/`for`/`while` bodies
- Add comments only for non-obvious WHY — hidden constraints, subtle invariants, or workarounds; never for what the code plainly does
- Use `@Builder` for classes or method calls with 3 or more parameters instead of positional constructors; for JPA entities combine with `@NoArgsConstructor` and `@AllArgsConstructor`
- Use text blocks (triple-quoted strings `"""..."""`) for any multi-line string literal
- Prefer standard library and framework utilities over hand-written equivalents:
  - Use `Optional` for null-safe chaining instead of explicit null checks
  - Use `Comparator.comparing(...).reversed()` instead of manual sort lambdas
  - Use pre-compiled `Pattern` constants instead of inline `String.replaceAll`
  - Use Spring Data JPA derived query methods (e.g. `findBySourceIdIn`) instead of manual loops
  - Use `java.time` with the injected `Clock` (e.g. `Year.now(clock)`) for date/time defaults instead of hardcoded values
  - Use `CollectionUtils.emptyIfNull(collection)` (Apache Commons Collections 4) to guard against null collections before streaming, instead of a separate `if (collection == null || collection.isEmpty())` guard
  - Use `StringUtils.isBlank(s)` / `StringUtils.isNotBlank(s)` (Apache Commons Lang 3) for null-safe blank checks instead of `s == null || s.isEmpty()` or `s != null && !s.isEmpty()`

## AI Tool Descriptions (`@Tool`)

When writing `@Tool` descriptions for `EmailParserTools` or any future AI tool:

- **Express when to call the tool**, not what it does internally. The description is read by the model to decide whether to invoke the tool — it must answer "should I call this now?"
- **Use "Use this when..." or "Use this to..."** as the opening, followed by the condition that makes calling it appropriate.
- **Describe fallback order explicitly** when a tool is part of a lookup chain, e.g. "Use this when `findPayerByAccountNumber` returns empty."
- **Do not put tool-calling instructions in the system prompt.** The system prompt should express intent and define output fields only. Each tool description is self-contained.
- **State what the caller should do with the result**, e.g. "Use the top-ranked name exactly" or "Use the exact enum key returned."

## System Prompt Guidelines

When writing system prompts that work with tools:

- **Express clear intent and output fields only.** The prompt should tell the model what to produce, not how to produce it. Instructions like "call tool X, then if empty call tool Y" belong in the tool descriptions, not here.
- **Make tool use the logical next step.** Frame fields the model cannot know without a lookup (e.g. `payerName`, `propertyName`, `category`) as requiring tool resolution: "Use the available tools to resolve X — do not guess values that a tool can look up."
- **Do not name specific tools in the system prompt.** If the prompt says "call `findPropertyByAccount`", the model treats it as a script and skips tools when the script feels complete. Instead say "resolved via tools" and let the tool descriptions guide which ones to call.

## Backend Testing

### Requirements

- Every Java production change ships with meaningful new or updated tests in the same task, written once the production change is complete; never defer them to a follow-up
- Every new or changed code path, including each branch, must be covered (100% branch coverage); if a branch is genuinely impractical to test, say why in your summary
- Every new service, config, or utility class gets a corresponding `*Test.java`

### Conventions

- **Assertions:** AssertJ `assertThat(...)` only — not JUnit `Assertions` or Hamcrest (MockMvc `jsonPath(..., is(...))` matchers are the one exception)
- **Structure:** group tests by method under `@Nested` inner classes (e.g. `class GetRentalEmails { ... }`); name tests as camelCase behavior sentences (`returnsEmptyPageWhenNoMessagesMatch`); do not mass-rename existing `condition_result` names
- **Test type by layer:**
  - Services and plain classes: `@ExtendWith(MockitoExtension.class)` with constructor-built subjects
  - Controllers: `@WebMvcTest` + `@MockitoBean` (never the deprecated `@MockBean`)
  - Repositories and JPA queries: `@DataJpaTest` + `@Import(ClockConfiguration.class)` (the entity audit listeners need the `Clock` bean, which JPA slices don't load)
  - `@SpringBootTest` only when the behavior depends on cross-module wiring
  - Module boundaries: extend the ArchUnit rules in `ModuleBoundaryTest`
- **Mockito:** keep the default strict stubs. Never add class-level `@MockitoSettings(strictness = LENIENT)` — it hides dead stubs; use `lenient()` on the one shared stub that needs it. Do not stub what the code under test never calls
- **Time:** production code never calls a no-arg `now()` (`ClockUsageTest` enforces this). Inject the shared `Clock` bean (`config/ClockConfiguration`) and call `LocalDate.now(clock)` etc.; JPA entity timestamps come from the module's audit listener (`IntakeAuditListener`, `LedgerAuditListener`). Tests pass `Clock.fixed(...)` — a real one, never a Mockito mock — and assert exact dates
- **Async:** wait with Awaitility, never `Thread.sleep`
- **Test data:** use entity/DTO builders inline; extract a private factory method in the test class once a fixture repeats

### Test tiers

| Tag | Task | Use for |
| --- | --- | --- |
| _(none)_ | `./gradlew test` | Default fast loop; run before finishing any backend task |
| `@Tag("slow")` | `./gradlew slowTest` | Tests that wait on real timers, polling, or restarts (JobRunr intake, shadow restore). Run when touching those areas |
| `@Tag("llm")` | `./gradlew llmTest` | Real LLM calls; on demand only |

`./gradlew check` and CI run `test` + `slowTest`. Format with `./gradlew spotlessApply` under JDK 21 (google-java-format fails on newer JDKs).

## Documentation Maintenance

- After any change to setup steps, environment variables, tech stack, or project structure, update `README.md` to match
- Keep architecture/system details in the matching `docs/architecture/*.md` topic doc and keep `ARCHITECTURE.md` as a short index; add a new topic doc (and index row) rather than growing one past ~20 KB

## Issue Tracking

- This repository is public. Security-sensitive work (vulnerabilities, hardening, data-exposure risks) is tracked in the private repo `jinloes/bookie-private`, not in this repo's issues.
- When planning or picking up work, check both `jinloes/bookie` and `jinloes/bookie-private` issues (e.g. `gh issue list --repo jinloes/bookie-private`).
- File new security-sensitive issues in `jinloes/bookie-private`, and never copy their details into public issues, PRs, commit messages, or docs in this repo until the fix has shipped.

## Commit Messages

- Do not add a `Co-authored-by: Copilot ...` trailer to commit messages unless the user explicitly asks for it.
- Never run `git commit` (or otherwise create a commit) unless the user explicitly asks you to commit in that turn. Making code changes does not imply permission to commit them.
