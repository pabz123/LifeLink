# LifeLink Uganda (Mobile App)

CSC3109 Mobile Applications Development, individual coursework project.
Student: Mulungi Precious Pabire, Reg No. 2024/BCS/199/PS, BCS III, MUST.

LifeLink Uganda is an Android app that connects blood donors, hospitals and blood banks
so that urgent blood needs reach eligible donors quickly.

---

## 1. Problem, users and requirements

### Problem & Context
In Uganda, medical emergencies such as maternal hemorrhages, severe malaria in children, and road traffic accidents frequently face critical delays due to shortages and supply-chain fragmentation across regional blood banks (e.g., Nakasero, Mbale, Mbarara). Blood inventory levels fluctuate unpredictably, and existing communication relies on manual phone calls or uncoordinated social media appeals.

### Users & Personas
1. **Blood Donor**: Wants to register their blood group, view their donation history, calculate eligibility for their next donation (following the 90-day waiting period rule), and receive real-time notifications for nearby urgent requests.
2. **Hospital / Blood Bank Staff**: Needs to quickly publish urgent blood requests specifying blood group, quantity (units required), medical facility, and emergency level to mobilize local donors.

### Requirements
- **Functional Requirements**:
  - User registration with real-time field validation (name, email, Ugandan mobile number, blood group, role selection, security question/answer, consent).
  - Secure authentication using PBKDF2-HMAC-SHA256 salted password hashing and lockout protection after 5 failed attempts.
  - Two-step password reset via security questions.
  - "Keep me signed in" persistent session management using `SharedPreferences`.
  - SQLite database for user storage (`users` table) and schema readiness for blood donation/request records (`records` table).
  - Role-specific dashboard (Home Activity): Donor view displays 90-day eligibility status ring and donation history; Hospital view displays open urgent requests.
  - Record CRUD management: Add, edit, and delete blood donation/request records.
  - Urgent request detail view supporting implicit intents (Call Hospital via dialler, Open in Maps, Share request).
  - Responsive layouts supporting both portrait and landscape orientation (`layout-land`) and tablets (`values-sw600dp`).

- **Quality & Non-Functional Requirements**:
  - Fast offline performance with local in-memory/SQLite database caching.
  - Security against SQL injection through parameterized queries (`?` placeholders) and database CHECK constraints.
  - WCAG-compliant UI with high-contrast text and dual visual cues (text + color) for status indicators.

---

## 2. Technology and justification

| Item | Choice | Reason |
|---|---|---|
| Platform | Native Android | Aligns directly with the CSC3109 syllabus; provides first-class support for hardware APIs (location, notifications) and offline-first background workers. |
| Language | Java | Core programming language for CSC3109; offers strong OOP structure, multi-threading via `ExecutorService`, and robust Android SDK integration. |
| UI | XML layouts with Views (ConstraintLayout, LinearLayout, ScrollView, ListView) and one custom Canvas View (`EligibilityRingView`) | Declarative layout design; allows clean separation of UI from business logic, custom Canvas graphics for rings, and supports resource qualifiers (e.g. `layout-land`, `values-sw600dp`) for responsive device configurations. |
| Minimum SDK | API 26 (Android 8.0) | Mandatory for `PBKDF2WithHmacSHA256` password hashing algorithm, adaptive icons, notification channels (required in M4), and broad device compatibility covering >95% of active Android devices in Uganda. |
| Local database | SQLite through `SQLiteOpenHelper` | Required by coursework specifications; lightweight, embedded relational database that operates offline with zero server overhead and fully transparent SQL queries. |

---

## 3. Project structure

```
app/
  build.gradle(.kts)                 Gradle script for the app module: SDK versions, dependencies
  src/main/
    AndroidManifest.xml              Declares the six activities, the launcher screen and permissions
    java/ug/lifelink/app/
      SplashActivity.java            Launcher screen: logo animation, then Home or Login
      LoginActivity.java             Login, remember me, link to reset, lifecycle logging
      RegisterActivity.java          Registration form with full validation (role aware)
      ForgotPasswordActivity.java    Two-step password reset using the security question
      HomeActivity.java              Greeting, hero status card, urgent requests, record list, menu
      RequestDetailActivity.java     One urgent request: call, maps, share, "I donated here"
      RecordEditActivity.java        Add, edit or delete one record, with full validation
      data/DbHelper.java             Creates the SQLite tables (users, records) with CHECK constraints
      data/UserRepository.java       All user queries: create, login with lockout, reset, find
      data/AuthResult.java           Outcome of a login attempt (success, invalid, locked)
      data/RecordRepository.java     In-memory records the user can add, edit and delete (SQLite in M3)
      data/SampleData.java           Starter records and sample urgent requests (API replaces them in M4)
      model/User.java                A registered user
      model/BloodRecord.java         A donation (donor) or a blood request (hospital)
      model/UrgentRequest.java       An urgent request shown to matching donors
      ui/EligibilityRingView.java    Custom View: progress ring drawn on a Canvas
      ui/ConfirmDialogFragment.java  Reusable confirmation or information dialog (survives rotation)
      ui/StatusLabels.java           Turns status codes into display text
      ui/RecordAdapter.java          ArrayAdapter that fills item_record.xml for the ListView
      util/Validators.java           Validation rules
      util/BloodGroups.java          Blood group codes, including UNKNOWN and NA
      util/BloodCompatibility.java   ABO and Rh matching of donor to request
      util/PasswordHasher.java       PBKDF2 salted hashing (passwords and security answers)
      util/SessionManager.java       "Keep me signed in" using SharedPreferences
      util/Eligibility.java          90-day donor eligibility rule
      util/SimpleTextWatcher.java    Small helper for text-change events
    res/
      layout/                        activity_splash, activity_login, activity_register,
                                     activity_forgot_password, activity_home, header_home, item_record,
                                     activity_request_detail, activity_record_edit
      menu/menu_home.xml             The app-wide menu on Home (add, about, log out)
      layout-land/                   Landscape versions of activity_login and activity_home
      values/                        strings, colors, dimens, styles, themes, arrays
      values-sw600dp/dimens.xml      Wider list margins on tablets
      values-v31/themes.xml          Splash background for Android 12 and newer
      values-night/themes.xml        Same light palette (the MVP ships one theme)
      drawable/                      Logo, icons, card, hero, badge and avatar shapes
      mipmap-anydpi-v26/             Adaptive launcher icon (with themed monochrome layer)
  src/test/java/.../util/            ValidatorsTest and BloodCompatibilityTest (local unit tests)
  src/test/java/.../data/            RecordRepositoryTest (ownership and rule checks)
```

---

## 4. Screens

| Screen | Layouts used | Controls | Landscape |
|---|---|---|---|
| Splash | ConstraintLayout | ImageView (logo), TextView; fade and scale animation, minimum 1.4 seconds | Same layout, centred |
| Login | ScrollView > ConstraintLayout > LinearLayout | ImageView, TextView, TextInputEditText (email, password), CheckBox, Button | Separate `layout-land` file: brand block left, form right |
| Register | ScrollView > ConstraintLayout > LinearLayout | TextInputEditText, Spinner (blood group, security question), RadioGroup with RadioButtons (role), CheckBox (consent), Button | Scrolls; form column capped at 560dp |
| Forgot password | ScrollView > ConstraintLayout > LinearLayout | TextInputEditText, TextView (question), Buttons; two steps in one screen | Scrolls |
| Request details | ScrollView > ConstraintLayout > LinearLayout | TextViews in a card, four Buttons (call, maps, share, "I donated here"), back arrow | Scrolls; column capped at 560dp |
| Add or edit record | ScrollView > ConstraintLayout > LinearLayout | TextInputEditText (facility, units, note), Spinner (blood group, status), date picker button, Save and Delete buttons | Scrolls; column capped at 560dp |
| Home | ConstraintLayout (top bar) and ListView with a header view | Custom ring View, TextViews, ImageButton (menu), Button (Add), ListView with `RecordAdapter` | Separate `layout-land` file: profile rail left, list right |

---

## 5. Validation rules

| Field | Rule | Message |
|---|---|---|
| Full name | Required, 2 to 60 characters, letters, spaces, `.`, `'`, `-` only, starts with a letter | `error_name_*` |
| Email | Required, must match `name@domain.tld`; stored in lower case | `error_email_*` |
| Email (register) | Must not already exist in the database | `error_email_taken` |
| Phone | Required, Ugandan mobile: `07XXXXXXXX` or `+2567XXXXXXXX`; spaces and dashes ignored; stored as `+256...` | `error_phone_*` |
| Role | One of two radio buttons (donor preselected). Choosing hospital staff hides the blood group block | none needed |
| Blood group (donor) | A real group, or "I do not know my blood group" (stored as `UNKNOWN`). The first spinner item is only a prompt and is rejected | `error_blood_group_required` |
| Blood group (hospital) | Not asked; stored as `NA` | none |
| Password (register) | 8 to 64 characters, at least one letter and one number | `error_password_*` |
| Confirm password | Must equal the password | `error_confirm_*` |
| Security question | One of four questions must be chosen (the prompt position is rejected) | `error_security_question_required` |
| Security answer | 3 to 60 characters; compared after trimming, collapsing spaces and ignoring case | `error_security_answer_*` |
| Consent | Must be ticked | `error_consent_required` |
| Login | Email must be valid, password must not be empty. A wrong email or password gives one message, so the app does not reveal which emails exist. | `login_failed` |
| Reset password | Email must be valid, answer must not be empty, new password follows the same rules as register | `error_*` |
| Record: hospital or blood bank | Required, 2 to 80 characters, letters, numbers and `. , ' & ( ) / -` only | `error_facility_*` |
| Record: blood group | A real group must be chosen. "Unknown" is not accepted, because a donation or request concerns one real group | `error_record_blood_group_required` |
| Record: units | Whole number from 1 to 20 | `error_units_*` |
| Record: date | Required, cannot be in the future (the picker also blocks future days) | `error_date_*` |
| Record: status | Donation is always Completed. A request is Open, Urgent or Fulfilled | enforced in `RecordRepository` |
| Record: note | Optional, at most 120 characters | `error_note_long` |

---

## 6. Event handling (Part D)

| Event | Where | Result |
|---|---|---|
| Tap Log in | `LoginActivity.btnLogin` click listener | Validate, check credentials on a worker thread, welcome message, open Home |
| Keyboard Done in password field | `setOnEditorActionListener` | Same as tapping Log in |
| Tap "Create an account" | `tvRegisterLink` click listener | Opens Register |
| Tap "Forgot password?" | `tvForgotLink` click listener | Opens reset screen with the email already typed |
| Choose a role | `RadioGroup` change listener in `RegisterActivity` | Shows or hides the blood group block |
| Choose blood group or question | `ClearOnSelect` item-selected listener | Clears that field's error |
| Edit a field that shows an error | `SimpleTextWatcher` | Error clears |
| Tap Log in with remember me ticked | `SessionManager.remember` | Next launch skips Login |
| Tap a record in the list | `HomeActivity` item click listener | Opens the edit form for that record |
| Tap Add (or Add in the menu) | `btnAddRecord`, menu item `action_add` | Opens the empty form |
| Tap an urgent request | Click listener on each row | Opens Request details with the request's data in the Intent |
| Tap Call hospital | `RequestDetailActivity.callHospital` | Dialler opens with the number filled in |
| Tap Open in Maps or Share request | `openMaps`, `shareRequest` | Maps search or Android share sheet |
| Tap Choose date | `RecordEditActivity.showDatePicker` | Date picker dialog; future days disabled |
| Tap Save | `RecordEditActivity.attemptSave` | Validates every field, saves, returns to Home with a message |
| Tap Delete | `btnDelete` click listener | Confirmation dialog, then deletes and returns to Home |
| Tap the three-dot button | `HomeActivity.showMenu` | Popup menu: add, about, log out |
| Tap Log out in the menu | `confirmLogout` then `logout` | Confirmation dialog, then clears remembered user, returns to Login, clears back stack |

---

## 7. Lifecycle observations (Part E)

`SplashActivity` is the launcher and finishes itself, so `LoginActivity` is the first screen that logs lifecycle events. Filter Logcat by the tag `LL_LIFECYCLE`.

| What I did | Logcat lines I saw | Explanation |
|---|---|---|
| Launched the app | `Login onCreate, fresh start (no saved state)`<br>`Login onStart: screen becoming visible`<br>`Login onResume: in the foreground, ready for input` | System initializes Activity, inflates view hierarchy, binds controls, and enters foreground interactive state. |
| Pressed the Home button | `Login onPause: losing focus...`<br>`Login onStop: no longer visible` | Activity loses focus and becomes hidden to save battery and RAM. |
| Returned to the app | `Login onRestart: coming back after being stopped`<br>`Login onStart: screen becoming visible`<br>`Login onResume: in the foreground...` | System restarts stopped activity, re-binds UI, and resumes interactive state. |
| Opened Recent apps and swiped back | `Login onPause...`<br>`Login onStop...`<br>`Login onDestroy, isChangingConfigurations=false isFinishing=true` | Swiping away terminates activity permanently, releasing resources. |
| Rotated the phone (typed text first) | `Login onPause...`<br>`Login onStop...`<br>`Login onSaveInstanceState: state saved...`<br>`Login onDestroy, isChangingConfigurations=true...`<br>`Login onCreate...`<br>`Login onStart...`<br>`Login onRestoreInstanceState: views restored...`<br>`Login onResume...` | Rotation triggers configuration change. Activity is recreated; view IDs preserve typed state automatically via `onSaveInstanceState`. |
| Pressed Back on Login | `Login onPause...`<br>`Login onStop...`<br>`Login onDestroy, isChangingConfigurations=false isFinishing=true` | Back pops activity off stack and destroys it. |

---

## 8. Learning log

| Date | Topic | Sources I used | What I learned | One problem I solved |
|---|---|---|---|---|
| Oct 12, 2026 | Event handling & Input Validation | Android Developer Docs (`TextWatcher`, `OnEditorActionListener`), CSC3109 Lecture Notes | Learned how to combine `TextInputEditText` listeners with `TextInputLayout.setError()` to give immediate visual feedback without blocking the UI. | Solved persistent error messages by creating `SimpleTextWatcher` to clear errors dynamically as soon as the user starts editing a field. |
| Oct 14, 2026 | Activity Lifecycle & State Preservation | Android Guide: "Understand the Activity Lifecycle", Logcat filtering | Mastered the lifecycle callbacks (`onCreate` through `onDestroy`) and how configuration changes (e.g. screen rotation) trigger Activity recreation. | Solved losing form input during device orientation changes by assigning explicit `android:id` attributes to all input controls so state is restored via `onSaveInstanceState`. |
| Oct 16, 2026 | Asynchronous SQLite & Hashing | Android `SQLiteOpenHelper` documentation, Java `ExecutorService` | Understood how background threads keep UI smooth when computing 100,000-iteration PBKDF2 password hashes and executing SQLite database operations. | Prevented Application Not Responding (ANR) errors by delegating database queries and password hashing to a single-thread `ExecutorService` background worker. |
| Oct 17, 2026 | Intents, Dialogs & Custom Canvas Views | Android Intents Guide, DialogFragment Docs, Canvas API | Learned how to use implicit intents (`ACTION_DIAL`, `ACTION_VIEW`, `ACTION_SEND`), custom Canvas drawing for progress rings, and DialogFragments that survive device rotation. | Solved dialog dismissal on rotation by replacing standard `AlertDialog` with `DialogFragment` and result listeners. |

---

## 9. Security and privacy (extended in M3)

| Risk | Control | Where in the code |
|---|---|---|
| Stolen database reveals passwords | Passwords are never stored in plain text. Each is salted and hashed with PBKDF2-HMAC-SHA256 (100,000 iterations, 16-byte random salt) | `util/PasswordHasher.java`, `data/UserRepository.create` |
| Stolen database reveals security answers | Answers are normalised, then salted and hashed the same way | `UserRepository.create`, `resetPassword` |
| SQL injection through form input | Every query uses `?` placeholders or `ContentValues`, never string concatenation of user input | `data/UserRepository.java` |
| Bad data written by a bug or a skipped check | Repository re-validates; database CHECK, UNIQUE and FOREIGN KEY constraints | `data/UserRepository.java`, `data/DbHelper.java` |
| Guessing passwords or security answers | After 5 failed attempts the account is locked for 5 minutes. Login and reset share one counter | `UserRepository.authenticate`, `resetPassword` |
| Screens reveal which emails are registered | One generic login message; reset screen shows a stable fake question for unknown email | `LoginActivity`, `ForgotPasswordActivity` |
| One user reads or changes another user's records | Every `RecordRepository` method takes the signed-in user's id and only looks inside that user's own list | `data/RecordRepository.java` |
| Another app opens an internal screen | Only `SplashActivity` is exported; Login, Register, Reset and Home are `exported="false"` | `AndroidManifest.xml` |
| Data copied out through Android backup | `android:allowBackup="false"` | `AndroidManifest.xml` |

---

## 10. Offline behaviour (M3), testing (M4), release (M4)

To be completed in later milestones.

---

## 11. AI tools used

| Tool | What it helped build |
|---|---|
| Claude / IDE AI Assistant | Assisted in drafting M1 & M2 project files: Java source classes, XML layouts, resource bundles, custom Canvas views, unit tests, and filling out the README documentation. All code was reviewed, built, tested, and validated locally. |

---

## 12. Known limitations

- New accounts start with a few sample records, which the user can edit or delete.
- Records are kept in memory (M2), so they are lost when Android ends the app process. SQLite in M3 fixes this.
- Data is stored on the phone only, so records are not shared between devices until the REST API step in M4.
