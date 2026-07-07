# Bookie Agent Guide

This file contains general instructions for coding agents. Tech-stack-specific guidance is in:
- **Backend (Java/Spring)**: Guidelines in this file
- **Frontend (React)**: See `frontend/AGENTS.md`

Architecture and system details are in `ARCHITECTURE.md`.

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
  - Use `java.time` (e.g. `Year.now()`) for date/time defaults instead of hardcoded values
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

## Testing Requirements

- Meaningful automated tests must be added or updated for every production code change; do not leave behavior changes untested
- Aim for 100% coverage on all new/changed code paths (including branches); if 100% is not practical, document why and still maximize coverage
- Controller tests use `@WebMvcTest` + `@MockitoBean` (not the deprecated `@MockBean`)
- Service tests use `@ExtendWith(MockitoExtension.class)`
- Group tests by method under `@Nested` inner classes (e.g. `class GetRentalEmails { ... }`)
- After any Java production code change, tests must be written or updated before the task is considered complete — this is non-negotiable
- Every new or changed code path must have 100% branch coverage; untested branches are not acceptable
- Every new service, config, or utility class must have a corresponding `*Test.java` file
- Tests are written after the production code change is complete; never skip or defer them to a follow-up task


## Documentation Maintenance

- After any change to setup steps, environment variables, tech stack, or project structure, update `README.md` to match
- Keep architecture/system details in `ARCHITECTURE.md`

## Commit Messages

- Do not add a `Co-authored-by: Copilot ...` trailer to commit messages unless the user explicitly asks for it.
