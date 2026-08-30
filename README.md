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
Accept / Reject Request
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
* Offered skill
* Requested skill
* Trade status
* Timestamp
* User information
* Skill information
* Rating/feedback information

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
* ⭐ Feedback/rating events
* 📢 Important application notifications

**Notification Architecture**

```text
Skill Swap Android App
          │
          ▼
   OneSignal SDK
          │
          ▼
  OneSignal Platform
          │
          ▼
 Android Push Service
          │
          ▼
        User
```

OneSignal provides the notification management and delivery layer for the application.

For Android push delivery, OneSignal uses Firebase Cloud Messaging (FCM) credentials configured through the OneSignal platform. **Firebase Cloud Functions are not required for the notification architecture.**

Official OneSignal documentation: [OneSignal Android SDK Setup](https://documentation.onesignal.com/docs/en/android-sdk-setup)

---

### 📍 Location Support

The application includes Google Places integration for location-related functionality.

This can be used to:

* Select locations
* Display user locations
* Assist users in finding relevant people based on location

---

## 🛡️ Admin Panel

Skill Swap includes a dedicated **Admin Panel** for administrators to monitor and manage the application.

### 📊 Admin Dashboard

The Admin Dashboard provides an overview of the application's current activity.

**Dashboard Statistics**

Administrators can monitor:

* 👥 Total Users
* 🔄 Total Trades
* 🎯 Total Skills
* 🚨 Pending Reports
* 📈 Swap Activity

**Statistics Filters**

The dashboard supports different time periods:

```text
Today
Week
Month
Custom Range
All Time
```

---

### 👥 Admin User Management

The Admin Panel includes a dedicated user management section, accessible through `AdminUsersActivity.kt`, providing administrators with an overview of users registered on the platform.

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
Normal User
     │
     ▼
User Dashboard

Admin User
     │
     ▼
Admin Dashboard
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
| **Supabase**                   | Storage/backend services          |
| **Ktor**                       | Networking                        |
| **Kotlin Serialization**       | JSON serialization                |
| **Glide**                      | Image loading                     |
| **Gradle Kotlin DSL**          | Build configuration               |

---

## ☁️ Backend Architecture

Skill Swap uses Firebase, Supabase, and OneSignal for different application requirements.

```text
                         Skill Swap
                             │
            ┌────────────────┼────────────────┐
            │                │                │
            ▼                ▼                ▼
         Firebase          Supabase        OneSignal
            │                │                │
      ┌─────┼─────┐          │                │
      │     │     │          │                │
      ▼     ▼     ▼          ▼                ▼
     Auth Firestore RTDB   Storage       Notifications
            │
            ▼
        Application
            Data
            │
            ▼
          Chat
```

### 🔥 Firebase

Firebase is used for:

* Authentication
* Cloud Firestore
* Realtime Database
* Storage

Firebase Cloud Firestore is also used for real-time chat functionality.

### 🟢 Supabase

Supabase is integrated into the application for backend/storage-related functionality.

```text
SupabaseClient.kt
SupabaseImageUploader.kt
```

Supabase can be used for application media/storage operations.

### 🔔 OneSignal

OneSignal is responsible for the application's push notification functionality. It provides:

* Push notifications
* Notification targeting
* User identification
* Notification data
* Notification management

---

## 🧩 Project Structure

```text
skill-swap/
│
├── app/
│   │
│   ├── src/
│   │   │
│   │   ├── androidTest/
│   │   │
│   │   └── main/
│   │       │
│   │       ├── java/com/example/sgp/
│   │       │   │
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
│   │       │
│   │       └── res/
│   │
│   ├── build.gradle.kts
│   └── google-services.json
│
├── gradle/
│
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

The application uses Firebase data collections for major application entities.

```text
users
skills
trades
chats
reports
```

**Chat Data**

```text
chats
 │
 └── {conversationId}
       │
       └── messages
             │
             └── {messageId}
```

---

## 🔄 Complete Application Workflow

```text
                         ┌───────────────┐
                         │   Sign Up /   │
                         │     Login     │
                         └───────┬───────┘
                                 │
                                 ▼
                         ┌───────────────┐
                         │    Profile    │
                         │   Completion  │
                         └───────┬───────┘
                                 │
                                 ▼
                         ┌───────────────┐
                         │ Explore Skills│
                         └───────┬───────┘
                                 │
                                 ▼
                         ┌───────────────┐
                         │ Find a User   │
                         └───────┬───────┘
                                 │
                                 ▼
                         ┌───────────────┐
                         │ Create Trade  │
                         └───────┬───────┘
                                 │
                       ┌─────────┴─────────┐
                       ▼                   ▼
                   Accepted             Rejected
                       │
                       ▼
                 ┌───────────────┐
                 │     Chat      │
                 └───────┬───────┘
                         │
                         ▼
                 ┌───────────────┐
                 │ Complete Trade│
                 └───────┬───────┘
                         │
                         ▼
                 ┌───────────────┐
                 │ Rating &      │
                 │   Feedback    │
                 └───────────────┘
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

Create a Supabase project and configure the required storage/backend services. Update the application's Supabase configuration accordingly.

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

Security should be considered carefully before deploying the application to production.

**Recommended practices**

* Never expose private API keys.
* Do not commit service-account credentials.
* Configure proper Firebase security rules.
* Configure proper Supabase access policies.
* Protect user information.
* Secure administrator access.
* Do not place OneSignal REST/API keys inside the Android application.
* Use backend/server-side infrastructure for sensitive operations.
* Keep production secrets outside the Git repository.

---

## 🧪 Testing

The project includes Android testing configuration.

Testing technologies include:

* JUnit
* AndroidX JUnit
* Espresso

Testing can be performed using Android Studio's built-in test and instrumentation testing tools.

---

## 📊 Admin Panel Overview

```text
                     ADMIN PANEL
                          │
             ┌────────────┼────────────┐
             │            │            │
             ▼            ▼            ▼
        Dashboard      Users        Skills
             │
      ┌──────┼───────┬───────────┐
      │      │       │           │
      ▼      ▼       ▼           ▼
    Trades Reports Feedback    Videos
      │
      ▼
   Settings
```

**Admin Modules**

| Module       | Purpose                 |
| ------------ | ------------------------ |
| 📊 Dashboard | Application statistics  |
| 👥 Users     | User management         |
| 🎯 Skills    | Skill management        |
| 🔄 Trades    | Trade monitoring        |
| 🚨 Reports   | Report management       |
| ⭐ Feedback   | Feedback management     |
| 🎥 Videos    | Video management        |
| ⚙️ Settings  | Administration settings |

---

## 📈 Admin Dashboard Statistics

The Admin Dashboard monitors:

```text
Total Users
Total Trades
Total Skills
Pending Reports
Swap Activity
```

Statistics can be viewed using:

```text
Today
Week
Month
Custom Range
All Time
```

---

## 🔮 Future Improvements

* 💬 **Advanced Chat & User Channels** – LinkedIn-style chat with user video/channel pages.
* 🔐 **Chat Security & Moderation** – Encrypted chats, inappropriate-content detection, warnings, and 24-hour chat restriction after 5 warnings.
* 👨‍🏫 **Teaching Limit** – Limit each user to teaching a maximum of 10 users simultaneously.
* 🎨 **UI Redesign** – Modern, attractive, and user-friendly redesign of the complete user interface.
* 🌐 **Multi-Language Support** – Allow users to select their preferred language.
* 💳 **Secure Payments** – Add a complete and secure payment system.
* 🌙 **Dark Mode** – Add Light/Dark theme support for the user side.
* 🔄 **Swap Animation** – Add interactive animations to the trade/swap experience.
* ▶️ **Continue Learning** – Resume videos, playlists, courses, and learning activities from the last position.
* 📜 **Certificates** – Generate certificates after completing skills, courses, or playlists.
* 🔧 **Trade System Improvements** – Fix and optimize the complete trade/swap workflow.
* 🎥 **Trade from Demo Videos** – Allow users to initiate trades directly from demo videos with a preferred schedule.
* 📅 **Smart Trade Scheduling** – Support proposed schedules, one counter-schedule, and final mutual confirmation.
* ⏰ **Meeting Reminders** – Send reminders before scheduled trades or learning sessions.
* 📚 **Multiple Resource Types** – Support trading Notes, Videos, Live Sessions, PDFs, Courses, and other learning resources.
* 📑 **Advanced Playlists** – Organize multiple videos and learning materials into playlists.
* 🏆 **Points System Improvements** – Fix and improve points calculation, deduction, and display.
* 🎬 **Playlist Preview Videos** – Add demo videos to showcase playlist content before access.
* 💰 **Playlist Discount** – Provide playlists at a lower points cost than individual resources.
* 🎁 **Completion Rewards** – Award a **500-point reward** after completing 2 full playlists.

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

**Videos**

```text
UploadVideoActivity.kt
VideoPlayerActivity.kt
PlaylistActivity.kt
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
