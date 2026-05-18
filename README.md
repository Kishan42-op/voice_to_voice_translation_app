# Voice to Voice Translation App

## Authentication PR

This branch adds a Firebase Authentication flow using Java, XML, MVVM, LiveData, Repository pattern, and Material Design.

### Flow

1. `SplashActivity`
   - Checks the persisted Firebase session.
   - Routes to `AuthActivity` or `MainActivity`.

2. `AuthActivity`
   - Hosts the auth navigation graph.
   - Shows `LoginFragment` and `SignupFragment`.

3. `LoginFragment`
   - Email + password login.
   - Validates fields.
   - Shows loading and error states.

4. `SignupFragment`
   - Name + email + password + confirm password.
   - Creates the Firebase Auth account.
   - Writes `users/{uid}` in Firestore.

5. `MainActivity`
   - Receives the authenticated user session.
   - Supports logout.

### Package structure

- `auth/data/` - auth-related data contracts and state objects
- `auth/repository/` - Firebase Auth and Firestore repositories
- `auth/ui/` - splash, auth host, and auth fragments
- `auth/viewmodel/` - session and auth ViewModels
- `core/` - reusable app state wrappers
- `models/` - Firestore-backed models
- `utils/` - validation and error mapping helpers
- `navigation/` - XML navigation graphs

### Firebase setup

1. Create a Firebase project.
2. Add the Android app with package name `com.example.indicpipeline`.
3. Download `google-services.json` and place it in `app/`.
4. Enable **Email/Password** authentication.
5. Create a **Cloud Firestore** database.
6. Ensure the Firestore rules allow authenticated reads/writes for your auth flow.

### Dependencies added

- Firebase Auth
- Firebase Firestore
- Lifecycle ViewModel
- Lifecycle LiveData
- Navigation Fragment + Navigation UI
- Google Services Gradle plugin

### Notes

- Login sessions persist automatically through Firebase Authentication.
- Signup creates the Firestore document:

```json
users/{uid}
{
  "uid": "...",
  "name": "...",
  "email": "...",
  "createdAt": "server timestamp"
}
```

- The app currently keeps the existing calling logic in `MainActivity`; auth is now layered around it cleanly.

