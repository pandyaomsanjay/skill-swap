# 🔄 Skill Swap

> **Learn • Share • Exchange**

Skill Swap is a peer-to-peer Android application designed to connect people who want to **teach their skills, learn new skills, and exchange knowledge with others**.

Users can create profiles, add skills, upload learning resources, discover other users, exchange skills through a full trade-request workflow, communicate through real-time chat, share skill videos and live sessions, create playlists, provide ratings and feedback, and receive push notifications and scheduled reminders.

---

## ✨ Key Features

* 🔐 User Authentication
* 👤 User Profiles
* 🎯 Skill Management
* 📦 Multi-Type Resource Uploads (Video, Notes, PDF, Document, Course, Live Session)
* 🔄 Skill Exchange / Trade Requests with Scheduling & Counter-Scheduling
* 🗂️ 5-Tab Trade Dashboard (Incoming, Sent, Active, Completed, Closed)
* ⏰ Meeting Reminders & Notifications (WorkManager)
* 💬 Real-Time Chat
* 🛡️ Encrypted Chat with 3-Stage Ban Escalation (24h → 48h → Permanent) & Admin Unban
* 🎥 Skill Video Sharing
* 📑 Skill Playlists
* ⭐ Ratings & Feedback
* 🔔 OneSignal Push Notifications
* 📍 Location Support
* 🔎 Skill Discovery
* 🌐 Multi-Language Support
* 🌙 Dark Mode
* 💳 Credit Purchase System
---

# 📱 User Features

## 🔐 Authentication

Users can securely access Skill Swap through the authentication system.

### Features

* User Registration
* User Login
* Firebase Authentication
* Google Sign-In
* OTP Verification
* Forgot Password
* Password Reset
* Change Password
* Account Management

---

## 👤 User Profile

Users can create and manage their personal profiles.

### Profile Information

* Name
* Email
* Profile Picture
* Location
* Skills Offered
* Skills Requested
* Rating
* Points
* Number of Trades
* Number of Skills

Users can update their profile information whenever required, and can add new **resources** (notes, PDFs, courses, live sessions, etc.) directly from the **+ Add Resource** button on their Profile screen, which opens `AddResourceActivity`.

---

## 🎯 Skill Management

Users can add and manage the skills they want to teach or share.

### Skill Features

* Add a New Skill
* View Skills
* Explore Skills
* Browse Skill Categories
* View Skill Details
* Upload Skill Videos
* Discover Skills Offered by Other Users

### Skill Categories

* 💻 Technology
* 🎨 Arts
* ⚽ Sports
* 🏠 Home
* 📚 Education
* 🌱 Lifestyle

---

## 📦 Resource Upload

In addition to skill videos, users can upload multiple types of learning resources from their profile via `AddResourceActivity`:

* 🎥 Videos
* 📝 Notes (text-based)
* 📄 PDFs
* 📃 Documents
* 🎓 Courses
* 🖥️ Live Teaching Sessions
* 📦 Other Resources

### Resource Upload Features

* Resource type selection
* Title, category, and description fields
* Credit value assignment
* File picker with **25 MB size limit validation**
* Supabase file upload + Firestore resource saving
* Loading and error handling

### Live Teaching Session Support

Live session resources carry extra metadata, stored via `LiveSessionInfo.kt`:

* Platform (e.g. Google Meet, Zoom)
* Online or offline mode
* Duration (minutes)
* Meeting link
* Physical location (for offline sessions)

---

# 🔄 Skill Exchange / Trading

The core functionality of Skill Swap is exchanging knowledge and resources between users through a complete trade-management system.

### Trade Process

```text
Browse Skill / Resource
        ↓
Select Resource to Offer
        ↓
Propose Date & Time
        ↓
Send Trade Request
        ↓
Receiver: Accept / Reject / Counter-Schedule
        ↓
Requester Accepts Counter-Schedule (if any)
        ↓
Schedule Finalized
        ↓
Complete / Cancel Trade
        ↓
Rating & Feedback
```

### Trade Status Lifecycle

```text
Pending → Counter Proposed → Accepted → Schedule Finalized → Completed
                                              ↓
                                   Cancelled / Rejected / Expired
```

### My Trades — 5 Sections

| Tab | Description |
|---|---|
| **Incoming** | Trade requests received from other users |
| **Sent** | Trade requests sent by the current user |
| **Active** | Pending and currently active trades |
| **Completed** | Successfully completed trades |
| **Closed** | Cancelled, rejected, or expired trades |

Active and Completed tabs load trades for both requester and receiver roles, merge and deduplicate results via real-time Firestore listeners, sort client-side, and show empty states when there are no trades.

### Resource Selection for Trades

Before sending a trade request, users pick which of their own resources to offer via `SelectResourceActivity`, which shows resource title, description, type, duration, and thumbnail, with empty/error states and a retry option.

### Trade Data Includes

* Requester & Receiver
* Requested Resource & Offered Resource details
* Proposed schedule / counter-schedule
* Trade status
* Completion & cancellation timestamps
* Live session data (if applicable)

### Main Trade Components

```text
TradeRequestActivity.kt
TradeDetailActivity.kt
TradeManager.kt
Trade.kt
TradeStatus.kt
TradeValidator.kt
SelectResourceActivity.kt
MyTradesActivity.kt
```

---

## ⏰ Meeting Reminders & Notifications

Scheduled reminders for finalized trade meetings are powered by **WorkManager**:

* Reminder **24 hours** before a finalized meeting
* Reminder **1 hour** before a finalized meeting
* Delivered via Android notification channels
* Reminder records stored in Firestore
* Reminders automatically cancelled if a trade is cancelled or rejected
* Duplicate-reminder prevention using unique WorkManager task names

### Main Components

```text
MeetingReminderScheduler.kt
MeetingReminderWorker.kt
```

---

# 💬 Real-Time Chat

Skill Swap provides real-time communication between users.

Users can:

* Start conversations
* Send messages
* Receive messages
* View previous messages
* Track unread conversations
* Communicate about skill exchanges

Chat functionality is implemented using **Cloud Firestore**.

### Chat Structure

```text
chats
└── {conversationId}
    └── messages
        └── {messageId}
```

---

---

## 🛟 Chat Recovery (Consent-Based)

Skill Swap includes a chat recovery feature that allows users to request administrative assistance with a specific conversation.

- **Recovery Request:** Users can submit a chat recovery request with an optional screenshot.
- **Admin Dashboard:** Administrators can access recovery requests through a dedicated recovery icon.
- **Request Review:** Admins can view submitted requests and review the associated conversation.
- **User Consent:** Temporary chat access is granted only with the user's explicit consent.
- **Temporary Access:** Administrators can access only the specific conversation authorized for recovery.
- **End Chat:** Administrators can end the recovery session to revoke temporary access.

---

## 🛡️ Chat Moderation, Encryption & Ban Escalation

Chat abuse is handled by a Supabase Edge Function, `chat-gateway`. Messages stay in **Firestore**; moderation state lives in **Supabase Postgres**. Enforcement is **server-side** — the Android app only displays state, so a tampered client cannot bypass a ban.

### Escalation Ladder

| Stage | Warnings in stage | Ban | Duration |
| ----- | ----------------- | --- | -------- |
| 1 | 5 | 24-hour temporary | 24h |
| 2 | 3 | 48-hour temporary | 48h |
| 3 | 2 | **Permanent** | Never |

* Each stage's counter restarts at **0** after the previous temporary ban expires.
* Warnings received while already banned are **not counted**.
* Permanent bans can only be removed by an admin.
* Thresholds and durations are configurable in `chat_config`. The legacy 90-day warning expiry is disabled.

### User-Side Behaviour

| Situation | What the user sees |
| --------- | ------------------ |
| Warnings 1–4 (Stage 1) | "Warning N of 5" dialog + **Appeal this decision** |
| 5th warning (Stage 1) | Input disabled, live countdown, "24h left" |
| 3rd warning (Stage 2) | Input disabled, live countdown, "48h left" |
| 2nd warning (Stage 3) | Permanent ban dialog, no countdown, "contact an administrator" |
| Banned (any type) | Can still **read** conversations; cannot send in any chat |

**Report** on another user's message queues it for admin review (recipient only).

### Admin Side

**Admin Dashboard → 🚫 → Banned users** (`AdminBannedUsersActivity.kt`). Each row shows username/uid, total warnings and stage, ban type (None / 24h / 48h / Permanent), ban start, expiry, reason, and an **Unban user** button (any stage). Filter: **Permanent bans** (default) or **+ temporary bans**.

**Unban effect:** user can chat immediately, ladder restarts at Stage 1, ban history is preserved, one audit row is written. Admin status is verified server-side from `users/{uid}.userType` in Firestore, never trusted from the client; unban is idempotent (no second audit row).

### How Enforcement Works

`chat-gateway` → `chat_apply_send` (Postgres):

1. Verifies the Firebase ID token.
2. Applies the escalation rule inside a row lock (`FOR UPDATE`) — parallel messages cannot skip a stage.
3. Encrypts the message (AES-256-GCM) only if allowed.
4. Writes to Firestore with a service account.

The client `message_id` is the idempotency key, so retries never add a second warning. Appeals and reports use database tables plus a resolve request from an admin account (`userType == "admin"`).

### Supabase Database

| Table | Purpose |
| ----- | ------- |
| `chat_config` | Policy row (id=1): stage thresholds and durations |
| `chat_moderation` | Per-uid warnings, stage, ban level, blocked-until, reason |
| `chat_events` | One row per violation (`pending` / `appealed` / `overturned` / `confirmed`) |
| `chat_review_queue` | Appeals and reports awaiting admin review |
| `chat_ban_history` | One row per ban, closed on expiry or unban |
| `chat_admin_audit` | One row per admin action (unban) |

| Function | Role |
| -------- | ---- |
| `chat_apply_send` | Escalation engine (row-locked, idempotent) |
| `chat_submit_appeal` | Marks event appealed, queues for review |
| `chat_resolve_case` | Admin overturns/confirms (cannot remove a permanent ban) |
| `chat_admin_unban` | Resets ladder to Stage 1, writes audit row |
| `chat_list_banned` | Feeds the Banned-users screen |
| `chat_maintenance` | Closes expired temporary bans on schedule |

**Security:** RLS on with no client policies; `REVOKE ALL` on all chat functions from `anon` and `authenticated`. Firestore rules block direct message creation (`allow create: if false`), so the edge function is the only way to send. Clients also cannot write the last-message preview, and delete-for-everyone clears `ciphertext` and `iv`.

### Setup Summary

Full commands are in `SETUP_SUPABASE.md`.

1. Run `001_chat_moderation.sql` in the Supabase SQL Editor.
2. Run `002_chat_bans.sql`.
3. Run the fix-up SQL (adds `event_id` unique constraint on `chat_events`).
4. Enable `pg_cron` and schedule the daily `chat_maintenance` job.
5. Download a Firebase service-account key (**Firebase Console → Project settings → Service accounts**).
6. Set three Supabase secrets:

   * `CHAT_MASTER_KEY` — generate and back it up first.
   * `FIREBASE_PROJECT_ID`
   * `FIREBASE_SERVICE_ACCOUNT`

7. Deploy the function:

   ```bash
   supabase functions deploy chat-gateway --no-verify-jwt
   ```

   `--no-verify-jwt` is required because the app sends Firebase tokens.

8. If using the Supabase Dashboard instead of the CLI:

   * **Edge Functions → Deploy a new function → Via Editor**.
   * Name it exactly `chat-gateway`.
   * Paste `index.ts` into the main file.
   * Add the other 5 files with exact names: `auth.ts`, `crypto.ts`, `firestore.ts`, `moderation.ts`, `policy.ts`.
   * Click **Deploy**, then turn **OFF** “Verify JWT” in Settings / Details.
   * Save the 3 secrets.

9. Rollout order:

   * Deploy the backend first.
   * Release the new app and force-update it.
   * Publish the new Firestore rules **last** (**Firebase Console → Firestore → Rules**). Publishing earlier breaks sending for everyone on an old app version.

### Files

| Type | Files |
|---|---|
| Edge function (`supabase/functions/chat-gateway/`) | `index.ts`, `auth.ts`, `crypto.ts`, `firestore.ts`, `moderation.ts`, `policy.ts` (6 files — do not add a 7th) |
| SQL | `supabase/migrations/001_chat_moderation.sql`, `002_chat_bans.sql` |
| New Android | `ChatCrypto.kt`, `ModerationClient.kt`, `ModerationMessages.kt`, `AdminBannedUsersActivity.kt`, `activity_admin_banned_users.xml`, `item_banned_user.xml` |
| Replaced Android | `Chatmessage.kt`, `ChatActivity.kt`, `ChatListActivity.kt`, `NewChatActivity.kt`, `data_extraction_rules.xml`, `backup_rules.xml` |
| Updated Android (admin entry) | `AdminDashboardActivity.kt` (🚫 entry), `activity_admin_dashboard.xml` (🚫 button), `AndroidManifest.xml` (new activity) |
| Firestore rules | `firestore.rules` (only the `CHATS` section changed; ban state lives entirely in Postgres) |

Optional tests (can be skipped): `ChatCryptoTest.kt`, `ModerationMessagesTest.kt`, `KeystoreKeyWrapperTest.kt`, `sql.test.mjs`, `crypto-interop.test.ts`. The `_alternative_firebase_functions` folder is the old Firebase version and is unused.

### Deployment Check

* All 6 function files present, none empty, each ending with `}` or `);`.
* Function name is exactly `chat-gateway`.
* Open `https://ghrxltlstncjcizyyqfo.supabase.co/functions/v1/chat-gateway/status` — a browser `GET` should return `{"error":"method-not-allowed", ...}`, proving the function is running. `Invalid JWT` or `404` means check the Verify JWT setting and URL.
* Build and release the Android app; leave Firestore rules for last.

### Test Recipe

1. Send `hello` → delivered.
2. Send abusive messages (e.g. `you are an idiot`) to reach 5 warnings → 24-hour ban.
3. After expiry (or advancing the clock), send 3 more → 48-hour ban.
4. After expiry, send 2 more → permanent ban.
5. As admin: **🚫 → Banned users → Unban** → confirm a row in `chat_admin_audit`.

**Test status:** SQL (warnings, bans, retries, cross-chat blocks, expiry, spam, appeals, overturns), the 35 word-filter cases, and edge-function encryption interop passed. **Not tested:** the edge function itself, Firestore rules in the emulator (bracket balance only), and the uncompiled Kotlin — send build errors if any appear.

**Behavior note:** the restriction banner refreshes when a chat opens and after each send; it is not live like the earlier Firestore-listener version.

---

# 🎥 Skill Videos

Users can share videos related to their skills.

### Video Features

* Upload Skill Videos
* View & Play Videos
* Browse Skill Videos
* Create / View Playlists
* **Trade / Swap button** on another user's video, opening `TradeRequestActivity` (hidden on a user's own videos)
* Playlist progress tracking preserved
* Admin Video Management

### Main Components

```text
UploadVideoActivity.kt
VideoPlayerActivity.kt
PlaylistActivity.kt
Resizablevideoview.kt
```

---

# 📑 Skill Playlists

Skill Swap supports video playlists that bundle related skill videos together.

### Playlist Features

* Create and publish playlists
* Browse playlist details
* View thumbnail, category, creator and description
* View video count and total duration
* Preview playlists using demo videos
* Unlock playlists using credits
* Live credit-balance validation
* Transactional playlist purchases
* Per-user video progress tracking
* Progress percentage tracking
* Progress bar completion indicator
* Free access for playlist owners
* Free access for administrators
* View people who purchased a playlist
* Automatic **500-credit reward** after completing 2 full playlists
* OneSignal notifications for playlist purchases and completion

### Playlist Components

```text
PlaylistActivity.kt
PlaylistManager.kt
PlaylistVideoAdapter.kt
AddPlaylistVideoActivity.kt
```

---

# ⭐ Ratings & Feedback

Users can provide feedback after interacting with or completing skill exchanges.

Users can:

* Submit Feedback
* Rate Users
* View Feedback
* Provide Ratings
* Review Previous Feedback

---

# 🔔 OneSignal Push Notifications

Skill Swap uses **OneSignal** for push notifications.

Notifications can be triggered for events such as:

* 🔄 New Trade Requests
* ✅ Trade Status Updates
* 💬 New Chat Messages
* 📑 Playlist Purchases
* 🎓 Playlist Completion
* ⭐ Feedback / Rating Events
* ⏰ Meeting Reminders
* 📢 Important Application Notifications

### Notification Architecture

```text
Skill Swap Android App
        ↓
OneSignal SDK
        ↓
OneSignal Platform
        ↓
Android Push Service
        ↓
User
```

OneSignal provides the notification management and delivery layer. Android push delivery uses Firebase Cloud Messaging (FCM) credentials configured through OneSignal.

Firebase Cloud Functions are **not required** for the notification architecture.

Official documentation:

https://documentation.onesignal.com/docs/en/android-sdk-setup

---

# 📍 Location Support

Skill Swap includes Google Places integration for location-related functionality.

Location functionality can be used to:

* Select locations
* Display user locations
* Assist users in finding relevant people based on location

---

# 🌐 Multi-Language Support

Skill Swap supports multiple languages to make the application accessible and easy to use for users from different regions.

## 🗣️ Available Languages

| Language                    | Code | Status      |
| --------------------------- | ---- | ----------- |
| 🇬🇧 **English**            | `en` | ✅ Supported |
| 🇮🇳 **Hindi (हिन्दी)**     | `hi` | ✅ Supported |
| 🇮🇳 **Gujarati (ગુજરાતી)** | `gu` | ✅ Supported |

## ⚙️ Language Selection

Users can select their preferred language from **two locations**:

### 🔐 Login Page

When a user opens the Skill Swap application and reaches the Login page, the application asks the user to select their preferred language:

**English | हिन्दी | ગુજરાતી**

### 👤 Profile Page

After logging in, users can change their preferred language anytime from **Profile → Language**. The selection is saved and applied automatically across the application.

### 🔧 Implementation

```text
LocaleHelper.kt
```

```text
res/
├── values/
│   └── strings.xml          # English
├── values-hi/
│   └── strings.xml          # Hindi
└── values-gu/
    └── strings.xml          # Gujarati
```

### 🔄 Language Flow

```text
Open Skill Swap → Login Page → Select Language → English / Hindi / Gujarati
        → Login / Continue → Application Opens → Profile → Language
        → Change Language Anytime
```

---

# 🌙 Dark Mode

Skill Swap includes Light and Dark themes.

Users can switch between themes through Settings.

Main components:

```text
SettingsActivity.kt
BaseActivity.kt
```

`BaseActivity` is used to apply theme preferences consistently across application screens.

---
## 💳 Credit Purchase System

SkillSwap includes a simulated credit purchase system that allows users to increase their credit balance by selecting a credit package and completing a demo checkout.

### Features

- **Credit Packages:** Multiple packages with different prices and credit amounts.
- **Custom Checkout:** A dedicated checkout screen displaying the selected package, price, and credits.
- **Simulated Payment Methods:** Users can choose between UPI Simulation and Card Simulation.
- **Instant Credit Updates:** After a successful simulated purchase, the purchased credits are added to the user's existing Firestore balance.
- **Transaction Records:** Purchase details are recorded in Firestore for tracking and administrative review.
- **Purchase Confirmation:** A success screen displays the purchased package, credits added, and updated balance.
- **Idempotent Transactions:** Repeated requests for the same transaction are handled to prevent duplicate credit awards.

> **Note:** Payments are simulated for demonstration purposes only. No real money is charged or transferred.

### Credit Packages

| Package | Price | Credits |
|---|---:|---:|
| Starter | ₹50 | 100 |
| Popular | ₹100 | 220 |
| Value | ₹200 | 500 |
| Pro | ₹500 | 1,400 |

Package information is retrieved from the existing Firestore `creditPackages` collection.

### Payment Workflow

1. The user opens the Credit Store.
2. The user selects a credit package.
3. The app opens the checkout screen.
4. The user selects UPI Simulation or Card Simulation.
5. The app sends the purchase request to the Supabase Edge Function.
6. The backend validates the authenticated user and the selected package.
7. The backend updates the user's existing Firestore credit balance and records the simulated transaction.
8. The app displays the purchase confirmation and updated balance.

### Technology Used

- **Android:** Kotlin and XML
- **Authentication:** Existing SkillSwap authentication system
- **Backend:** Supabase Edge Functions
- **Database:** Firebase Cloud Firestore
- **Payment Processing:** Custom simulated payment flow

### Transaction Records

Simulated purchases are recorded in the Firestore `demoTransactions` collection. These records support purchase tracking and can be used by the Admin Panel to review transaction activity.

Purchase records may include:

- Transaction ID
- User ID and email
- Package ID
- Purchase price
- Credits purchased
- Payment method
- Transaction status
- Purchase timestamp
- Demo transaction indicator

The actual fields depend on the current backend implementation.

### Admin Purchase History

The Admin Panel can provide a centralized view of simulated credit purchases, helping administrators identify who purchased a package, which package was selected, how many credits were awarded, and when the transaction occurred.

Access to purchase records must be restricted to authorized administrators.

### Security and Data Integrity

- Package prices and credit amounts are validated on the backend.
- The backend uses the authenticated user's identity rather than trusting a user ID supplied by the client.
- Existing credit balances are updated instead of creating a separate balance for purchased credits.
- Transaction IDs support idempotent processing to prevent duplicate credit awards.
- Simulated payment processing is controlled by a server-side configuration flag.
- The demo payment gateway is intended for development and demonstration, not real financial transactions.

### Future Enhancement

Integration with a real payment gateway, such as Razorpay, may be considered in a future version. The current simulated checkout remains separate from the existing Razorpay implementation.

# 🏗️ Technology Stack

| Technology                     | Purpose                                                |
| ------------------------------ | ------------------------------------------------------ |
| **Kotlin**                     | Primary programming language                           |
| **Android SDK**                | Android application development                        |
| **AndroidX**                   | Android components                                     |
| **Material Components**        | UI components                                          |
| **Firebase Authentication**    | User authentication                                    |
| **Cloud Firestore**            | Application data & real-time chat                      |
| **Firebase Realtime Database** | Real-time data                                         |
| **Firebase Storage**           | File / media storage                                   |
| **WorkManager**                | Scheduled meeting reminders                            |
| **OneSignal**                  | Push notifications                                     |
| **Google Sign-In**             | Google authentication                                  |
| **Google Places**              | Location functionality                                 |
| **Supabase**                   | Authentication, PostgreSQL backend & security features |
| **Supabase Edge Functions**    | `chat-gateway`: chat moderation, bans & encryption     |
| **Ktor**                       | Networking                                             |
| **Kotlin Serialization**       | JSON serialization                                     |
| **Glide**                      | Image loading                                          |
| **Gradle Kotlin DSL**          | Build configuration                                    |

---

# ☁️ Backend Architecture

Skill Swap uses Firebase, Supabase, and OneSignal for different application requirements.

```text
Skill Swap
│
├── Firebase
│   ├── Authentication
│   ├── Cloud Firestore
│   ├── Realtime Database
│   ├── Storage
│   └── Chat (message store)
│
├── Supabase
│   ├── Authentication
│   ├── PostgreSQL
│   ├── Security Functions
│   ├── Chat Moderation (bans, warnings, audit)
│   ├── Edge Function: chat-gateway
│   └── Storage
│
└── OneSignal
    └── Push Notifications
```

## 🔥 Firebase

Firebase is used for Authentication, Cloud Firestore, Realtime Database, Firebase Storage, and real-time chat message storage.

## 🟢 Supabase

Supabase Auth handles email/password authentication, while PostgreSQL hosts custom security functions and chat moderation state. The `chat-gateway` edge function is the only path for sending chat messages.

```text
SupabaseClient.kt
SupabaseImageUploader.kt
ModerationClient.kt
```

## 🔔 OneSignal

OneSignal manages push notifications, notification targeting, user identification, notification data, and notification management.

---

# 🧩 Project Structure

```text
skill-swap/
│
├── app/
│   ├── src/
│   │   ├── androidTest/
│   │   └── main/
│   │       ├── java/com/example/sgp/
│   │       │   ├── Authentication/
│   │       │   ├── User & Profile/
│   │       │   ├── Skills/
│   │       │   ├── Resources/
│   │       │   ├── Trades/
│   │       │   ├── Chat/
│   │       │   ├── Admin/
│   │       │   ├── Videos/
│   │       │   ├── Feedback/
│   │       │   ├── Notifications/
│   │       │   ├── Supabase/
│   │       │   ├── Utils/
│   │       │   └── Other Components
│   │       │
│   │       └── res/
│   │
│   ├── build.gradle.kts
│   └── google-services.json
│
├── supabase/
│   ├── migrations/
│   │   ├── 001_chat_moderation.sql
│   │   └── 002_chat_bans.sql
│   └── functions/
│       └── chat-gateway/
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── firebase.json
├── gradle.properties
├── gradlew
├── gradlew.bat
└── .gitignore
```

---

# 🗄️ Main Data Structure

```text
users
skills
trades
chats
reports
```

### Chat Data

```text
chats
└── {conversationId}
    └── messages
        └── {messageId}
```

Moderation data (warnings, stages, bans, audit) lives in Supabase Postgres — see **Chat Moderation** above.

---

# 🔄 Complete Application Workflow

```text
Sign Up / Login
      ↓
Profile Completion
      ↓
Explore Skills / Resources
      ↓
Find a User
      ↓
Select Resource to Offer & Propose Schedule
      ↓
Create Trade
      ↓
Accepted / Rejected / Counter-Scheduled
      │
      └── Accepted → Schedule Finalized
            ↓
          Chat (moderated: warnings → 24h → 48h → permanent ban)
            ↓
      Meeting Reminders (24h / 1h)
            ↓
      Complete Trade
            ↓
      Rating & Feedback
```

OneSignal notifications operate independently throughout relevant application events.

---

# ⚙️ Requirements

To build and run Skill Swap, you need:

* Android Studio
* JDK 11
* Android SDK 36
* Android Device or Emulator
* Firebase Project
* Supabase Project
* OneSignal Account / Application

### Android Configuration

```text
Minimum SDK : 27
Compile SDK : 36
Target SDK  : 36
Java        : 11
Kotlin JVM  : 11
```

---

# 🚀 Installation

## 1. Clone the Repository

```bash
git clone https://github.com/pandyaomsanjay/skill-swap.git
cd skill-swap
```

## 2. Open in Android Studio

Open the cloned project using Android Studio and allow Gradle to synchronize and download the required dependencies.

## 3. Configure Firebase

Enable Firebase Authentication, Cloud Firestore, Realtime Database, and Firebase Storage, then add `google-services.json` to the Android project.

## 4. Configure Google Sign-In

Configure Google Sign-In through Firebase Authentication and ensure the required SHA-1/SHA-256 fingerprints are set for the app.

## 5. Configure Supabase

Create a Supabase project and configure Supabase Auth, email/password authentication, PostgreSQL security functions, and required storage. Run the SQL scripts from the `sql/` directory in the **Supabase SQL Editor**. For chat moderation, also run `supabase/migrations/001_chat_moderation.sql` and `002_chat_bans.sql`, then deploy `chat-gateway` (see **Setup Summary** under Chat Moderation).

## 6. Configure OneSignal

Create a OneSignal application, configure Android push notifications, connect FCM credentials through OneSignal, and add the OneSignal App ID to the app.

Documentation: https://documentation.onesignal.com/docs/en/android-sdk-setup

## 7. Build the Application

```text
File → Sync Project with Gradle Files
Build → Make Project
```

## 8. Run the Application

```text
Run → Run 'app'
```

> ⚠️ **Note:** The trade/resource feature set was added in a large single commit (5,036 additions, 567 deletions) touching many Kotlin files and layouts. Run a full Gradle build and manually test the trade, resource-upload, and reminder flows after pulling these changes.

---

# 🔐 Security

## ✅ Implemented Security Controls

| Requirement                               | Implementation                                                        |
| ------------------------------------------ | ---------------------------------------------------------------------- |
| **No Password Reuse**                     | Enforced during password change                                       |
| **Password Complexity**                   | Uppercase + lowercase + number + special character                    |
| **Account Lockout / Throttling**          | 5 failed attempts → 15-minute lock; second lockout → 24-hour lock     |
| **No Clear-Text Password Storage**        | Supabase Auth stores password hashes rather than plaintext passwords  |
| **Random Salt**                           | Bcrypt generates a unique salt for stored password hashes             |
| **Dictionary / Weak Password Protection** | Common and weak password patterns are rejected                        |
| **Server-Side Chat Ban Enforcement**      | Row-locked, idempotent escalation in Postgres; clients cannot bypass  |
| **Chat Message Encryption**               | AES-256-GCM via `chat-gateway`; direct Firestore message creation blocked |
| **Admin Action Validation & Audit**       | `userType` verified server-side; every unban logged in `chat_admin_audit` |

### Account Lockout

```text
5 Failed Attempts → 15-Minute Lock → Repeated Lockout → 24-Hour Lock
```

### Weak Password Protection

Rejects patterns such as: `aaaaaa`, `abab`, `123123`, `qwerty`, `asdf`, `zxcv`, `P@ssw0rd`

---

# 🗂️ SQL Security Files

```text
sql/
├── login_lockout.sql
├── common_passwords.sql
├── password_reset_otps.sql
└── verification_queries.sql

supabase/migrations/
├── 001_chat_moderation.sql
└── 002_chat_bans.sql
```

| SQL File                   | Purpose                                   |
| --------------------------- | ------------------------------------------ |
| `login_lockout.sql`        | Account lockout and failed-login tracking |
| `common_passwords.sql`     | Weak/dictionary password detection        |
| `password_reset_otps.sql`  | Password-reset OTP functionality          |
| `verification_queries.sql` | Security verification queries             |
| `001_chat_moderation.sql`  | Chat warnings, events, review queue       |
| `002_chat_bans.sql`        | 3-stage ban escalation, ban history, admin unban & audit |

---

# 🛡️ Security Best Practices

* Never expose private API keys.
* Do not commit service-account credentials.
* Configure proper Firebase Security Rules.
* Configure appropriate Supabase Row Level Security (RLS).
* Keep production secrets (including `CHAT_MASTER_KEY`) outside the Git repository.
* Never place OneSignal REST/API keys inside the Android application.
* Do not store passwords in plaintext.

---

# 🧪 Testing

The project includes Android testing configuration using JUnit, AndroidX JUnit, and Espresso, run through Android Studio's built-in unit and instrumentation testing tools. See **Test Recipe** under Chat Moderation for the ban-escalation walkthrough.

---

# 📂 Important Application Components

## Authentication

```text
AuthRepository.kt
Login.kt
Createaccount.kt
OtpActivity.kt
OtpVerificationActivity.kt
ForgotPasswordEmailActivity.kt
ResetPasswordActivity.kt
```

## Skills

```text
Skill.kt
AddSkillActivity.kt
ExploreActivity.kt
```

## Resources

```text
AddResourceActivity.kt
SelectResourceActivity.kt
LiveSessionInfo.kt
```

## Trades

```text
Trade.kt
TradeStatus.kt
TradeValidator.kt
TradeManager.kt
TradeRequestActivity.kt
TradeDetailActivity.kt
MyTradesActivity.kt
ActiveTradesFragment.kt
CompletedTradesFragment.kt
```

## Reminders & Notifications

```text
MeetingReminderScheduler.kt
MeetingReminderWorker.kt
```

## Chat & Moderation

```text
ChatActivity.kt
ChatListActivity.kt
NewChatActivity.kt
Chatmessage.kt
ChatCrypto.kt
ModerationClient.kt
ModerationMessages.kt
```

## Admin

```text
AdminDashboardActivity.kt
AdminBannedUsersActivity.kt
activity_admin_banned_users.xml
item_banned_user.xml
```

## Videos & Playlists

```text
UploadVideoActivity.kt
VideoPlayerActivity.kt
PlaylistActivity.kt
PlaylistManager.kt
PlaylistVideoAdapter.kt
AddPlaylistVideoActivity.kt
Resizablevideoview.kt
```

## Feedback

```text
FeedbackModels.kt
FeedbackAdapter.kt
SubmitFeedbackActivit.kt
RateUsActivity.kt
```

## Utilities

```text
LocaleHelper.kt
BaseActivity.kt
SettingsActivity.kt
```

## Supabase

```text
SupabaseClient.kt
SupabaseImageUploader.kt
supabase/functions/chat-gateway/ (index.ts, auth.ts, crypto.ts, firestore.ts, moderation.ts, policy.ts)
```

## SQL

```text
login_lockout.sql
common_passwords.sql
password_reset_otps.sql
verification_queries.sql
001_chat_moderation.sql
002_chat_bans.sql
```

# 🔮 Future Improvements

## 🔴 High Priority

1. **Teaching Limit** – Limit each user to teaching a maximum of 10 other users simultaneously.

2. **Payment Module**

   * **Payment Gateway Integration** – Allow users to make payments directly through the app.
   * **Multiple Payment Options** – Support multiple payment methods for user convenience.
   * **Payment Confirmation** – Provide confirmation after successful payments.
   * **Transaction History** – Allow users to view their previous payment transactions.
   * **Payment Status** – Display transactions as **Successful, Failed, or Pending**.
   * **48-Hour Payment/Service Window** – Manage the payment and associated service process within a 48-hour period.
   * **Admin Payment Section** – Allow administrators to monitor and manage payments, transactions, and payment statuses from the Admin Panel.
   * **Minimum Offer Amount** – Require all payment offers to start from a minimum of **₹50**.

3. **Points Transaction History** – Maintain a complete log of point credits and deductions, including the reason and timestamp, so users can review how their points were earned or spent.

4. **Trade/Swap from Demo Video** – Add a **Trade/Swap** option directly to individual demo videos, allowing users to send a trade request along with their preferred schedule.

5. **Playlist Completion Reward** – After successfully completing **2 entire playlists**, award the user a **500-point reward/discount** that can be used for future learning or trade activities.

6. **Notifications**

   * **New Trade Requests** – Notify users when they receive a new trade request.
   * **Meeting Reminders** – Send scheduled trade/meeting reminders to both users before the meeting.

7. **Hamburger Menu Improvements** – Complete the remaining pages accessible through the hamburger menu and remove the **Notifications** section from the hamburger menu.

## 🟡 Low Priority

1. **Playlist Purchase Time Limit** – After a defined period, prevent users from purchasing playlists that are no longer available for purchase.

2. **Playlist Sharing Across Platforms** – Allow users to share playlist links with others, with an option to make the shared playlist **public or private**.

3. **Low-Points Warning** – Notify users when their point balance is insufficient to access a playlist and provide suggestions for earning more points.

4. **Certificate System** – Provide users with a certificate after successfully completing a skill, course, or playlist.

# 📸 Screenshots

Add screenshots of the application inside the `screenshots/` directory.

Recommended screenshots: Login, Home, Explore Skills, Skill Details, Add Resource, Create Trade, My Trades (5 tabs), Trade Detail, Chat, Chat Ban Dialog, Admin Banned Users, Profile, Videos, Settings, Language Selection, Dark Mode.

```markdown
## 📸 Screenshots

| Login | Home |
|---|---|
| ![Login](screenshots/login.png) | ![Home](screenshots/home.png) |

| Explore | Trade |
|---|---|
| ![Explore](screenshots/explore.png) | ![Trade](screenshots/trade.png) |

| Chat | Profile |
|---|---|
| ![Chat](screenshots/chat.png) | ![Profile](screenshots/profile.png) |

| Settings | Language Selection |
|---|---|
| ![Settings](screenshots/settings.png) | ![Language](screenshots/language.png) |
```

---

# 🌐 Repository

**GitHub Repository:** https://github.com/pandyaomsanjay/skill-swap

**Latest major commit (Sep 26, 2026 — Trade & Resource System):**
https://github.com/pandyaomsanjay/skill-swap/commit/0d611ae2a28c767f558c5a0f8fbcbbde5359967c

---

# 👨‍💻 Project

## Skill Swap

### Learn • Share • Exchange

Skill Swap is designed to make knowledge exchange easier by connecting people who want to **teach their skills with people who want to learn them**.

---

# 📄 License

No explicit open-source license is currently included in the repository. If this project is intended for public distribution or open-source use, add an appropriate `LICENSE` file.

---

# ⭐ Support

If you find **Skill Swap** useful or interesting, consider giving the repository a ⭐ on GitHub.

---

**Built with Kotlin, Firebase, Supabase & OneSignal.**
