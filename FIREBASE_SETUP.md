# Firebase Configuration Guide for Global Call

This document provides step-by-step instructions to connect the **Global Call** application with your official Firebase project, unlocking production-grade authentication, Firestore database mirroring, and push notifications.

---

## Step 1: Provision your Firebase Console Project
1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Click **Add Project** and specify a name (e.g., `Global Call`).
3. (Optional) Enable Google Analytics for the project, then click **Create Project**.
4. Once ready, click on the **Android Icon** on the project dashboard to add an Android module target.

---

## Step 2: Register Android Module Credentials
1. Under **Android package name**, enter the exact `applicationId` declared in your gradle script:
   ```
   com.aistudio.globalcall.bkvwqr
   ```
2. (Optional) Enter your SHA-1 signing certificates, which is required if using phone authentication or Google Sign-In:
   * Run the Gradle task in Android Studio to find your SHA-1:
     - Open the terminal drawer and run: `gradle signingReport`
     - Copy the SHA-1 output string and paste it here.
3. Click **Register App**.

---

## Step 3: Bundle `google-services.json`
1. Download the generated configuration file `google-services.json`.
2. Open Android Studio, switch the sidebar view format from **Android** to **Project**.
3. Locate the `google-services.json` from your downloads folder and copy/move it directly inside:
   ```
   [Project Root]/app/google-services.json
   ```
4. Add the Google Services Gradle plugin inside the project and module Gradle builds if you want the Google platform compiler helpers to inject variables (though the app includes robust fallbacks to initialize smoothly!).

---

## Step 4: Configure Database and Authentication Handlers

### 1. Firebase Authentication
* Navigate to Build > **Authentication** -> **Get Started**.
* Under the **Sign-in method** dashboard, verify and enable the following providers:
  - **Email/Password:** Enable and save.
  - **Phone Number:** Enable and save (Allows direct OTP authentication).
  - **Google:** Enable, select your project support email and save (Unlocks one-touch Google Sign-In!).

### 2. Cloud Firestore Signalling Rules
* Go to Build > **Firestore Database** -> **Create Database**.
* Set your location and choose mode (e.g. testing mode, or locked production mode).
* To allow WebRTC SDP packets and candidates to mirror without delays, set the **Firestore Rules** as follows:
  ```javascript
  rules_version = '2';
  service cloud.firestore {
    match /databases/{database}/documents {
      // Allow authenticated users to search profiles, message, and signal peer details
      match /users/{userId} {
        allow read, write: if request.auth != null;
      }
      match /chats/{chatId}/{document=**} {
        allow read, write: if request.auth != null;
      }
      match /calls/{callId}/{document=**} {
        allow read, write: if request.auth != null;
      }
    }
  }
  ```

### 3. Firebase Cloud Messaging (FCM)
* Go to the Project Gears icon > **Project Settings** > **Cloud Messaging**.
* Ensure the Firebase Cloud Messaging API is enabled inside the Google Cloud Console.
* Live calling notifications and foreground alert requests will automatically route through `GlobalCallFcmService` securely.
