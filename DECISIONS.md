# DECISIONS.md

## CR-2026/11 — Rolling 7-Day Contact Limit

---

### What we changed

#### 1. Maximum 2 contacts per resident

We implemented a strict regulatory firewall that limits every resident to
a maximum of **2 outbound contact attempts in any rolling 7-day period**.

The limit is enforced as **Stage 2** in the Reminder Decision Engine,
immediately after the appointment-status check and before any
time-based, location-based, or activity-based logic.

#### 2. Rolling 7-day calculation

The window is calculated as exactly **7 × 24 hours** backward from the
current instant (`NOW() - 7 days`). We deliberately did not implement
calendar-week logic (e.g., Monday-to-Sunday), because the regulatory
language specifies a rolling period, not a fixed period.

This means the window slides forward in time automatically. A contact
made on Tuesday becomes eligible to drop out of the window on the
following Tuesday at the same hour.

#### 3. All channels combined

The contact limit applies across **all notification channels** (EMAIL,
SMS, PUSH) combined. It is not a per-channel limit.
Two email contacts exhaust the same budget as one email and one SMS.

#### 4. Failed outbound attempts count

A `ContactAttempt` record is created in the database **before** the
notification is dispatched. This ensures:

- An SMTP failure does not prevent the attempt from counting.
- A system crash after the record is written still counts.
- The outcome field (`PENDING → DELIVERED / FAILED`) is updated
  after the send, but the count is already committed.

This directly addresses the regulatory requirement:
> "An outbound attempt counts whether delivered, answered, or read."

#### 5. Historical contacts count

The contact-limit check queries the `contact_attempts` table using a
time-window filter (`attempted_at >= NOW - 7 days`). It does not filter
by the date this feature was deployed.

Contacts recorded before this feature was introduced are therefore
counted if they fall inside the current rolling window. There is no
reset of historical data.

#### 6. Contact limit firewall added before notification

The `ContactLimitService.checkLimit()` call runs inside the
`ReminderDecisionService.evaluate()` pipeline **before** the engine
reaches any send decision. If the limit is reached, a `WITHHELD`
decision is recorded and the engine returns early without sending.

#### 7. Withheld decisions are audited

When the firewall blocks a contact:

- A `DecisionLog` record is written with `decision = WITHHELD`.
- The log includes: resident ID, appointment ID, current contact count,
  maximum allowed, all relevant contact timestamps, window start/end,
  and a human-readable reason.
- **No `ContactAttempt` is created** — because nothing was actually sent.
- The reminder is kept in `WAITING` status with a re-evaluation in
  4 hours, so that when the rolling window clears, the resident
  can still receive the reminder.

---

### What we did not change

We did not remove or modify:

- The existing appointment booking system
- The initial reminder scheduling logic
- The active-time window detection (ActivityService)
- The location-aware departure trigger (LocationService)
- The appointment conflict detection (ConflictService)
- The harassment firewall (per-appointment reminder limit)
- The previous-response tracking (SEEN / CONFIRMED / CANCELLED)
- The email notification service (except adding attempt recording)
- The admin dashboard core functionality

All of these remain fully operational. The contact limit firewall
is an additional layer on top of the existing engine.

We did not introduce a separate contact limit per appointment,
per channel, or per contact point. The limit is strictly per resident,
as the regulation specifies.

We did not use any protected characteristics (age, gender, ethnicity,
disability status, etc.) for contact prioritisation or scheduling
decisions. All prioritisation is based on objective appointment factors:
appointment time, location distance, and user activity patterns.

---

### Important implementation distinction

| Event | Record created | Counts toward limit |
|---|---|---|
| Outbound email attempt (success) | `ContactAttempt` (DELIVERED) | YES |
| Outbound email attempt (failure) | `ContactAttempt` (FAILED) | YES |
| WITHHELD by firewall | `DecisionLog` (WITHHELD) | NO |

A `ContactAttempt` represents something that was actually sent
(or attempted to be sent). A `DecisionLog` with `WITHHELD` represents
a decision that prevented a send. They are different records in
different tables for deliberate reasons.

---

### What we would have done differently

If this regulatory requirement had been known at the beginning of the
project, we would have:

1. **Included contact history in the initial data model.** The
   `contact_attempts` table would have been part of the original schema,
   rather than added retrospectively. This would have allowed the
   contact-limit check to be integrated into the reminder architecture
   from day one rather than inserted as an additional stage.

2. **Designed the decision engine with regulatory stages as first-class
   concerns.** The current implementation adds the contact-limit firewall
   as Stage 2 in an existing pipeline. If the requirement had been known
   earlier, the pipeline would have been designed with regulatory gates
   as primary gates rather than secondary additions.

3. **Implemented cross-channel tracking from the start.** The current
   implementation tracks `channel` in `contact_attempts` but only uses
   EMAIL. A multi-channel system (SMS, push) would have been designed
   with shared contact budgets from the beginning.

4. **Built a dedicated regulatory audit log** as a separate concern from
   the general decision log, making it easier to produce evidence for
   regulators without exposing internal engine details.

---

*Document created: 2026-08-23*
*Regulatory reference: CR-2026/11*
*Author: Smart Appointment Reminder System*
