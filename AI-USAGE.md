# AI-USAGE.md

## AI Assistance Disclosure

This project was developed with the assistance of **Antigravity**, an agentic AI coding assistant designed by Google DeepMind. We disclose the usage of AI in this repository in compliance with the hackathon rules.

### What the AI was used for

1. **Bug Resolution**:
   - Resolved Hibernate `LazyInitializationException` inside `ReminderDecisionService.evaluate` by refactoring the pipeline to fetch database entities within a `@Transactional` block using reminder IDs rather than raw entity references.
   - Diagnosed and resolved Spring Boot database connectivity issues, ensuring smooth integration with the local MySQL server instance.

2. **CR-2026/11 Implementation (Rolling 7-Day Contact Limit Firewall)**:
   - Designed and generated the `ContactAttempt` entity and the accompanying JPA repository.
   - Built the `ContactLimitService` to encapsulate regulatory limit calculation, next-permitted-time logic, and history tracking.
   - Structured the `ContactLimitResult` DTO to securely carry data between services and controllers.
   - Injected the firewall as Stage 2 inside the `ReminderDecisionService` pipeline.
   - Integrated the pre-send recording logic within `NotificationService`.
   - Exposed status endpoints inside `AdminController` (`/api/admin/residents/{userId}/contact-limit`).
   - Extended the Admin Dashboard frontend UI in `admin.html` with a modern, glassmorphic layout highlighting the remaining rolling contacts, contact histories with DELIVERED/FAILED badges, and next permitted times.

3. **Automated Testing**:
   - Generated unit and integration tests inside `ContactLimitServiceTest.java` covering all 11 regulatory scenarios (e.g. rolling windows, failed attempts counting, multiple channel limits, race-condition protection via pessimistic read locks).

4. **Documentation**:
   - Co-authored `DECISIONS.md` outlining architectural decisions, regulatory boundaries, and trade-offs.
   - Drafted instructions inside `README.md` for building and executing the system.

---

### Human Oversight and Review

While code generation and structural changes were facilitated by the AI assistant, the human developer has:
- Guided the architectural design to fit within the existing project constraints.
- Reviewed all files generated/modified for compliance with Java, Spring Boot, and HTML/CSS/JS best practices.
- Verified and validated that all unit tests compile and pass.
- Manually run the application locally on port 8082, executing scenario validations and ensuring the visual layout is consistent with a professional aesthetic.
