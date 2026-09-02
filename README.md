# 🔄 Skill Swap

> **Learn • Share • Exchange**

Skill Swap is a peer-to-peer Android application designed to connect people who want to **teach their skills, learn new skills, and exchange knowledge with others**.

Users can create profiles, add skills, discover other users, exchange skills through trade requests, communicate through real-time chat, share skill videos, create playlists, provide ratings and feedback, and receive push notifications.

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

Users can update their profile information whenever required.

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

# 🔄 Skill Exchange / Trading

The core functionality of Skill Swap is exchanging knowledge between users.

A user can select:

1. A skill they can teach
2. A skill they want to learn
3. Another user to exchange skills with
4. An optional message

### Trade Process

```text
Find Skill
    ↓
Select User
    ↓
Choose Skill to Offer
    ↓
Choose Skill to Learn
    ↓
Send Trade Request
    ↓
Accept / Reject
    ↓
Start Skill Exchange
    ↓
Complete Trade
    ↓
Rating & Feedback
```

Trade information can include:

* Requester
* Receiver
* Offered Skill
* Requested Skill
* Trade Status
* Timestamp
* User Information
* Skill Information
* Rating / Feedback Information

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

# 🎥 Skill Videos

Users can share videos related to their skills.

### Video Features

* Upload Skill Videos
* View Videos
* Play Videos
* Browse Skill Videos
* Create / View Playlists
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

Skill Swap supports multiple languages.

### Supported Languages

* 🇬🇧 English
* 🇮🇳 Hindi
* 🇮🇳 Gujarati

Language preferences are managed using:

```text
LocaleHelper.kt
```

Users can change their preferred language through the application settings.

The selected language is persisted across sessions.

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

Firebase is used for:

* Authentication
* Cloud Firestore
* Realtime Database
* Firebase Storage
* Real-time chat

## 🟢 Supabase

Supabase is integrated for authentication and backend security functionality.

Supabase Auth handles email/password authentication, while PostgreSQL hosts custom security functions.

Important components:

```text
SupabaseClient.kt
SupabaseImageUploader.kt
```

## 🔔 OneSignal

OneSignal manages:

* Push notifications
* Notification targeting
* User identification
* Notification data
* Notification management

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
│   │       │   ├── Trades/
│   │       │   ├── Chat/
│   │       │   ├── Videos/
│   │       │   ├── Feedback/
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

The application uses Firebase data collections for major application entities:

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
Explore Skills
      ↓
Find a User
      ↓
Create Trade
      ↓
Accepted / Rejected
      │
      └── Accepted
            ↓
          Chat
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

Open the cloned project using Android Studio.

Allow Gradle to synchronize and download the required dependencies.

## 3. Configure Firebase

Create or configure a Firebase project and enable:

```text
Firebase Authentication
Cloud Firestore
Realtime Database
Firebase Storage
```

Add the appropriate:

```text
google-services.json
```

to the Android project.

## 4. Configure Google Sign-In

Configure Google Sign-In through Firebase Authentication.

Make sure the required SHA-1 and SHA-256 fingerprints are configured for the Android application.

## 5. Configure Supabase

Create a Supabase project and configure:

* Supabase Auth
* Email/password authentication
* PostgreSQL security functions
* Required storage

Run the SQL scripts from the `sql/` directory in the **Supabase SQL Editor**.

## 6. Configure OneSignal

Create a OneSignal application.

Configure Android push notifications and connect the required FCM credentials through OneSignal.

Add the OneSignal App ID to the Android application.

Documentation:

https://documentation.onesignal.com/docs/en/android-sdk-setup

## 7. Build the Application

In Android Studio:

```text
File → Sync Project with Gradle Files

Build → Make Project
```

## 8. Run the Application

Connect an Android device or start an emulator.

Then select:

```text
Run → Run 'app'
```

---

# 🔐 Security

Skill Swap implements several password and account-security controls.

## ✅ Implemented Security Controls

| Requirement                               | Implementation                                                       |
| ----------------------------------------- | -------------------------------------------------------------------- |
| **No Password Reuse**                     | Enforced during password change                                      |
| **Password Complexity**                   | Uppercase + lowercase + number + special character                   |
| **Account Lockout / Throttling**          | 5 failed attempts → 15-minute lock; second lockout → 24-hour lock    |
| **No Clear-Text Password Storage**        | Supabase Auth stores password hashes rather than plaintext passwords |
| **Random Salt**                           | Bcrypt generates a unique salt for stored password hashes            |
| **Dictionary / Weak Password Protection** | Common and weak password patterns are rejected                       |

### Password Complexity

Password validation requires:

```text
Uppercase
Lowercase
Number
Special Character
```

### Account Lockout

```text
5 Failed Attempts
        ↓
15-Minute Lock
        ↓
Repeated Lockout
        ↓
24-Hour Lock
```

### Weak Password Protection

The password protection system checks for patterns such as:

```text
aaaaaa
abab
123123
qwerty
asdf
zxcv
P@ssw0rd
```

---

# 🗂️ SQL Security Files

Supabase security logic is maintained through:

```text
sql/
│
├── login_lockout.sql
├── common_passwords.sql
├── password_reset_otps.sql
└── verification_queries.sql
```

### Purpose

| SQL File                   | Purpose                                   |
| -------------------------- | ----------------------------------------- |
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

The project includes Android testing configuration.

Testing technologies include:

* JUnit
* AndroidX JUnit
* Espresso

Testing can be performed using Android Studio's built-in unit and instrumentation testing tools.

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

## Trades

```text
Trade.kt
CreateTradeActivity.kt
MyTradesActivity.kt
ActiveTradesFragment.kt
CompletedTradesFragment.kt
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

---

# 🔮 Future Improvements

## 🔴 High Priority

1. **Chat Encryption & Warning System** – Encrypt chats and detect inappropriate or unnecessary messages. After 5 warnings, temporarily block chatting for 24 hours.

2. **Teaching Limit** – Limit each user to teaching a maximum of 10 users simultaneously.

3. **Complete UI Redesign** – Redesign the user-side UI to make it modern, attractive, responsive, and user-friendly.

4. **Payment Module** – Implement a secure in-app payment system with:

   * **Payment Gateway Integration** – Enable direct in-app payments.
   * **Multiple Payment Options** – Support multiple payment methods.
   * **Payment Confirmation** – Confirm successful payments.
   * **Transaction History** – Allow users to view previous transactions.
   * **Payment Status** – Show Successful, Failed, or Pending status.
   * **48-Hour Payment/Service Window** – Manage payment/service processing within 48 hours.
   * **Admin Payment Section** – Allow admins to monitor and manage payments and transactions.
   * **Minimum Offer Amount** – All payment offers must start from **₹50**.

5. **Dark Mode** – Add Dark Mode support for the user-side application.

6. **Continue Where You Left Off** – Resume videos, playlists, courses, and learning activities from the user's previous progress.

7. **Certificate System** – Generate certificates after successfully completing a skill, course, or playlist.

8. **Complete Trade/Swap System** – Fix and improve the complete trade/swap workflow for smooth and reliable operation.

9. **Trade/Swap from Demo Video** – Allow users to initiate a trade directly from a demo video with their preferred schedule.

10. **Trade Request & Counter-Schedule** – Allow recipients to accept the proposed schedule or submit one counter-schedule. Both users must finalize a mutually agreed date and time.

11. **Different Trade Options** – Support exchanging:

    * Notes
    * Videos
    * Live Teaching Sessions
    * Documents/PDFs
    * Courses
    * Other Learning Resources

12. **Playlist Completion Reward** – Award **500 points** after successfully completing 2 complete playlists, usable for future learning or trade activities.

13. **Notifications & Reminders** – Send notifications for:

    * 🔄 New Trade Requests
    * ✅ Trade Status Updates
    * 📑 Playlist Purchases & Completion
    * ⭐ Feedback & Rating Events
    * 🔔 Scheduled Trade/Meeting Reminders

---

## 🟡 Low Priority

1. Playlist Purchase Expiry
2. Points Transaction History
3. Playlist Sharing Across Platforms
4. Low-Points Warning
5. Playlist Discounts
6. Advanced Chat & User Channels

---

# 🌐 Multi-Language Support

Skill Swap supports multiple languages to make the application accessible and easy to use for users from different regions.

## 🗣️ Available Languages

| Language                    | Code | Status      |
| --------------------------- | ---- | ----------- |
| 🇬🇧 **English**            | `en` | ✅ Supported |
| 🇮🇳 **Hindi (हिन्दी)**     | `hi` | ✅ Supported |
| 🇮🇳 **Gujarati (ગુજરાતી)** | `gu` | ✅ Supported |

### 🇬🇧 English

English is the default language of the Skill Swap application and is used throughout the main user interface.

### 🇮🇳 Hindi (हिन्दी)

Hindi support allows users to use the application interface in Hindi, making Skill Swap more accessible to Hindi-speaking users.

### 🇮🇳 Gujarati (ગુજરાતી)

Gujarati support provides a localized interface for Gujarati-speaking users and improves accessibility for users in Gujarat and other Gujarati-speaking communities.

## ⚙️ Language Selection

Users can select their preferred language from **two locations**:

### 🔐 Login Page

When a user opens the Skill Swap application and reaches the Login page, the application asks the user to select their preferred language:

**English | हिन्दी | ગુજરાતી**

The selected language is applied to the application interface.

### 👤 Profile Page

After logging in, users can change their preferred language from their Profile page:

**Profile → Language**

Users can switch between English, Hindi, and Gujarati at any time. The selected language is saved and automatically applied across the application.

### 🔧 Implementation

Language management is handled through:

```text
LocaleHelper.kt
```

Android localization resources are organized using language-specific resource directories:

```text
res/
├── values/
│   └── strings.xml          # English
│
├── values-hi/
│   └── strings.xml          # Hindi
│
└── values-gu/
    └── strings.xml          # Gujarati
```

This structure allows the application UI to display translated text based on the user's selected language.

## 🔄 Language Flow

```text
Open Skill Swap
       ↓
    Login Page
       ↓
Select Language
       ↓
English / Hindi / Gujarati
       ↓
Login / Continue
       ↓
Application Opens
       ↓
Profile → Language
       ↓
Change Language Anytime
```


# 📸 Screenshots

Add screenshots of the application inside the `screenshots/` directory.

Recommended screenshots:

* Login
* Home
* Explore Skills
* Skill Details
* Create Trade
* My Trades
* Chat
* Profile
* Videos
* Settings
* Language Selection
* Dark Mode

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

| Settings | Language Selection |
|---|---|
| ![Settings](screenshots/settings.png) | ![Language](screenshots/language.png) |
```

---

# 🌐 Repository

**GitHub Repository:**

https://github.com/pandyaomsanjay/skill-swap

---

# 👨‍💻 Project

## Skill Swap

### Learn • Share • Exchange

Skill Swap is designed to make knowledge exchange easier by connecting people who want to **teach their skills with people who want to learn them**.

---

# 📄 License

No explicit open-source license is currently included in the repository.

If this project is intended for public distribution or open-source use, add an appropriate `LICENSE` file.

---

# ⭐ Support

If you find **Skill Swap** useful or interesting, consider giving the repository a ⭐ on GitHub.

---

**Built with Kotlin, Firebase, Supabase & OneSignal.**
