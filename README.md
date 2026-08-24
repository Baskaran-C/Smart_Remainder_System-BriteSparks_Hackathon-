# Smart Appointment Reminder System

A production-ready Spring Boot application designed to manage automated user reminder lifecycles, decision logs, and effectiveness metrics while strictly enforcing regulatory guidelines.

---

## 🌟 Key Features

1. **Regulatory Rolling 7-Day Contact Limit (CR-2026/11)**:
   - Limits outbound contact attempts to a maximum of **2 in any rolling 7-day period** across all channels.
   - Restricts contacts at the **resident level** (not per appointment or channel).
   - Counts failed notification attempts (counts at attempt-time, not delivery).
   - Logs `WITHHELD` decisions separately for audit, without sending or creating fake contact attempts.
   - Prevents race conditions with concurrent sends using **pessimistic locking**.

2. **Smart Reminder Decision Engine**:
   - Multi-stage pipeline checking: Appointment Status → Contact Limit Firewall → Time Check → Harassment Firewall → Previous Response Cooldown → Active Time Window → Location Check → Conflict Check.
   - Pushes real-time updates to the dashboard via WebSockets.

3. **Admin Dashboard**:
   - Modern, professional, glassmorphic dark/light UI.
   - High-fidelity metrics: No-Show Rate, Delivery Rate, Seen Rate, and Confirm Rate.
   - Interactive tracking panel showing details per resident: rolling 7-day contacts, next permitted time, decision timelines, and detailed logs.

---

## ⚙️ Prerequisites

- **Java**: Java 21
- **Database**: MySQL Server (running locally on port 3306)
- **SMTP Server**: Brevo (configured with key in properties)

---

## 🚀 Setup & Installation

### 1. Database Setup
Make sure MySQL is running. Create the database schema:
```sql
CREATE DATABASE remaindersys;
```

### 2. Properties Configuration
Open `src/main/resources/application.properties` and verify/adjust your MySQL credentials:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/remaindersys
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD
```

### 3. Build the Application
Use the Maven wrapper to build the project and compile dependencies:
```bash
./mvnw clean package
```

### 4. Run the Tests
Run the comprehensive test suite (including the 11 new regulatory test cases):
```bash
./mvnw test
```

### 5. Run the Application
Start the Spring Boot server:
```bash
./mvnw spring-boot:run
```
By default, the server will start on port `8080` (or `8082` if configured). You can access the UI at:
- **Admin Dashboard**: `http://localhost:8080/admin.html`
- **Book Appointment**: `http://localhost:8080/book.html`
- **Home**: `http://localhost:8080/index.html`

---

## 🧪 Regulatory Verification Scenario

To demonstrate compliance with the **CR-2026/11** rules, perform the following:

1. **Resident Booking**:
   - Navigate to the **Book Appointment** page (`/book.html`).
   - Create **3 different appointments** for the same resident (e.g., name: `Vadivu`, email: your verified address).

2. **First Evaluation**:
   - The scheduler runs every 2 minutes (or trigger it manually).
   - **Appointment 1**: Will check limits, find `0/2` used, create a `ContactAttempt` (outcome `PENDING`), and attempt dispatch. Upon success, outcome changes to `DELIVERED`.
   - **Appointment 2**: Will check limits, find `1/2` used, create a `ContactAttempt`, and attempt dispatch.
   - **Appointment 3**: Checks limits, finds `2/2` used. The Firewall triggers. The decision is logged as `WITHHELD`. No email is sent, and no `ContactAttempt` is generated. The reminder remains in `WAITING` to be evaluated once the window clears.

3. **Verify Admin Dashboard**:
   - Open the **Admin Dashboard** (`/admin.html`).
   - Click on the resident's appointment row to open the tracking panel.
   - Under **Regulatory Contact Limit**, you will see:
     - Count: `2 / 2 LIMIT REACHED`
     - The history listing 2 attempts.
     - **Next contact permitted**: Displays the exact timestamp when the oldest attempt will fall out of the 7-day window.
   - In the **Decisions Timeline**, you will see a detailed log entry marked as `WITHHELD` with a `REGULATORY BLOCK` badge, citing the timestamps of the previous attempts.

---

## 📂 Project Structure

- `src/main/java/com/example/SmartRemainderSystem/entity/ContactAttempt.java`: Represents outbound contact attempts.
- `src/main/java/com/example/SmartRemainderSystem/service/ContactLimitService.java`: Evaluates rolling counts and Next Permitted Time.
- `src/main/java/com/example/SmartRemainderSystem/service/ReminderDecisionService.java`: Core engine containing the 9-stage pipeline.
- `src/main/java/com/example/SmartRemainderSystem/service/NotificationService.java`: Saves attempts prior to mail dispatch.
- `DECISIONS.md`: Details the architectural trade-offs, scope, and decisions made.
- `AI-USAGE.md`: Standard disclosure of AI model usage.
