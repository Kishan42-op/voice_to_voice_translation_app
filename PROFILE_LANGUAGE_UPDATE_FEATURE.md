# Profile Language Preference Update Feature

## Branch Created
`profile-update-language` - Branch for implementing language preference updates from user profile

## Feature Summary
Users can now update their preferred language directly from their profile screen. The implementation includes a Material Design dialog for language selection with all 11 supported Indian languages.

## Changes Implemented

### 1. **ProfileViewModel** (`ProfileViewModel.java`)
- Added `updateLanguageState` LiveData to track language update operations
- Added `updatePreferredLanguage(PreferredLanguage newLanguage)` method
- Added `getUpdateLanguageState()` public method to expose update state
- Added `clearUpdateLanguageState()` method for state cleanup
- Language updates trigger automatic profile reload

### 2. **UserRepository** (`UserRepository.java`)
- Added `updatePreferredLanguage(String uid, PreferredLanguage preferredLanguage, callback)` method
- Validates language is supported before updating
- Updates Firebase Firestore document with new preferred language
- Fetches updated user document after successful update
- Includes error handling with `AuthErrorMapper`

### 3. **ProfileFragment** (`ProfileFragment.java`)
- Added click listener on language preference text view
- Implemented `showLanguageSelectionDialog()` method with Material Dialog
- Dialog displays all supported languages from `LanguageCatalog`
- Single-choice selection with pre-selected current language
- Updates user preference via ViewModel when language is selected
- Shows success/error feedback via Snackbar
- Loading state management during update

### 4. **Layout Updates** (`fragment_profile_shell.xml`)
- Made language preference field interactive and clickable
- Added `selectableItemBackgroundBorderless` ripple effect for touch feedback
- Added padding to language field for better touch target
- Set `clickable="true"` and `focusable="true"` attributes

### 5. **Dialog Layout** (`dialog_select_language.xml`)
- New layout file for language selection dialog
- Simple LinearLayout with instructional text

## Supported Languages
All 11 Indian languages are available:
- Hindi
- Gujarati
- Marathi
- Bengali
- Tamil
- Telugu
- Kannada
- Malayalam
- Odia
- Punjabi
- Assamese

## User Flow
1. User navigates to Profile screen
2. User clicks on "Preferred language" field
3. Material Dialog appears with all available languages
4. Current language is pre-selected
5. User selects a new language from the list
6. Language is updated in Firebase Firestore
7. Profile automatically reloads with the new language
8. Success message displayed via Snackbar
9. Global models are preloaded for the new language

## Technical Details
- **State Management**: Uses LiveData for reactive updates
- **Database**: Firebase Firestore for persistence
- **UI Component**: Material Design 3 AlertDialog
- **Error Handling**: Comprehensive validation and error messages
- **Loading States**: Progress indicator during update

## Files Modified/Created
- `ProfileViewModel.java` - Enhanced with language update capability
- `UserRepository.java` - Added language update persistence
- `ProfileFragment.java` - Complete rewrite with language selection UI
- `fragment_profile_shell.xml` - Made language field interactive
- `dialog_select_language.xml` - NEW - Dialog for language selection

## Testing Recommendations
1. Verify language selection dialog appears when clicking language field
2. Test selecting each supported language
3. Verify profile reloads with new language
4. Check Firebase Firestore updates correctly
5. Verify success message appears
6. Test error handling with invalid selections
7. Verify loading state shows during update
8. Test with different user preferences

## Future Enhancements
- Language preference sync across devices
- Language preference history
- Language-based content recommendations
- Multi-language support for UI
