# 🔄 Skill Swap

> **A peer-to-peer Android platform for exchanging skills, knowledge, and expertise.**

Skill Swap is an Android application designed to connect people who want to **share what they know and learn new skills from others**.

Users can create profiles, add skills, discover other users, exchange skills through trade requests, communicate through real-time chat, share skill videos, provide ratings and feedback, and receive notifications.

The application also includes a dedicated **Admin Panel** for managing users, skills, trades, reports, feedback, videos, and application statistics.

---

## ✨ Key Features

* 🔐 User Authentication
* 👤 User Profiles
* 🎯 Skill Management
* 🔄 Skill Exchange / Trade Requests
* 💬 Real-Time Chat
* 🎥 Skill Video Sharing
* 📑 Skill Playlists
* ⭐ Ratings & Feedback
* 🔔 OneSignal Push Notifications
* 📍 Location Support
* 🔎 Skill Discovery
* 🛡️ Admin Panel
* 📊 Admin Dashboard & Statistics
* 🚨 Report Management
* ⚙️ Admin Settings

---

## 📱 User Features

### 🔐 Authentication

Users can securely access the application through the authentication system.

Features include:

* User registration
* User login
* Firebase Authentication
* Google Sign-In
* OTP verification
* Forgot password
* Password reset
* Change password
* Account management

---

### 👤 User Profile

Users can create and manage their personal profiles.

Profile information includes:

* Name
* Email
* Profile picture
* Location
* Skills offered
* Skills requested
* Rating
* Points
* Number of trades
* Number of skills

Users can also update their profile information whenever required.

---

### 🎯 Skill Management

Users can add and manage the skills they want to teach or share.

**Skill Features**

* Add a new skill
* View skills
* Explore skills
* Browse skill categories
* View skill details
* Upload skill videos
* Discover skills offered by other users

**Skill Categories**

The application includes categories such as:

* 💻 Technology
* 🎨 Arts
* ⚽ Sports
* 🏠 Home
* 📚 Education
* 🌱 Lifestyle

---

### 🔄 Skill Exchange / Trading

The core functionality of Skill Swap is exchanging knowledge between users.

A user can select:

* A skill they can teach
* A skill they want to learn
* Another user to exchange skills with
* An optional message

**Trade Process**

```text
Find Skill → Select User → Choose Skill to Offer → Choose Skill to Learn
   → Send Trade Request → Accept / Reject → Start Skill Exchange
   → Complete Trade → Rating & Feedback
```

Trade information can include: requester, receiver, offered skill, requested skill, trade status, timestamp, user information, skill information, and rating/feedback information.

---

### 💬 Real-Time Chat

Skill Swap provides communication between users through real-time chat.

Users can:

* Start conversations
* Send messages
* Receive messages
* View previous messages
* Track unread conversations
* Communicate about skill exchanges

Chat functionality is implemented using **Cloud Firestore**.

**Chat Structure**

```text
chats
 └── {conversationId}
      └── messages
           └── {messageId}
```

---

### 🎥 Skill Videos

Users can share videos related to their skills.

**Video Features**

* Upload skill videos
* View videos
* Play videos
* Browse skill videos
* Create/view playlists
* Admin video management

Application components include:

```text
UploadVideoActivity.kt
VideoPlayerActivity.kt
PlaylistActivity.kt
Resizablevideoview.kt
```

---

### 📑 Playlists

Skill Swap supports **video playlists** — a bundled way to publish and unlock a set of related skill videos as one purchasable unit.

**Implemented Playlist Features**

* Create and publish playlists (a `Skill` with `skillType == "playlist"`)
* Browse playlist details — thumbnail, category, creator, description, video count, and total duration
* Preview a playlist via a **demo video** before purchasing
* Unlock playlists using **credits**, with live balance checks and clear "not enough credits" state
* Transactional purchase flow — credits are deducted, the purchase is recorded, and access is granted atomically, so a failed step can't double-charge or leave partial access
* Per-user **progress tracking** — videos completed, percentage watched, and a progress bar that turns green at 100%
* Free, automatic access for the playlist **owner** and for **admins**
* **"People with Access"** view for playlist owners — everyone who purchased the playlist, with names and join dates
* **500-credit reward**, granted automatically, after a user completes 2 full playlists
* OneSignal push notifications on playlist purchase and playlist completion
* One-time data-migration utility (`backfillPurchaseNames`) to backfill missing buyer names on existing purchase records

Application components include:

```text
PlaylistActivity.kt
PlaylistManager.kt
PlaylistVideoAdapter.kt
AddPlaylistVideoActivity.kt
```

---

### ⭐ Ratings & Feedback

Skill Swap allows users to provide feedback after interacting or completing skill exchanges.

Users can:

* Submit feedback
* Rate users
* View feedback
* Provide ratings
* Review previous feedback

The application contains dedicated feedback and rating components.

---

### 🔔 OneSignal Push Notifications

Skill Swap uses **OneSignal** for push notifications.

Notifications can be used for important events such as:

* 🔄 New trade requests
* ✅ Trade status updates
* 💬 New chat messages
* 📑 Playlist purchases & playlist completion
* ⭐ Feedback/rating events
* 📢 Important application notifications

**Notification Architecture**

```text
Skill Swap Android App → OneSignal SDK → OneSignal Platform
   → Android Push Service → User
```

OneSignal provides the notification management and delivery layer for the application. For Android push delivery, OneSignal uses Firebase Cloud Messaging (FCM) credentials configured through the OneSignal platform. **Firebase Cloud Functions are not required for the notification architecture.**

Official OneSignal documentation: [OneSignal Android SDK Setup](https://documentation.onesignal.com/docs/en/android-sdk-setup)

---

### 📍 Location Support

The application includes Google Places integration for location-related functionality — used to select locations, display user locations, and assist users in finding relevant people based on location.

---

## 🛡️ Admin Panel

Skill Swap includes a dedicated **Admin Panel** for administrators to monitor and manage the application.

### 📊 Admin Dashboard

The Admin Dashboard provides an overview of the application's current activity.

Administrators can monitor: 👥 Total Users, 🔄 Total Trades, 🎯 Total Skills, 🚨 Pending Reports, and 📈 Swap Activity.

**Statistics Filters:** Today · Week · Month · Custom Range · All Time

### 👥 Admin User Management

Accessible through `AdminUsersActivity.kt`, providing administrators with an overview of users registered on the platform.

### 🎯 Admin Skill Management

Administrators can manage the skills available on the platform through `AdminSkillsActivity.kt`, allowing them to monitor skill-related information and maintain the skill database.

### 🔄 Admin Trade Management

Administrators can monitor skill exchange/trade activity through `AdminTradesActivity.kt`, reviewing trade-related information and the platform's exchange activity.

### 🚨 Admin Report Management

The application includes a reporting system where issues can be reported. Administrators can review reports through `AdminReportsActivity.kt`. The dashboard also tracks pending reports so administrators can identify issues requiring attention.

### ⭐ Admin Feedback Management

Administrators can manage user feedback through `AdminFeedbackActivity.kt`, monitoring feedback and ratings submitted by users.

### 🎥 Admin Video Management

The Admin Panel includes video management functionality through `AdminVideosActivity.kt` and `AdminVideoPlayerActivity.kt`, allowing administrators to access and play uploaded skill videos.

### ⚙️ Admin Settings

The application includes an administration settings section through `AdminSettingsActivity.kt`, providing a dedicated area for administrator-level application settings.

### 🔐 Admin Access

The application checks the user's account type before providing access to the Admin Dashboard, using a `user_type` value to distinguish administrator access from normal user access.

```text
Normal User → User Dashboard
Admin User  → Admin Dashboard
```

---

## 🏗️ Technology Stack

| Technology                     | Purpose                           |
| ------------------------------- | ---------------------------------- |
| **Kotlin**                     | Primary programming language      |
| **Android SDK**                | Android application development   |
| **AndroidX**                   | Android components                |
| **Material Components**        | UI components                     |
| **Firebase Authentication**    | User authentication               |
| **Cloud Firestore**            | Application data & real-time chat |
| **Firebase Realtime Database** | Real-time data                    |
| **Firebase Storage**           | File/media storage                |
| **OneSignal**                  | Push notifications                |
| **Google Sign-In**             | Google authentication             |
| **Google Places**              | Location functionality            |
| **Supabase**                   | Auth (email/password), Postgres backend for security features |
| **Ktor**                       | Networking                        |
| **Kotlin Serialization**       | JSON serialization                |
| **Glide**                      | Image loading                     |
| **Gradle Kotlin DSL**          | Build configuration               |

---

## ☁️ Backend Architecture

Skill Swap uses Firebase, Supabase, and OneSignal for different application requirements.

```text
Skill Swap
   ├── Firebase  → Auth, Firestore, Realtime DB, Storage, Chat
   ├── Supabase  → Auth (email/password), Storage
   └── OneSignal → Push Notifications
```

### 🔥 Firebase

Firebase is used for authentication, Cloud Firestore, Realtime Database, and Storage. Firebase Cloud Firestore is also used for real-time chat functionality.

### 🟢 Supabase

Supabase is integrated into the application for authentication and backend security functionality, in addition to storage.

```text
SupabaseClient.kt
SupabaseImageUploader.kt
```

Supabase Auth (Postgres-backed, `auth.users`) handles email/password login and password hashing (bcrypt, with a unique random salt embedded per user). Supabase Postgres also hosts the custom SQL functions used for account lockout and password-strength checks — see [🔐 Security](#-security) below.

### 🔔 OneSignal

OneSignal is responsible for the application's push notification functionality. It provides push notifications, notification targeting, user identification, notification data, and notification management.

---

## 🧩 Project Structure

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
│   │       │   ├── Trades/
│   │       │   ├── Chat/
│   │       │   ├── Videos/
│   │       │   ├── Feedback/
│   │       │   ├── Admin/
│   │       │   ├── Supabase/
│   │       │   └── Other Components
│   │       └── res/
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

## 🗄️ Main Data Structure

The application uses Firebase data collections for major application entities: `users`, `skills`, `trades`, `chats`, `reports`.

**Chat Data**

```text
chats
 └── {conversationId}
       └── messages
             └── {messageId}
```

---

## 🔄 Complete Application Workflow

```text
Sign Up / Login → Profile Completion → Explore Skills → Find a User
   → Create Trade → Accepted / Rejected
                       │
                    Accepted → Chat → Complete Trade → Rating & Feedback
```

Notifications are handled independently through OneSignal throughout relevant application events.

---

## ⚙️ Requirements

To build and run Skill Swap, you need:

* Android Studio
* JDK 11
* Android SDK 36
* Android device or emulator
* Firebase project
* Supabase project
* OneSignal account/application

**Android Configuration**

```text
Minimum SDK : 27
Compile SDK : 36
Target SDK  : 36
Java        : 11
Kotlin JVM  : 11
```

---

## 🚀 Installation

### 1. Clone the Repository

```bash
git clone https://github.com/pandyaomsanjay/skill-swap.git
cd skill-swap
```

### 2. Open in Android Studio

Open the cloned project using Android Studio. Allow Gradle to synchronize the project and download the required dependencies.

### 3. Configure Firebase

Create or configure a Firebase project. Enable the services required by the application:

```text
Firebase Authentication
Cloud Firestore
Realtime Database
Firebase Storage
```

Add the appropriate Android Firebase configuration (`google-services.json`).

### 4. Configure Google Sign-In

Configure Google Sign-In through Firebase Authentication. Make sure the required SHA-1/SHA-256 fingerprints are configured for the Android application.

### 5. Configure Supabase

Create a Supabase project and configure Supabase Auth for email/password login. Run the SQL scripts under `sql/` (see [🔐 Security](#-security) below) in the Supabase SQL Editor to set up account lockout, password-pattern checking, and password-reset OTP support. Update the application's Supabase configuration accordingly.

### 6. Configure OneSignal

Create a OneSignal application. Configure Android push notifications and connect the required FCM credentials through OneSignal, then configure the OneSignal App ID in the Android application.

Official documentation: [OneSignal Android SDK Setup](https://documentation.onesignal.com/docs/en/android-sdk-setup)

### 7. Build the Application

In Android Studio:

```text
File → Sync Project with Gradle Files
Build → Make Project
```

### 8. Run the Application

Connect an Android device or start an Android emulator, then select:

```text
Run → Run 'app'
```

---

## 🔐 Security

Skill Swap implements the following password and account-security controls, primarily via Supabase Auth (Postgres) with supporting checks in the Android client.

### ✅ Implemented

| Requirement | Implementation |
|---|---|
| **No password reuse on change** | Enforced during password change flow |
| **Password complexity** | Client-side validation requires uppercase, lowercase, a number, and a special character (`Createaccount.kt`, `ResetPasswordActivity.kt`) |
| **Account lockout / throttling** | Escalating lockout via Postgres `SECURITY DEFINER` RPCs: 5 failed attempts → 15-minute lock; a second lockout → 24-hour lock (`login_lockout.sql`, called from `AuthRepository.kt`) |
| **No clear-text password storage** | Authentication runs through Supabase Auth (`auth.users`), which stores only a bcrypt hash — never the plaintext password. Verified directly via SQL: `auth.users` has no plaintext password column, only `encrypted_password` |
| **Random salt** | Bcrypt embeds a unique, randomly generated salt in every stored hash. Verified that no two users share an identical hash even where duplicate passwords are possible, and that 100% of stored hashes match bcrypt's salted format |
| **Disallow dictionary / weak passwords** | `is_password_common()` Postgres function (`common_passwords.sql`) rejects: exact matches against a known-weak password list, all-repeated-character strings (`aaaaaa`), repeated-chunk strings (`abab`, `123123`), common keyboard-walk patterns (`qwerty`, `asdf`, `zxcv`), and leetspeak-disguised matches (`P@ssw0rd` → `password`). Called from the Android app at signup (`Createaccount.kt`) and password reset (`AuthRepository.kt`) |

### ⏳ Not implemented (policy-level)

| Requirement | Status |
|---|---|
| **Disallow password sharing** | Not enforced technically — no reliable server-side way to detect shared credentials without additional device/session tracking. Addressed as a stated policy: accounts are for individual use only, per the application's Terms of Service |

### SQL Files

The Supabase-side security logic lives in the SQL Editor as the following scripts:

```text
sql/
├── login_lockout.sql           # Escalating lockout functions (check_login_lock, record_failed_login, reset_login_attempts)
├── common_passwords.sql        # Dictionary/pattern password check (is_password_common)
├── password_reset_otps.sql     # OTP table for password reset flow
└── verification_queries.sql    # Manual queries used to verify hashing/salting on auth.users (not runtime code)
```

### General Recommended Practices

* Never expose private API keys.
* Do not commit service-account credentials.
* Configure proper Firebase security rules.
* Configure proper Supabase Row Level Security (RLS) policies — tables like `common_passwords` and `password_reset_otps` have RLS enabled with no client-facing policies; they are only accessed through `SECURITY DEFINER` functions.
* Do not place OneSignal REST/API keys inside the Android application.
* Keep production secrets outside the Git repository.

---

## 🧪 Testing

The project includes Android testing configuration.

Testing technologies include: JUnit, AndroidX JUnit, Espresso.

Testing can be performed using Android Studio's built-in test and instrumentation testing tools.

---

## 📂 Important Application Components

**Authentication**

```text
AuthRepository.kt
Login.kt
Createaccount.kt
OtpActivity.kt
OtpVerificationActivity.kt
ForgotPasswordEmailActivity.kt
ResetPasswordActivity.kt
```

**Skills**

```text
Skill.kt
AddSkillActivity.kt
ExploreActivity.kt
```

**Trades**

```text
Trade.kt
CreateTradeActivity.kt
MyTradesActivity.kt
ActiveTradesFragment.kt
CompletedTradesFragment.kt
```

**Chat**

```text
ChatActivity.kt
ChatListActivity.kt
NewChatActivity.kt
Chatmessage.kt
```

**Videos & Playlists**

```text
UploadVideoActivity.kt
VideoPlayerActivity.kt
PlaylistActivity.kt
PlaylistManager.kt
PlaylistVideoAdapter.kt
AddPlaylistVideoActivity.kt
Resizablevideoview.kt
```

**Feedback**

```text
FeedbackModels.kt
FeedbackAdapter.kt
SubmitFeedbackActivit.kt
RateUsActivity.kt
```

**Admin**

```text
AdminDashboardActivity.kt
AdminUsersActivity.kt
AdminSkillsActivity.kt
AdminTradesActivity.kt
AdminFeedbackActivity.kt
AdminReportsActivity.kt
AdminSettingsActivity.kt
AdminVideosActivity.kt
AdminVideoPlayerActivity.kt
```

**Supabase**

```text
SupabaseClient.kt
SupabaseImageUploader.kt
```

**SQL (Supabase SQL Editor)**

```text
login_lockout.sql
common_passwords.sql
password_reset_otps.sql
verification_queries.sql
```

---

## 🔮 Future Improvements

------------------------------------
### 🔴 High Priority
------------------------------------

1. **Chat Encryption & Warning System** – Implement encryption for chats. The system should detect unnecessary or inappropriate chats and issue warnings. After 5 warnings, the user should be temporarily blocked from chatting for 24 hours.
2. **Teaching Limit** – Each user can teach up to 10 other users at the same time.
3. **Complete UI Redesign** – Change and improve the entire user-side UI to make the application modern, attractive, and user-friendly.
4. **Multiple Languages** – Add support for different languages so users can select their preferred language.
5. **Payment Module** – Implement a complete and secure payment system.
6. **Dark Mode** – Add Dark Mode for the user side of the application.
7. **Swap Animation** – Add an attractive animation on the swapping/trade page to make the swap process more interactive.
8. **Continue Where You Left Off** – Users should be able to continue from where they previously stopped, such as continuing a video, playlist, course, or learning activity.
9. **Certificate System** – Provide users with a certificate after successfully completing a skill/course/playlist.
10. **Fix the Complete Trade/Swap System** – Review and fix the entire trade/swap functionality to make the process smooth and reliable.
11. **Trade/Swap from Demo Video** – Add a Trade/Swap option directly on individual demo videos. When a user clicks it, they can send a trade request along with their preferred schedule.
12. **Trade Request & Counter-Schedule System** – When another user receives a trade request, they can accept it if they are available at the proposed time. If they are not available, they can provide one counter-schedule. After that, both users must finalize one mutually agreed date and time for the trade.
13. **Meeting Reminders** – Both users should receive a reminder before the scheduled trade/meeting.
14. **Different Trade Options** – Users should be able to trade different types of learning resources, such as:
    - Notes
    - Videos
    - Live teaching sessions
    - Documents/PDFs
    - Courses
    - Other useful learning resources
15. **Password Sharing Detection** – Optional device/session-based signal (e.g. flag logins from a notably different device than the last known one) to support the "disallow password sharing" security requirement beyond policy alone.

------------------------------------
### 🟡 Low Priority
------------------------------------

1. **Playlist Purchase Expiry** – After some months, a user should no longer be able to purchase a given playlist (e.g. it expires or is retired).
2. **Points Transaction History** – Maintain a log of all point credits and deductions with reason and timestamp, so users can review how their points were earned or spent.
3. **Playlist Sharing Across Platforms** – Allow users to share a playlist link with others, with an option to keep it public or private.
4. **Low-Points Warning** – Notify users when their point balance is insufficient to access a playlist, along with suggestions on how to earn more points.
5. **Playlist Discount** – Offer playlists at a lower total points cost than purchasing the equivalent individual resources separately.
6. **Advanced Chat & User Channels** – LinkedIn-style chat with user video/channel pages.

> ℹ️ Items previously listed here that are now implemented — Multi-Language Support (via `LocaleHelper`), Dark Mode (via `SettingsActivity`/`BaseActivity`), Advanced Playlists, Playlist Preview Videos, Points/Completion Rewards, Account Lockout/Throttling, Password Complexity Rules, Dictionary/Pattern Password Checking — have been moved to their relevant feature sections above (see [📑 Playlists](#-playlists) and [🔐 Security](#-security)) and removed from this roadmap.

---

## 📸 Screenshots

Add screenshots of the application here to showcase the main interfaces.

Recommended screenshots: Login, Home, Explore Skills, Skill Details, Create Trade, My Trades, Chat, Profile, Videos, Admin Dashboard, Admin Users, Admin Skills, Admin Trades, Admin Reports.

Example:

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

| Admin Dashboard | Admin Panel |
|---|---|
| ![Dashboard](screenshots/admin-dashboard.png) | ![Admin](screenshots/admin-panel.png) |
```

---

## 🌐 Repository

**GitHub Repository:** [Skill Swap — GitHub Repository](https://github.com/pandyaomsanjay/skill-swap)

---

## 👨‍💻 Project

### Skill Swap

**Learn • Share • Exchange**

A platform designed to make knowledge exchange easier by connecting people who want to teach their skills with people who want to learn them.

---

## 📄 License

No explicit open-source license is currently included in the repository.

If this project is intended for public distribution or open-source use, add an appropriate `LICENSE` file.

---

## ⭐ Support

If you find **Skill Swap** useful or interesting, consider giving the repository a ⭐ on GitHub.

**Built with Kotlin, Firebase, Supabase & OneSignal.**
