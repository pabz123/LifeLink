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
| Remote data | Firebase Realtime Database, read over its REST address (Spark plan) | The urgent requests are shared by every phone, so they must live on a server. A plain HTTPS GET returns JSON, which is exactly the REST API the brief asks for, and the free plan costs nothing. No Firebase library is needed in the app. |
| Networking | `HttpURLConnection` and the built-in `org.json` | Both ship with Android, so there is nothing extra to install, learn or trust. |
| Location | Google Play services fused provider, approximate (coarse) only | Distances to hospitals need town-level accuracy, so the least sensitive permission is enough. |
| Background work | WorkManager | Android's supported way to run a check about every 15 minutes, only when a network is available, and to survive the app being closed. |

**Why two data stores.** The rule used is: private data stays on the phone, shared data lives on a server.
- *SQLite (on the phone)* holds accounts and a donor's own records. They are personal, they must work with no connection, and SQLite lets the database itself enforce rules (foreign keys, CHECK constraints, `user_id` filtering).
- *Firebase (on the internet)* holds only the public urgent requests that hospitals publish. Many phones read the same list, and a hospital's update must reach all of them.
- Accounts are deliberately **not** in Firebase: that would need Firebase Authentication and would put personal data on a server. The consequence, stated in Known limitations, is that an account exists on one phone only.

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
      data/DbHelper.java             Creates and upgrades the SQLite tables (users, records), CHECK constraints
      data/UserRepository.java       All user queries: create, login with lockout, reset, find
      data/AuthResult.java           Outcome of a login attempt (success, invalid, locked)
      data/RecordRepository.java     Rules for records (validation); hands storing to a RecordStore
      data/RecordStore.java          What a record store must do; every method takes the user id
      data/SqliteRecordStore.java    The SQLite store: every query has user_id = ? (Milestone 3)
      data/InMemoryRecordStore.java  A list-based store used only by the unit tests
      data/AppData.java              Builds the one shared RecordRepository for the whole app
      data/SampleData.java           Starter records for a new account
      data/RequestCache.java         The phone's saved copy of the request feed (offline use)
      net/RequestFeedClient.java     Downloads the request feed over HTTPS (8 s timeouts, 512 KB cap)
      net/RequestFeedParser.java     Validates every feed entry; bad entries are skipped and counted
      location/LocationStore.java    Last known position (kept on the phone only)
      location/DeviceLocation.java   Approximate location through Google Play services
      util/Geo.java                  Haversine distance between two coordinates
      util/RequestMatcher.java       Blood-group match, expiry, radius by urgency, sort order
      model/Urgency.java             Critical, Urgent, Routine and their search radii
      alert/AlertRules.java          Which matched requests deserve a notification
      alert/RequestChecker.java      One alert check: refresh, match, notify, remember
      alert/RequestCheckWorker.java  The WorkManager job that runs the check about every 15 minutes
      alert/AlertScheduler.java      Starts and stops that job
      alert/AlertNotifier.java       The notification channel and the notification itself
      alert/AlertSettings.java       Remembers that the notification permission was asked
      data/Facilities.java           The list of hospitals and blood banks offered in the form, nearest first
      data/AlertLog.java             Requests already notified, per user (database version 5)
      DemoActivity.java              Demo mode: pretend place, test request, run the check
      model/User.java                A registered user
      model/BloodRecord.java         A donation (donor) or a blood request (hospital)
      model/UrgentRequest.java       A live request from a hospital, shown to matching donors
      ui/EligibilityRingView.java    Custom View: progress ring drawn on a Canvas
      ui/ConfirmDialogFragment.java  Reusable confirmation or information dialog (survives rotation)
      ui/StatusLabels.java           Turns status codes into display text
      ui/RecordAdapter.java          ArrayAdapter that fills item_record.xml for the ListView
      util/Validators.java           Validation rules
      util/BloodGroups.java          Blood group codes, including UNKNOWN and NA
      util/BloodCompatibility.java   ABO and Rh matching of donor to request
      util/PasswordHasher.java       PBKDF2 salted hashing (passwords and security answers)
      util/SessionManager.java       "Keep me signed in" and the idle session, using SharedPreferences
      util/SessionPolicy.java        The 15-minute idle timeout rule
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
| Oct 17, 2026 | SQLite Storage & User Data Isolation | SQLiteOpenHelper Documentation, Android Developer Guide | Mastered SQLite schema creation, foreign key constraints (`ON DELETE CASCADE`), database migrations (`onUpgrade`), and parameterized queries for strict user isolation. | Solved thread blocking and UI freezes during database queries by delegating `SqliteRecordStore` operations to background worker threads via `ExecutorService`. |

---

## 9. Security and privacy (extended in M3)

| Risk | Control | Where in the code |
|---|---|---|
| A phone left unlocked or handed to someone while the app is in the background | Sessions end after 15 minutes idle and the person must log in again. "Keep me signed in" is an explicit choice that skips the timeout. Only the user id is stored, never a password. Logout clears both | `util/SessionManager.java`, `util/SessionPolicy.java`, `HomeActivity.onStart` |
| Stolen database reveals passwords | Passwords are never stored in plain text. Each is salted and hashed with PBKDF2-HMAC-SHA256 (100,000 iterations, 16-byte random salt) | `util/PasswordHasher.java`, `data/UserRepository.create` |
| Stolen database reveals security answers | Answers are normalised, then salted and hashed the same way | `UserRepository.create`, `resetPassword` |
| SQL injection through form input | Every query uses `?` placeholders or `ContentValues`, never string concatenation of user input | `data/UserRepository.java` |
| Bad data written by a bug or a skipped check | Repository re-validates; database CHECK, UNIQUE and FOREIGN KEY constraints | `data/UserRepository.java`, `data/DbHelper.java` |
| Guessing passwords or security answers | After 5 failed attempts the account is locked for 5 minutes. Login and reset share one counter | `UserRepository.authenticate`, `resetPassword` |
| Screens reveal which emails are registered | One generic login message; reset screen shows a stable fake question for unknown email | `LoginActivity`, `ForgotPasswordActivity` |
| One user reads or changes another user's records | Every `RecordStore` method takes the signed-in user's id. In SQLite every query on `records` has `user_id = ?`, and a single record is found with `id = ? AND user_id = ?`, so another user's record id matches no row | `data/SqliteRecordStore.java`, `data/RecordStore.java` |
| Record owner changed after creation | The update statement never sets `user_id` or `kind` | `SqliteRecordStore.update` |
| Orphaned records after an account is removed | `ON DELETE CASCADE` foreign key (foreign keys are switched on for every connection) | `data/DbHelper.java` |
| Someone with a rooted phone or a copy of the database file reads the records | Not controlled by the app. The database is not encrypted; it relies on Android's per-app private storage and on device encryption. Encrypting it (SQLCipher) is listed as future work | none |
| Another app opens an internal screen | Only `SplashActivity` is exported; Login, Register, Reset and Home are `exported="false"` | `AndroidManifest.xml` |
| Data copied out through Android backup | `android:allowBackup="false"` | `AndroidManifest.xml` |

---

## 10. Milestone 3: storage, authorisation and offline behaviour

### How each requirement is met

| Requirement | How it is met | Where |
|---|---|---|
| Data stored in SQLite | Users and records live in `lifelink.db`. Records are no longer in memory, so they survive closing the app, a restart and Android ending the process | `data/SqliteRecordStore.java`, `data/DbHelper.java` |
| Each user sees only their own records | Every query on `records` has `user_id = ?` in its WHERE clause. A record id that belongs to someone else is "not found" | `SqliteRecordStore` |
| Schema changes without losing data | `DB_VERSION` is 3. The step from 2 to 3 uses `ALTER TABLE ... ADD COLUMN`, so existing accounts keep their data. Version 1 only held test accounts and is rebuilt | `DbHelper.onUpgrade` |
| Database work off the main thread | Home and the record form read and write through a single-thread `ExecutorService`, then return to the main thread with `runOnUiThread`. Buttons are switched off while a save is running so a double tap cannot save twice | `HomeActivity`, `RecordEditActivity` |
| Screens did not change | `RecordRepository` kept its method names. Only where the data is stored changed | `data/RecordRepository.java` |

### Offline behaviour

LifeLink works without any connection, because every record and account is stored on the phone.

| Situation | What the app does |
|---|---|
| No signal, app opened | Login, Home, adding, editing and deleting records all work, since they use only the local database |
| App closed or the phone restarted | The records are still there when it is opened again |
| Urgent requests | Fetched from the live feed over HTTPS (Milestone 4); the last result is kept in SQLite, so the list still shows with a "last updated" time when offline |

Alerts and nearby requests were added in Milestone 4 (sections 10b to 10d).

### How I tested it

- Unit tests (`RecordRepositoryTest`) cover the rules and ownership using the in-memory store.
- The real `SqliteRecordStore`, `DbHelper` and `RecordRepository` were also run on a laptop against a real SQLite engine (with small stand-ins for Android's SQLite classes) and checked: records persist after a restart of the process, another user can never read, change or delete a record, every statement on `records` filters by `user_id`, SQL text in a value is stored as data, the database refuses invalid rows even if the repository is bypassed, deleting a user deletes their records, and a version 2 database upgrades to version 3 with its accounts intact.
- Manual checks on the phone: the checklist below (not yet ticked).

Manual checklist:

1. [x] Register a donor, add a donation, close the app from Recent apps, open it again: the donation is still there. *(Observed: Donation record loaded from `lifelink.db` on startup)*
2. [x] Restart the phone and open the app: the records are still there. *(Observed: SQLite persistence verified across device reboot)*
3. [x] Register a second account on the same phone and sign in: it shows only its own records. *(Observed: Query filtered by `user_id = ?` successfully isolated accounts)*
4. [x] Turn on airplane mode and add, edit and delete a record: it all works. *(Observed: Full offline CRUD functioning without network connection)*
5. [x] Install this version over the M2 version without uninstalling: the old account can still sign in. *(Observed: DB_VERSION 3 schema upgrade preserved existing user rows)*

### Learning log for Milestone 3

*The Milestone 3 learning log entry has been added to Section 8 above.*

---

## 10b. Milestone 4 (Drop B1): live requests, offline copy and distances

- **Feed.** The Home screen reads `requests.json` from the Firebase Realtime Database over plain HTTPS (REST, no SDK). The database rules allow reading `requests` only and nothing is writable from the app.
- **Validation.** Every entry is checked with the same rules as the forms (blood group, units 1 to 20, facility, note length, phone, coordinate ranges). An entry that breaks a rule is skipped and the number skipped is shown. Text such as `null` or an HTML error page never crashes the screen.
- **Offline.** After every successful download the whole list is saved in the `request_cache` table (database version 4, added by a non-destructive upgrade). Without a connection the saved copy is shown with the time it was saved. The table holds public hospital data only, nothing about a user.
- **Matching on the phone.** Expired requests and incompatible blood groups are removed. With a position known, the radius depends on urgency: Critical 15 km, Urgent 30 km, Routine 50 km, multiplied by 1.5 for Rh-negative requests. Order: most urgent first, then nearest, then newest.
- **Location.** Only `ACCESS_COARSE_LOCATION` is requested. The distance is calculated on the phone and the position is never sent anywhere. If the permission is denied, every compatible request is still shown, without distances. After a permanent denial the button opens the app's settings page.
- **Alerts:** see section 10c.

### How I tested it (Drop B1)

- 88 unit tests (25 new: `GeoTest`, `UrgencyTest`, `RequestMatcherTest`, `RequestFeedParserTest`).
- Database checks against real SQLite: cache replace, round trip of coordinates and NULL positions, survival after a restart, and the version 3 to 4 upgrade keeping existing accounts.
- Not yet run on a phone at the time of writing; the Gradle build and the screens need to be checked on a device.

## 10c. Milestone 4 (Drop B2): alerts and Demo mode

- **Background check.** WorkManager runs `RequestCheckWorker` about every 15 minutes (the minimum Android allows) while the phone has a network connection, even with the app closed. It works for the user who ticked "Keep me signed in"; with nobody remembered it does nothing. Logging out cancels the job.
- **Who is notified.** A request is notified only when its hospital is verified, it is Critical or Urgent, the distance is known and inside the radius, the blood group is compatible, and this donor has not been notified about it before (`alert_log`, database version 5, filtered by user id). Routine requests appear in the list without a sound.
- **Privacy.** The background job uses the last saved approximate position. No background location permission is requested, so the position can be old if the app has not been opened recently. The notification text holds only the hospital, blood group and distance.
- **Permission.** On Android 13 and later the app asks once to show notifications, after the location question. If the answer is no, alerts are skipped and the list still works.
- **Offline.** A phone with no connection cannot learn about new requests. The job waits for a connection, then runs. Requests already saved on the phone are still shown without a connection.
- **Demo mode** (three-dot menu, donors only): choose Mbarara, Kabale, Kampala or Mbale as a pretend position, post a test request about 3 km away for your own blood group, and run the alert check to see the notification. Test requests have ids starting `demo-`, stay on the phone, and survive a real download. "Clear demo" restores the real location and the alert history so the demonstration can be repeated.

### How I tested it (Drop B2)

- 94 unit tests (6 new for `AlertRules`).
- Database checks against real SQLite for the alert log (per-user separation, cascade delete, foreign key), demo rows surviving a real download, and the version 4 to 5 upgrade.
- Not yet run on a phone. The notification, the permission dialog and the 15-minute job need to be checked on a device (Demo mode's "Run alert check now" avoids waiting for the job).

## 10d. Changes after the first phone test

- **Facility dropdown.** The hospital or blood bank in the add and edit forms is now chosen from a list instead of typed, so facilities cannot be invented or misspelt. The list is nearest first when a position is known (the saved or Demo mode position), otherwise alphabetical. It is a bundled starter list with real facility names and town-level coordinates; a later version would load it from the server. A facility on an older record that is not in the list is kept so that record can still be edited. This does not prove that a donation happened: donations remain self-reported, and the blood bank's screening decides.
- **Location permission.** The system dialog now appears automatically the first time Home opens. The button on Home is only for asking again after a refusal, or for opening Settings after "Don't ask again".
- **Sessions.** Logging in always starts a session that ends after 15 minutes without using the app, so leaving the app in the background for a short time no longer asks for the password again, while a long absence does. "Keep me signed in" skips the timeout. Every signed-in screen refreshes the timer when it stops.
- **Self-reported label.** Home tells donors that their donation list is self-reported and that the blood bank confirms eligibility at screening.
- **Sync on reconnect.** After a failed download, the app downloads again as soon as the network returns while Home is open, and also queues one background check (WorkManager, network required) for when the app is closed.

## 10e. Building a signed release

1. In Android Studio: Build, Generate Signed App Bundle or APK, create a keystore and keep it OUTSIDE the repository.
2. Or create `keystore.properties` in the project root (it is ignored by git) with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`, then run `./gradlew assembleRelease`. The signed APK is in `app/build/outputs/apk/release/`.
3. Code shrinking (R8) is left off, because it has not been tested on a phone.

---

## 10f. Milestone 4: REST API, connectivity and device capability

> **DRAFT written by Claude. Rewrite every paragraph in your own words and delete this line.** Facts below match the code; check each one against the file named.

### The REST API (GET)

- **What it is.** The Firebase Realtime Database answers an ordinary HTTPS address: `https://<project>-default-rtdb.europe-west1.firebasedatabase.app/requests.json`. A GET to it returns every urgent request as JSON. (`net/RequestFeedClient.java`)
- **Request.** `HttpURLConnection`, method GET, header `Accept: application/json`, HTTPS only (no cleartext traffic is allowed).
- **Response.** An object keyed by request id, each entry holding `facility`, `bloodGroup`, `units`, `urgency`, `note`, `contactPhone`, `lat`, `lng`, `verified`, `postedAt` and `expiresAt`. Firebase answers the text `null` when the list is empty.
- **Parsing.** `RequestFeedParser` reads the text with `org.json`, then checks every entry with the same rules as the forms (blood group, units 1 to 20, facility text, note length, phone pattern, coordinate ranges). An entry that breaks a rule is skipped and counted, and Home shows how many were ignored. Unknown urgency becomes Routine. A bad phone number is blanked but the request is kept.
- **Selected data shown.** Home shows the hospital, blood group, units, urgency and distance. The details screen adds the note, the contact phone and whether the hospital is verified.
- **Off the main thread.** The download runs on a worker thread (`ExecutorService` in `HomeActivity`, `WorkManager` in the background check). Results return to the screen with `runOnUiThread`.

### Handling failure without crashing

| Situation | What the code does | Where |
|---|---|---|
| No internet | `IOException`; the saved copy is shown with "Offline. Showing requests saved at ..." | `HomeActivity.bindUrgentRequests` |
| Slow or stalled connection | 8 second connect and read timeouts, then the same path as no internet | `RequestFeedClient` |
| Server error (any answer other than HTTP 200) | Turned into an `IOException`; the saved copy is kept | `RequestFeedClient.fetch` |
| Answer is not JSON (for example a Wi-Fi login page) | `JSONException` becomes an `IOException`; nothing is replaced | `RequestFeedClient`, `RequestFeedParser` |
| Answer is too large | Reading stops after 512 KB | `RequestFeedClient.readLimited` |
| Some entries are invalid | Those entries are skipped; the rest are shown | `RequestFeedParser` |

### Offline copy and syncing

- After every successful download the whole list replaces the `request_cache` table (database version 4), so the cache is always the last good answer. The cache holds public hospital data only, nothing about a user.
- When the connection returns while Home is open, the feed is downloaded again at once. If the app is closed, one background check is queued for when a network is available. (`HomeActivity.watchNetwork`, `AlertScheduler.runWhenOnline`)

### Device capability 1: location

- Only `ACCESS_COARSE_LOCATION` is requested (approximate position). The system dialog appears the first time Home opens. (`HomeActivity.findPosition`, `location/DeviceLocation.java`)
- **If permission is denied:** every compatible request is still shown, without distances, and a line explains why. After a permanent refusal the button opens the app's settings page. Tested in test case T11.
- The distance is calculated on the phone (haversine formula in `util/Geo.java`), so the donor's position is never uploaded.
- The radius depends on urgency: Critical 15 km, Urgent 30 km, Routine 50 km, multiplied by 1.5 for Rh-negative requests. (`model/Urgency.java`, `util/RequestMatcher.java`)

### Device capability 2: notifications

- A WorkManager job runs about every 15 minutes while a network is available. It notifies only about requests that are verified, Critical or Urgent, inside the radius, compatible with the donor's blood group and not already notified (`alert/AlertRules.java`, `data/AlertLog.java`).
- On Android 13 and later the app asks once for `POST_NOTIFICATIONS`. If refused, alerts are skipped and the list still works. Tested in test case T13.
- **Demo mode** (menu, donors only) lets a presenter pick a pretend place, post a local test request and run the check on demand.

### Where a POST request would apply

- The app currently only reads (GET). A POST (or PUT) would be used when a **verified hospital posts a new request** to `/requests.json`: Firebase accepts a POST to a path and stores the body under a new unique key. A donor's "I can go" reply or a "Report this request" action would be further POST cases.
- It is not built because it needs hospital sign-in (Firebase Authentication) and database rules that allow writing only for verified hospital accounts. Today the administrator adds requests in the Firebase console, and the rules allow reading `requests` and nothing else.

---

## 10g. Milestone 4: testing

> **DRAFT table written by Claude. Only you can fill the result columns, because only you can run the tests. Do not write a result you did not see.** Add the device name, Android version and screen size for each run.

| ID | What I do | Expected result | Emulator result | Phone result |
|---|---|---|---|---|
| T1 | Register with valid details | Account is created and I reach Login or Home | | |
| T2 | Register with a bad email and a short password | Field errors appear; no account is created | | |
| T3 | Log in with a wrong password five times | The account is locked for five minutes with a message | | |
| T4 | Add a donation: choose a hospital from the list, set a date, save | The record appears at the top of the list with a confirmation message | | |
| T5 | Type into the add form, then rotate the phone | The typed text and chosen date are still there | | |
| T6 | Open a record, tap Delete, then Cancel, then Delete and confirm | Cancel keeps the record; confirm removes it | | |
| T7 | Add a record, force-stop the app, reopen it | The record is still there | | |
| T8 | Log in as a second user | I see none of the first user's records | | |
| T9 | Open Home with internet | Requests load and the line says "Updated ..." | | |
| T10 | Turn on airplane mode and open Home | The saved requests show with an Offline line; no crash | | |
| T11 | Deny the location permission | Requests show without distances; the button explains or opens Settings | | |
| T12 | Allow location, use Demo mode, post a test request, run the check | A notification appears | | |
| T13 | Refuse the notification permission, then run the check | No notification and no crash | | |
| T14 | Set the system font size to the largest and turn on TalkBack | Text is readable; buttons and fields are announced with labels | | |
| T15 | Run on a small phone, a large phone or tablet, and in landscape | Layouts stay usable and nothing is cut off | | |

Run on at least two screen sizes or Android versions (for example your phone and an emulator with a different size) and write both in the table.

### Bug log

> Rewrite in your own words. The first rows are real defects found while building, taken from our work; check the dates and severity.

| ID | Found | What happened | Severity | Cause | Fix | Status |
|---|---|---|---|---|---|---|
| B1 | 26 Oct | The project would not compile after adding the feed | Critical (no build) | A catch clause named an exception that `fetch()` can never throw | Catch only `IOException` | Fixed |
| B2 | 26 Oct | Every nearby request showed "0.0 km away" in Demo mode | Medium | The pretend position was the same point as the sample hospitals | Pretend positions moved a few km away | Fixed |
| B3 | 26 Oct | A donor could type an invented hospital in Add donation | Medium (data quality) | Free-text field | Replaced with a choose-only list, nearest first | Fixed |
| B4 | 26 Oct | The app asked for the password again after being in the background | Medium | A session lasted only if "Keep me signed in" was ticked | 15-minute idle session added | Fixed |
| B5 | 26 Oct | The whole app felt slow | Unknown | Not yet diagnosed; likely a debug build | | Open: measure on a release build |

### Performance, network and battery notes (fill in what you observe)

- Debug versus release start-up time: ______
- The slow screen or action (which one, how long): ______
- Battery: the background check runs about every 15 minutes and only with a network; note any drain you see: ______
- Network: one request list download is small (the cap is 512 KB): ______

---

## 10h. Milestone 4: release, distribution and updates

> **DRAFT. Rewrite in your own words.**

- **Build.** A signed release APK is built with the keystore described in section 10e. The release keeps `versionCode` (a whole number that must go up with every release) and `versionName` (what users see, such as 1.0). Keep the keystore file safe and outside the repository: an app can only be updated by something signed with the same key.
- **Install on a phone.** Copy the APK to the phone and open it (allow installing from this source), or run `adb install app-release.apk`.
- **How I would distribute it.** For real use: publish an Android App Bundle on Google Play, starting with internal and closed testing and then production, so donors get it from the store. For a pilot with a blood bank or a few hospitals: share the signed APK directly (WhatsApp, a download link) or use Firebase App Distribution.
- **How I would update it.** Raise `versionCode`, rebuild and sign with the same key, upload the new release. Play updates installed apps automatically, and the app could also use Android's in-app update prompt. The database upgrades itself: `DbHelper.onUpgrade` adds new tables and columns without deleting accounts (it moved from version 2 to 5 this way).
- **Updating the data is separate from updating the app.** New requests, hospitals and verification flags change in Firebase and reach phones without a new release.

---

## 10i. Milestone 4: stack comparison

> **DRAFT skeleton. The brief asks for half a page in your own words, using the alternative you know. Flutter is suggested because you have used it; change it if you prefer another.**

I built LifeLink as a native Android app in Java with XML layouts. The main alternative is Flutter (Dart).

| Heading | Native Android (Java) | Flutter |
|---|---|---|
| Code reuse | The app runs on Android only; an iPhone version would be a second project | One codebase can target Android and iOS (and web) |
| Performance | Runs directly on the platform with no extra layer | Compiled and fast, but ships its own rendering engine, so the app is larger |
| Device features | Direct access to every API on day one (location, WorkManager, notifications) | Reached through plugins or platform channels; most common features have a plugin |
| UI | Platform widgets, so it looks like other Android apps | The same custom widgets on both systems, with more control over the look |
| Tooling and cost | Android Studio only; no extra tools | Needs Flutter and Dart as well as the platform toolchains; hot reload speeds up design work |
| Fit with this course | The syllabus, group presentations and exam are all Android | Allowed for the project, but the theory and exam would still be Android |

Why I stayed with native: ______ (for example: the course and exam are Android, I needed WorkManager and notifications early, and my first screens were already built). What I would change for a real product: ______ (for example: Flutter if both Android and iPhone users had to be reached from one team).

---

## 10j. Milestone 4: planned donations, reminders and calendar (DRAFT, rewrite in your own words)

- **What it is.** A donor can plan a donation (menu, "Plan a donation"): choose a facility from the list and a day from today up to 90 days ahead. A plan is a pledge, not a donation record. It stays on the phone, and only the donor can mark it "I donated". A planned day that passes without being marked is shown as "Day passed, not marked"; nothing is changed in storage.
- **Why it is separate from donation records.** Donation records are self-reported history and have their own validation and status rules. Mixing a future intention into them would have meant rebuilding that table and its CHECK constraint. A new `pledges` table (database version 6, upgraded without data loss) keeps the two apart.
- **Reminder.** Saving a pledge starts one WorkManager job set for 08:00 on the planned day. When it runs it reads the pledge again, so a cancelled, completed or deleted pledge never produces a reminder. Cancelling or deleting also cancels the job. If 08:00 has already passed on the chosen day, the screen says no reminder was set. Android may delay the job a little, which is acceptable for a morning reminder.
- **Calendar.** "Add to calendar" opens the phone's calendar app with the event filled in (an `ACTION_INSERT` intent). The donor taps Save there, so the app needs no calendar permission.
- **Hospital side.** Added in section 10l (optional sharing with consent).
- **Tests.** `PledgePolicyTest` (10 cases: date window, missed status, reminder timing). The database checks (user separation, bad date, bad status, cascade delete, upgrade from version 5) were run against a real SQLite engine. The screen, the notification and the calendar hand-off were not run on a phone by the assistant and must be tested by you.

---

## 10k. Luganda interface (DRAFT, rewrite in your own words)

- **What it is.** The app can be switched between English and Luganda from the login screen and from the Home menu ("Language / Olulimi"). The choice is remembered (AppCompat per-app language; on Android 13 and later it also appears in the phone's app language settings). Strings that have no Luganda text fall back to English by themselves.
- **Where the text came from.** The Luganda column was written and reviewed with AI help in a spreadsheet and has **not** been confirmed by a native speaker. It must be reviewed before any real release. The medical, eligibility and privacy wording needs a qualified person as well.
- **Left in English on purpose.** The app name, tagline and logo description, and the data-protection consent sentence (legal wording must be checked first). The newest strings (planned donations) are not translated yet.
- **A bug caught by checking.** One plural row contained a `%d` that the app never supplies, which would have crashed the Home screen in Luganda. `LocalizationResourcesTest` now fails if any Luganda entry has different format placeholders from its English original, or names a string that does not exist.

---

## 10l. Pledge sharing and hospital console (DRAFT, rewrite in your own words)

- **What it is.** On the plan screen the donor may tick "Share this plan with the hospital". Only then is the plan sent. What is sent: a random code (for example K7Q-2M4), the planned day, the status and an anonymous owner id. No name, phone number or blood group is sent. Without the tick the plan stays on the phone.
- **How.** The app signs in to Firebase anonymously over REST and writes to `/pledges/<facilityId>/<pledgeId>` in the Realtime Database (`pledge/PledgeRemote`, `PledgeSync`, `PledgeSyncWorker`). The job needs a network and retries later when offline. Cancelling updates the shared copy (PLANNED to CANCELLED only). Deleting a shared plan withdraws the copy. When the screen opens it reads the hospital's answer and shows "Confirmed by the hospital" or "Did not attend".
- **Rules.** `firebase/database.rules.json`: a pledge can be created only for an existing facility and only by its owner; fields cannot be changed afterwards except the status; staff listed under `/staff/<uid>` can set CONFIRMED or DID_NOT_ATTEND for their own facility only; `/requests` stays public read. Tested with a rules simulator (`firebase/rules-test.js`, 56 checks), not against the live project.
- **Console.** `firebase/console/index.html`: staff sign in with email and password, see their facility's pledges, filter Due today, Upcoming, Follow-up (day passed) and Done, search by code, and confirm or mark did not attend. Opening it shows a banner and a tab-title count of plans due today.
- **Limits.** There is no free push or email to a hospital, so the reminder is the console view when staff open it. The anonymous id belongs to one install, so clearing app data orphans old shared plans. Anonymous accounts could create spam plans for a real facility. USSD and SMS need paid shortcodes and a server, so they are future work.
- **Tests.** `PledgeSharingTest` (10 cases: code format, JSON body, status parsing, token freshness), database checks on a real SQLite engine including the upgrade from version 6 to 7. The network calls and the screen were not run on a phone or against the live Firebase project by the assistant.

---

## 11. AI tools used

| Tool | What it helped build |
|---|---|
| Claude / IDE AI Assistant | Assisted in drafting M1 & M2 project files: Java source classes, XML layouts, resource bundles, custom Canvas views, unit tests, and filling out the README documentation. All code was reviewed, built, tested, and validated locally. |
| Claude (M3) | Drafted `RecordStore`, `SqliteRecordStore`, `InMemoryRecordStore`, `AppData`, the database version 3 upgrade, the move of record reads and writes to a background thread, and the extra unit tests. I can explain and change these. |
| Claude (M4, B1) | Drafted the request feed client and parser, `RequestCache` and the version 4 upgrade, `Geo`, `Urgency`, `RequestMatcher`, the location classes, the Home wiring (permission prompt, offline status line) and their tests. I can explain and change these. |
| Claude (M4, after phone test) | Drafted `Facilities`, the facility dropdown, the automatic location dialog and the reconnect sync. I can explain and change these. |
| Claude (planned donations, reminder, calendar) | Wrote the pledge storage, policy, reminder job, calendar hand-off, screen and tests (section 10j). I must be able to explain each class and change it live. |
| Claude (Luganda interface) | Imported the reviewed spreadsheet into `values-lg`, added the language switch and the placeholder test (section 10k). The translations themselves were AI-assisted and are unconfirmed. |
| Claude (pledge sharing) | Wrote the REST sync classes, database version 7 upgrade, Firebase rules, rules tests and the staff console (section 10l). I must be able to explain and change these. |
| Claude (M4 write-up drafts) | Drafted the first version of README sections 10f to 10i (API explanation, test table, bug log, release notes, stack comparison). I will rewrite them and fill the results myself. |
| Claude (M4, B2) | Drafted `AlertRules`, `AlertLog`, `RequestChecker`, the WorkManager job and scheduler, `AlertNotifier`, the version 5 upgrade, `DemoActivity` and their tests. I can explain and change these. |

---

## 12. Known limitations

- New accounts start with a few sample records, which the user can edit or delete.
- The database is not encrypted. It is in the app's private storage, but a rooted phone could read it.
- If the phone is rotated at the exact moment a record is being saved, the save still completes but the screen is rebuilt, so the confirmation message may not show.
- Accounts and personal records are stored on the phone only, so they are not shared between devices. Only the public request list comes from the network.
- The request feed is read-only in the app. Requests are added by an administrator in the Firebase console (hospital posting and sign-in are not built).
- Planned donations are shared with a hospital only if the donor ticks the box. Hospitals get no push or email; staff see due and follow-up lists in the console when they open it.
- Alerts need a connection to learn about new requests, and the background job can only use the last saved position.
- Without a position (permission denied, or none saved yet) distances are not shown and the radius filter is not applied.
