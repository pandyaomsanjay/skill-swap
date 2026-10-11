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
* 🎥 Skill Video Sharing
* 📑 Skill Playlists
* ⭐ Ratings & Feedback
* 🔔 OneSignal Push Notifications
* 📍 Location Support
* 🔎 Skill Discovery
* 🌐 Multi-Language Support
* 🌙 Dark Mode

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

## 🛡️ Chat Moderation, Encryption & Supabase Gateway

Skill Swap now uses a Supabase Edge Function named `chat-gateway` for chat moderation, warning enforcement, and message encryption. Firestore remains the chat message store, while moderation state lives in Supabase Postgres.

### What Changed

* The merged `firestore.rules` file keeps the existing rules and changes only the `CHATS` section.
* Messages can no longer be created directly from the Android app; only the `chat-gateway` edge function can write them.
* Clients also cannot write the last-message preview.
* Delete-for-everyone now clears `ciphertext` and `iv`.
* Garbled characters in existing comments were cleaned up.
* The Firestore rules file should be pasted into **Firebase Console → Firestore → Rules** and published **last** during rollout.

### How It Works on Supabase

* Chat messages stay in **Firestore**.
* The warning counter and 24-hour block live in **Supabase Postgres**, so one row lock makes the warning counter atomic.
* The `chat-gateway` function:

  1. Checks the Firebase login token.
  2. Applies warnings and the 24-hour block.
  3. Encrypts the message.
  4. Writes the message to Firestore using a Firebase service account.

* Appeals and reports work through a database table plus a resolve request from an admin account.
* The flow uses the existing `userType == "admin"` rule. There is no in-app review screen.

### Setup Summary

Full setup commands are in `SETUP_SUPABASE.md`.

1. Run `001_chat_moderation.sql` in the Supabase SQL Editor.
2. Enable `pg_cron` and schedule the daily job.
3. Download a Firebase service-account key from **Firebase Console → Project settings → Service accounts**.
4. Set three Supabase secrets:

   * `CHAT_MASTER_KEY` — generate and back it up first.
   * `FIREBASE_PROJECT_ID`
   * `FIREBASE_SERVICE_ACCOUNT`

5. Deploy the function:

   ```bash
   supabase functions deploy chat-gateway --no-verify-jwt
   ```

   The `--no-verify-jwt` flag is required because the app sends Firebase tokens.

6. If using the Supabase Dashboard instead of the CLI:

   * Go to **Edge Functions → Deploy a new function → Via Editor**.
   * Name it exactly `chat-gateway`.
   * Paste `index.ts` into the main file.
   * Add the other 5 files with exact names: `auth.ts`, `crypto.ts`, `firestore.ts`, `moderation.ts`, `policy.ts`.
   * Click **Deploy**.
   * Open the function’s **Settings / Details** and turn **OFF** “Verify JWT”.
   * Save the 3 secrets.

7. Rollout order:

   * Deploy the backend first.
   * Release the new app and force-update it.
   * Publish the new Firestore rules last.
   * Publishing the rules earlier breaks sending for everyone on an old app version.

### Files Added and Replaced

| Type | Files | Count |
|---|---|---|
| New Android files | `ChatCrypto.kt`, `ModerationClient.kt`, `ModerationMessages.kt` | 3 |
| New Supabase edge function files | `index.ts`, `auth.ts`, `crypto.ts`, `firestore.ts`, `moderation.ts`, `policy.ts` | 6 |
| New SQL file | `001_chat_moderation.sql` | 1 |
| Existing Android files replaced | `Chatmessage.kt`, `ChatActivity.kt`, `ChatListActivity.kt`, `NewChatActivity.kt` | 4 |
| Existing Android XML replaced | `data_extraction_rules.xml`, `backup_rules.xml` | 2 |
| Existing Firestore rules replaced | `firestore.rules` | 1 |

Optional tests: `ChatCryptoTest.kt`, `ModerationMessagesTest.kt`, `KeystoreKeyWrapperTest.kt`, `sql.test.mjs`, `crypto-interop.test.ts`. These can be skipped; the app works without them. The `_alternative_firebase_functions` folder is the old Firebase version and is not used.

### Test Status

Passed:

* SQL ran in an in-process Postgres.
* Checks covered warnings 1 to 5, the 24-hour block, retries counting once, the block applying across chats, expiry, spam repeats, appeals, and overturns.
* The abuse, threat, sexual, and spam word-filter rules passed 35 test cases.
* Edge function encryption matches the earlier Node implementation in both directions.

Not tested:

* The edge function itself.
* The Firestore rules in the emulator; only bracket balance was checked.
* The Kotlin code is uncompiled. Send build errors if any appear.

### Deployment Check

Before deploying:

* All 6 function files are present.
* The function name is exactly `chat-gateway`.
* No file is empty.
* Each file ends with a closing `}` or `);`.
* Do not add a 7th file.

After deploying, test the function is alive by opening:

```text
https://ghrxltlstncjcizyyqfo.supabase.co/functions/v1/chat-gateway/status
```

A browser sends `GET`, so the expected response is similar to:

```json
{"error":"method-not-allowed", ...}
```

That proves the function is running. If you see `Invalid JWT` or `404`, check the Verify JWT setting and the function URL.

Then replace the Android files, run the app, and test:

1. Send `hello`.
2. Send `you are an idiot`.

Leave the Firestore rules for last.

### Behavior Note

The restriction banner now refreshes when you open a chat and after each send. It is not live like the earlier Firestore-listener version.

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
│   └── Chat
│
├── Supabase
│   ├── Authentication
│   ├── PostgreSQL
│   ├── Security Functions
│   └── Storage
│
└── OneSignal
    └── Push Notifications
```

## 🔥 Firebase

Firebase is used for Authentication, Cloud Firestore, Realtime Database, Firebase Storage, and real-time chat.

## 🟢 Supabase

Supabase Auth handles email/password authentication, while PostgreSQL hosts custom security functions.

```text
SupabaseClient.kt
SupabaseImageUploader.kt
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
          Chat
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

Create a Supabase project and configure Supabase Auth, email/password authentication, PostgreSQL security functions, and required storage. Run the SQL scripts from the `sql/` directory in the **Supabase SQL Editor**.

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
```

| SQL File                   | Purpose                                   |
| --------------------------- | ------------------------------------------ |
| `login_lockout.sql`        | Account lockout and failed-login tracking |
| `common_passwords.sql`     | Weak/dictionary password detection        |
| `password_reset_otps.sql`  | Password-reset OTP functionality          |
| `verification_queries.sql` | Security verification queries             |

---

# 🛡️ Security Best Practices

* Never expose private API keys.
* Do not commit service-account credentials.
* Configure proper Firebase Security Rules.
* Configure appropriate Supabase Row Level Security (RLS).
* Keep production secrets outside the Git repository.
* Never place OneSignal REST/API keys inside the Android application.
* Do not store passwords in plaintext.

---

# 🧪 Testing

The project includes Android testing configuration using JUnit, AndroidX JUnit, and Espresso, run through Android Studio's built-in unit and instrumentation testing tools.

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

## Chat

```text
ChatActivity.kt
ChatListActivity.kt
NewChatActivity.kt
Chatmessage.kt
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
```

## SQL

```text
login_lockout.sql
common_passwords.sql
password_reset_otps.sql
verification_queries.sql
```

# 🔮 Future Improvements

## 🔴 High Priority

1. **Chat Encryption & Warning System** – Implement secure chat encryption, detect unnecessary or inappropriate messages, and issue warnings. After 5 warnings, temporarily restrict the user from chatting for 24 hours.

2. **Teaching Limit** – Limit each user to teaching a maximum of 10 other users simultaneously.

3. **Complete UI Redesign** – Redesign the entire user-side interface to make the application modern, attractive, responsive, intuitive, and user-friendly.

4. **Payment Module**

   * **Payment Gateway Integration** – Allow users to make payments directly through the app.
   * **Multiple Payment Options** – Support multiple payment methods for user convenience.
   * **Payment Confirmation** – Provide confirmation after successful payments.
   * **Transaction History** – Allow users to view their previous payment transactions.
   * **Payment Status** – Display transactions as **Successful, Failed, or Pending**.
   * **48-Hour Payment/Service Window** – Manage the payment and associated service process within a 48-hour period.
   * **Admin Payment Section** – Allow administrators to monitor and manage payments, transactions, and payment statuses from the Admin Panel.
   * **Minimum Offer Amount** – Require all payment offers to start from a minimum of **₹50**.

5. **Points Transaction History** – Maintain a complete log of point credits and deductions, including the reason and timestamp, so users can review how their points were earned or spent.

6. **Trade/Swap from Demo Video** – Add a **Trade/Swap** option directly to individual demo videos, allowing users to send a trade request along with their preferred schedule.

7. **Playlist Completion Reward** – After successfully completing **2 entire playlists**, award the user a **500-point reward/discount** that can be used for future learning or trade activities.

8. **Notifications**

   * **New Trade Requests** – Notify users when they receive a new trade request.
   * **Meeting Reminders** – Send scheduled trade/meeting reminders to both users before the meeting.

9. **Admin Users Page Improvements** – Fix the login verification flow for verified users and display reported-user information clearly when a user has been reported.

10. **Hamburger Menu Improvements** – Complete the remaining pages accessible through the hamburger menu and remove the **Notifications** section from the hamburger menu.

## 🟡 Low Priority

1. **Playlist Purchase Time Limit** – After a defined period, prevent users from purchasing playlists that are no longer available for purchase.

2. **Playlist Sharing Across Platforms** – Allow users to share playlist links with others, with an option to make the shared playlist **public or private**.

3. **Low-Points Warning** – Notify users when their point balance is insufficient to access a playlist and provide suggestions for earning more points.

4. **Certificate System** – Provide users with a certificate after successfully completing a skill, course, or playlist.

# 📸 Screenshots

Add screenshots of the application inside the `screenshots/` directory.

Recommended screenshots: Login, Home, Explore Skills, Skill Details, Add Resource, Create Trade, My Trades (5 tabs), Trade Detail, Chat, Profile, Videos, Settings, Language Selection, Dark Mode.

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
