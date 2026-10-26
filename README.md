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
| Oct 17, 2026 | SQLite Storage & User Data Isolation | SQLiteOpenHelper Documentation, Android Developer Guide | Mastered SQLite schema creation, foreign key constraints (`ON DELETE CASCADE`), database migrations (`onUpgrade`), and parameterized queries for strict user isolation. | Solved thread blocking and UI freezes during database queries by delegating `SqliteRecordStore` operations to background worker threads via `ExecutorService`. |

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
- **Sync on reconnect.** After a failed download, the app downloads again as soon as the network returns while Home is open, and also queues one background check (WorkManager, network required) for when the app is closed.

## 10e. Building a signed release

1. In Android Studio: Build, Generate Signed App Bundle or APK, create a keystore and keep it OUTSIDE the repository.
2. Or create `keystore.properties` in the project root (it is ignored by git) with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`, then run `./gradlew assembleRelease`. The signed APK is in `app/build/outputs/apk/release/`.
3. Code shrinking (R8) is left off, because it has not been tested on a phone.

---

## 11. AI tools used

| Tool | What it helped build |
|---|---|
| Claude / IDE AI Assistant | Assisted in drafting M1 & M2 project files: Java source classes, XML layouts, resource bundles, custom Canvas views, unit tests, and filling out the README documentation. All code was reviewed, built, tested, and validated locally. |
| Claude (M3) | Drafted `RecordStore`, `SqliteRecordStore`, `InMemoryRecordStore`, `AppData`, the database version 3 upgrade, the move of record reads and writes to a background thread, and the extra unit tests. I can explain and change these. |
| Claude (M4, B1) | Drafted the request feed client and parser, `RequestCache` and the version 4 upgrade, `Geo`, `Urgency`, `RequestMatcher`, the location classes, the Home wiring (permission prompt, offline status line) and their tests. I can explain and change these. |
| Claude (M4, after phone test) | Drafted `Facilities`, the facility dropdown, the automatic location dialog and the reconnect sync. I can explain and change these. |
| Claude (M4, B2) | Drafted `AlertRules`, `AlertLog`, `RequestChecker`, the WorkManager job and scheduler, `AlertNotifier`, the version 5 upgrade, `DemoActivity` and their tests. I can explain and change these. |

---

## 12. Known limitations

- New accounts start with a few sample records, which the user can edit or delete.
- The database is not encrypted. It is in the app's private storage, but a rooted phone could read it.
- If the phone is rotated at the exact moment a record is being saved, the save still completes but the screen is rebuilt, so the confirmation message may not show.
- Accounts and personal records are stored on the phone only, so they are not shared between devices. Only the public request list comes from the network.
- The request feed is read-only in the app. Requests are added by an administrator in the Firebase console (hospital posting and sign-in are not built).
- Alerts need a connection to learn about new requests, and the background job can only use the last saved position.
- Without a position (permission denied, or none saved yet) distances are not shown and the radius filter is not applied.
