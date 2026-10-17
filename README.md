# LifeLink Uganda (Mobile App)

CSC3109 Mobile Applications Development, individual coursework project.
Student: Mulungi Precious Pabire, Reg No. 2024/BCS/199/PS, BCS III, MUST.

LifeLink Uganda is an Android app that connects blood donors, hospitals and blood banks
so that urgent blood needs reach eligible donors quickly.

> Sections marked **WRITE THIS YOURSELF** must be in your own words (coursework section 4).
> Everything else describes what the code actually does; check each statement against the code before you submit.

---

## 1. Problem, users and requirements (WRITE THIS YOURSELF)

Short summary of the problem and evidence, the two personas, and the functional and quality requirements.
Link to the Part A concept note and the Part B design pack in `docs/`.

## 2. Technology and justification

| Item | Choice | Reason |
|---|---|---|
| Platform | Native Android | (write your own reasons: the course, labs, theory sets and exam are all Android, so every lab feeds the project directly; device features such as location and notifications are first-class) |
| Language | Java | (your reasons) |
| UI | XML layouts with Views (ConstraintLayout, LinearLayout, ScrollView, FrameLayout) | (your reasons) |
| Minimum SDK | API 26 (Android 8.0) | Notification channels are mandatory from API 26 (used in M4), `PBKDF2WithHmacSHA256` for password hashing exists from API 26, and Android 8.0 or newer covers the large majority of active phones. |
| Local database | SQLite through `SQLiteOpenHelper` | Required by the coursework, works offline, no server cost, and every SQL statement is visible and explainable. |

## 3. Project structure

```
app/
  build.gradle(.kts)                 Gradle script for the app module: SDK versions, dependencies
  src/main/
    AndroidManifest.xml              Declares the three activities, the launcher screen and permissions
    java/ug/lifelink/app/
      LoginActivity.java             Launcher screen: login, remember me, lifecycle logging
      RegisterActivity.java          Registration form with full validation
      HomeActivity.java              Greeting, status card, record list
      data/DbHelper.java             Creates the SQLite tables (users, records)
      data/UserRepository.java       All user queries, using ? placeholders
      data/SampleData.java           Sample records for M1 (replaced in M2 and M3)
      model/User.java                A registered user
      model/BloodRecord.java         A donation (donor) or a blood request (hospital)
      ui/RecordAdapter.java          ArrayAdapter that fills item_record.xml for the ListView
      util/Validators.java           Validation rules
      util/PasswordHasher.java       PBKDF2 salted password hashing
      util/SessionManager.java       "Keep me signed in" using SharedPreferences
      util/Eligibility.java          90-day donor eligibility rule
      util/SimpleTextWatcher.java    Small helper for text-change events
    res/
      layout/                        activity_login, activity_register, activity_home, item_record
      layout-land/                   Landscape versions of activity_login and activity_home
      values/                        strings, colors, dimens, styles, themes, arrays
      values-night/themes.xml        Same light palette (the MVP ships one theme)
      drawable/                      Vector logo and icons, card and badge shapes
  src/test/java/.../ValidatorsTest.java   Local unit tests
```

## 4. Screens (Milestone 1)

| Screen | Layouts used | Controls | Landscape |
|---|---|---|---|
| Login | ScrollView > ConstraintLayout > LinearLayout | ImageView, TextView, TextInputEditText (email, password), CheckBox, Button | Separate `layout-land` file: brand block left, form right |
| Register | ScrollView > ConstraintLayout > LinearLayout | TextInputEditText, Spinner (blood group), RadioGroup with RadioButtons (role), CheckBox (consent), Button | Scrolls; form column capped at 560dp |
| Home | ConstraintLayout > LinearLayout, ListView | TextView, ImageButton (log out), ListView with `RecordAdapter` | Separate `layout-land` file: header and status left, list right |

## 5. Validation rules

| Field | Rule | Message |
|---|---|---|
| Full name | Required, 2 to 60 characters, letters, spaces, `.`, `'`, `-` only, starts with a letter | `error_name_*` |
| Email | Required, must match `name@domain.tld` | `error_email_*` |
| Email (register) | Must not already exist in the database | `error_email_taken` |
| Phone | Required, Ugandan mobile: `07XXXXXXXX` or `+2567XXXXXXXX`; spaces and dashes ignored; stored as `+256...` | `error_phone_*` |
| Blood group | A real group must be chosen (the first spinner item is only a prompt) | `error_blood_group_required` |
| Role | One of two radio buttons (donor preselected) | none needed |
| Password (register) | 8 to 64 characters, at least one letter and one number | `error_password_*` |
| Confirm password | Must equal the password | `error_confirm_*` |
| Consent | Must be ticked | `error_consent_required` |
| Login | Email must be valid, password must not be empty. A wrong email or password gives one message, so the app does not reveal which emails exist. | `login_failed` |

All rules live in `util/Validators.java` and are covered by `ValidatorsTest`.

## 6. Event handling (Part D)

| Event | Where | Result |
|---|---|---|
| Tap Log in | `LoginActivity.btnLogin` click listener | Validate, check credentials on a worker thread, welcome message, open Home |
| Keyboard Done in password field | `setOnEditorActionListener` | Same as tapping Log in |
| Tap "Create an account" | `tvRegisterLink` click listener | Opens Register |
| Edit a field that shows an error | `SimpleTextWatcher` | Error clears |
| Tap Log in with remember me ticked | `SessionManager.remember` | Next launch skips Login |
| Tap a record in the list | `HomeActivity` item click listener | Message naming the facility |
| Tap log out | `HomeActivity.logout` | Clears remembered user, returns to Login, clears back stack |

## 7. Lifecycle observations (Part E) (WRITE THIS YOURSELF)

Filter Logcat by the tag `LL_LIFECYCLE`. For each action below, copy the lines you actually see
and explain them in your own words.

| What I did | Logcat lines I saw | Explanation |
|---|---|---|
| Launched the app | | |
| Pressed the Home button | | |
| Returned to the app | | |
| Opened Recent apps and swiped back | | |
| Rotated the phone (typed text first) | | |
| Pressed Back on Login | | |

## 8. Learning log (WRITE THIS YOURSELF)

| Date | Topic | Sources I used | What I learned | One problem I solved |
|---|---|---|---|---|
| | Event handling | | | |
| | Activity lifecycle | | | |

## 9. Security and privacy (extended in M3)

| Risk | Control | Where in the code |
|---|---|---|
| Stolen database reveals passwords | Passwords are never stored. Each is salted and hashed with PBKDF2-HMAC-SHA256 (100,000 iterations) | `util/PasswordHasher.java`, `data/UserRepository.create` |
| SQL injection through form input | Every query uses `?` placeholders or `ContentValues`, never string concatenation of user input | `data/UserRepository.java` |
| Data copied out through Android backup | `android:allowBackup="false"` | `AndroidManifest.xml` |
| Unneeded access to the phone | No permissions requested in M1. Later milestones add only location, notifications and internet | `AndroidManifest.xml` |
| Login screen reveals which emails exist | One generic message for wrong email or wrong password | `LoginActivity.onLoginResult` |

Personal data collected: name, email, phone, blood group, role, password hash. Nothing else.
Known trade-off: "Keep me signed in" stores only the user id in private SharedPreferences.

## 10. Offline behaviour (M3), testing (M4), release (M4)

To be completed in later milestones.

## 11. AI tools used

| Tool | What it helped build |
|---|---|
| Claude (Anthropic) | Drafted the M1 project files: Java sources, XML layouts, resources, unit tests and this README skeleton. I reviewed the code, ran it, and can explain and change every part. |

(Edit this table so it is accurate for your own work. List tutorials or other sources you copy from as well.)

## 12. Known limitations

- Home shows sample records in M1. Real records arrive in M2 and M3.
- Data is stored on the phone only, so records are not shared between devices until the REST API step in M4.
