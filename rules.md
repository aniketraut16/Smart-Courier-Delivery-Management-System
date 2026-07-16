# Smart Courier AI Development Rules

## Purpose

You are assisting in the development of the Smart Courier microservices project.

Your goal is to produce production-quality Spring Boot code while preserving the project's architecture and conventions.

---

# 1. Never modify project configuration

DO NOT modify any of the following unless I explicitly ask.

- pom.xml
- parent pom.xml
- application.yml
- application.properties
- Maven Wrapper
- Docker files
- project structure
- package names

If a task requires changing one of these files, STOP and ask for confirmation first.

Never silently add dependencies.

Never silently update dependency versions.

Never silently modify plugins.

---

# 2. Read documentation first

Before implementing anything, read the relevant documentation from the project.

Examples:

- architecture.md
- database.md
- api-contracts.md
- project-plan.md
- service-design.md
- security.md

Only read documents that are relevant to the current task.

Do not ignore project documentation.

Documentation is always the source of truth over assumptions.

---

# 3. Never assume requirements

If something is unclear,

Ask.

Never invent APIs.

Never invent database tables.

Never invent DTO fields.

Never invent business logic.

---

# 4. Respect Microservice Boundaries

Never access another service's database.

Communication between services must happen only through APIs or clients.

Never create cross-database foreign keys.

Never duplicate ownership of data.

---

# 5. Keep Changes Minimal

Only change files required for the requested task.

Do not refactor unrelated code.

Do not rename files.

Do not reorganize packages.

Do not change formatting of unrelated files.

---

# 6. Follow Existing Code Style

Always match the existing codebase.

Use existing naming conventions.

Use existing package structure.

Use existing exception handling.

Use existing logging style.

Use existing DTO style.

Consistency is more important than personal preference.

---

# 7. Do Not Generate Unrequested Code

Do not create

- controllers
- services
- entities
- repositories
- DTOs
- configs
- tests

unless explicitly requested.

Generate only what is asked.

---

# 8. Security Rules

Never hardcode

- passwords
- JWT secrets
- API keys
- tokens

Always use configuration properties.

Never disable security for convenience.

---

# 9. Database Rules

Never modify database schema unless explicitly requested.

Never rename columns.

Never remove constraints.

Never change relationships.

Never change IDs.

Respect the existing database architecture.

---

# 10. Dependency Rules

Never add a dependency without permission.

Never upgrade versions.

Never downgrade versions.

Never replace libraries.

If a dependency is required,

Explain why and ask first.

---

# 11. Spring Boot Rules

Follow standard Spring Boot conventions.

Do not invent project structure.

Do not create unnecessary configuration classes.

Use constructor injection.

Avoid field injection.

Use Lombok only where already used.

---

# 12. Logging

Use SLF4J.

Never use System.out.println().

Log meaningful events only.

Do not log secrets.

---

# 13. Error Handling

Reuse existing exceptions.

Reuse existing GlobalExceptionHandler.

Do not introduce multiple exception styles.

---

# 14. Code Quality

Keep methods small.

Keep classes focused.

Avoid duplication.

Prefer readability over cleverness.

Write production-quality code.

---

# 15. Output Rules

Return only the required code.

Do not explain basic Spring concepts.

Do not rewrite entire files if only a few lines change.

Prefer showing minimal diffs.

If multiple approaches exist,

Choose the simplest production-ready solution.

---

# 16. Before Finishing

Verify:

- project compiles
- imports are correct
- package names are correct
- no unused code
- no TODOs
- no placeholders
- no compilation errors

---

# 17. When Unsure

Do not guess.

Ask for clarification.