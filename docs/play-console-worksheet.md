# Google Play Console declaration worksheet

Draft reviewed against the repository and current Google guidance on 2026-10-07.
This is a worksheet for the owner, not a record of submitted declarations. Recheck
the release artifact and the wording shown in Play Console before submitting.

## Evidence snapshot

- App: **Phone Sense**, application identifier `ca.mattmccormick.phone_sense`,
  version `0.1.0` (`versionCode` 1).
- Android versions: minimum Application Programming Interface (API) level 26;
  target API level 36. The current Play requirement for ordinary phone apps is
  API level 36 for new apps and updates as of 2026-08-31.
- Manifest permissions: `PACKAGE_USAGE_STATS` and `POST_NOTIFICATIONS`. There is
  no `INTERNET` permission. The manifest also queries launcher activities so the
  app can resolve labels and icons for apps represented in usage data.
- Runtime code: usage events are read through Android's `UsageStatsManager`,
  aggregated, and stored in a local Room database. Goals, exclusions, and
  preferences are also stored locally.
- Release dependencies: AndroidX user interface, lifecycle, Room, WorkManager,
  DataStore, and Kotlin serialization libraries. The resolved release runtime
  tree has no advertising, analytics, crash-reporting, authentication, social,
  or remote-storage Software Development Kit (SDK).
- Android backup is enabled. Backup rules include the Room database and DataStore
  preferences and exclude WorkManager's internal database. Android may copy this
  data to the device's configured backup account or directly to a new device.
- Export is user-directed. Android's document picker chooses the destination;
  Phone Sense writes JavaScript Object Notation (JSON) or comma-separated values
  (CSV) data to the returned document location. Phone Sense does not upload it.
- There is no Phone Sense account, sign-in, purchase, advertising, or server.
  Notification permission is optional and has a **Not now** path.

Repository evidence: `app/build.gradle.kts`, `gradle/libs.versions.toml`,
`app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/backup_rules.xml`,
`app/src/main/res/xml/data_extraction_rules.xml`, `ExportFlow.kt`,
`UsageEventsSource.kt`, and `PRIVACY.md`.

## Proposed answers supported by the app

### Privacy policy

**Proposed Uniform Resource Locator (URL):**
`https://github.com/mattmccormick/phone-sense/blob/main/PRIVACY.md`

The same URL is linked from the in-app About screen. The policy names Phone
Sense and the developer, provides a contact, and covers usage access, on-device
storage, Android backup and transfer, export, retention, and deletion.

Before submission:

- [ ] Open the URL while signed out and confirm it is public, readable, and not
  geofenced. Google requires an active, public, non-editable web page, not a
  Portable Document Format (PDF) file.
- [ ] Confirm the developer name in the store listing matches the policy's
  developer information. If Play rejects the GitHub page, publish the same policy
  as static Hypertext Markup Language (HTML) and update both Play Console and
  the in-app link.

### Ads

**Proposed answer: No, the app does not contain ads.**

There is no ad SDK or ad presentation code. The app does not contain banner,
interstitial, native, or house ads. Recheck the final dependency tree before
each release because Play's declaration includes ads delivered by third-party
SDKs.

### App access and reviewer setup

**Proposed answer: All functionality is available without special access.**

No login, membership, location restriction, or test account is required. If the
console offers a reviewer-instructions field, use:

> Phone Sense has no account or login. On first launch, tap **Allow usage
> access**. Android opens **Settings > Special app access > Usage access**;
> enable Phone Sense, then return to the app. This lets Phone Sense read which
> apps were in the foreground and for how long so it can calculate screen-time
> totals and budgets. It does not read app content. On the notification screen,
> either enable notifications or tap **Not now**; notifications are optional.
> Complete the week-start and notification-time screen to reach the home screen.

`PACKAGE_USAGE_STATS` is granted through Android Settings rather than a normal
runtime permission dialog. Android documents `ACTION_USAGE_ACCESS_SETTINGS` as
the settings screen that controls access to usage information. If the review
device has no useful history, the reviewer can still inspect onboarding,
settings, manual entries, goals, and import/export; charts based on device usage
will populate only when usage history exists.

### Data safety

**Proposed top-level answers for the current release:**

| Console question | Proposed answer | Basis |
| --- | --- | --- |
| Does the app collect any required user data types? | No | Play defines collection as transmitting data off the device. Phone Sense and its release libraries have no network permission or transmission code. |
| Does the app share any required user data types? | No | There is no developer or third-party transfer. User-directed export goes straight to the document provider and is governed by the selected provider. |
| Is all user data encrypted in transit? | Not applicable | The proposed declaration has no collected or shared data in transit. Answer the console's generated question only if it appears. |
| Can users request deletion? | Use the console's no-collection path | On-device data can be deleted by clearing app storage or uninstalling. There is no account or developer-held server copy to delete. |

Why local usage data is not listed as collected: the app accesses app activity
(package identity and foreground duration), screen-time totals, goals, rules,
and settings, but processes and stores them on the device. Google's definition
does not treat on-device processing alone as collection.

Two transfers still need an explicit final review:

- Android backup and device transfer: Android, not Phone Sense, performs these
  under the device owner's backup settings and provider terms. The app opts its
  database and preferences into that platform facility. This behavior is fully
  disclosed in the privacy policy. Confirm the final Play form does not give
  platform backup a new, specific disclosure treatment before selecting **No**.
- User-directed export: Google says a direct upload to a user's own external
  drive or cloud account need not be declared as collection when the app itself
  does not collect or access the uploaded copy. Phone Sense only writes to the
  document location the user selects. A later automatic upload, share flow,
  analytics library, network permission, or developer-controlled destination
  would require reassessment.

Before submission:

- [ ] Generate the final signed release bundle and inspect its merged manifest;
  confirm it still lacks `INTERNET` and unexpected permissions.
- [ ] Resolve `releaseRuntimeClasspath` again and audit any changed direct or
  transitive dependency for data transmission.
- [ ] Walk every Data safety data type and purpose in the live form. Do not infer
  that local access must be declared as off-device collection.
- [ ] Confirm Data safety answers remain consistent with `PRIVACY.md`.

### Content rating

**Evidence-backed draft:** identify this as an application, not a game. The
current app contains no violence, sexual content or nudity, profanity, drugs,
gambling, horror, user-generated content, chat, public content sharing,
location sharing, purchases, or ads. It is a local screen-time budgeting tool.

Answer the live International Age Rating Coalition questionnaire from its exact
wording. The rating authorities calculate the result; do not choose or promise
a rating in advance. Revisit these answers if store text, links, community
features, purchases, or advertising change.

### Usage access and notifications

Usage access is core functionality: it supplies the app identities and durations
needed for automatic screen-time totals and budgets. The first screen explains
what is read, that processing happens on-device, and that Android backup may
copy app data when device backup is enabled. A button opens Android's usage
access settings. The app checks access again when the user returns.

Notifications are optional. On Android 13 and later the app requests
`POST_NOTIFICATIONS` only after the user selects **Turn on notifications**. The
user can choose **Not now** and continue. Notifications cover daily remaining
budget, a weekly summary and recommended goal, a missing-goal prompt, and an
alert when usage access is needed.

No broad Play permissions declaration is proposed solely for these two manifest
permissions. Play generates a Permissions Declaration Form when an uploaded
artifact contains a permission subject to that process; inspect and answer any
form the console actually generates rather than assuming none will appear.

## Owner decisions and console-only answers

The repository cannot decide these. The owner must review and select them:

- [ ] Intended audience age groups. Selecting children invokes the Families
  policy; suitability for children is not the same as intentionally targeting
  children.
- [ ] Countries and regions for distribution.
- [ ] Free or paid availability and any later monetization plan.
- [ ] App category and store contact details.
- [ ] Signing ownership, upload-key custody, and Play App Signing enrollment.
- [ ] Every legal certification, policy attestation, and declaration of authority
  displayed by the console.
- [ ] Whether the account or app must complete a testing or production-access
  process, based on what Play Console currently shows.

The owner reports that **Google Android identity verification is complete**.
Record that only as user-reported status. It does not establish that this app is
registered for Android developer verification, that Play Console setup is
complete, that production access has been granted, or that any applicable test
obligation has been satisfied.

## Official guidance checked

- [Data safety form](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Prepare an app for review: privacy policy, ads, and app access](https://support.google.com/googleplay/android-developer/answer/9859455)
- [Target audience and content](https://support.google.com/googleplay/android-developer/answer/9867159)
- [Content rating requirements](https://support.google.com/googleplay/android-developer/answer/9859655)
- [Permissions declarations](https://support.google.com/googleplay/android-developer/answer/9214102)
- [Google Play user-data policy](https://support.google.com/googleplay/android-developer/answer/18258653)
- [Android usage-access settings reference](https://developer.android.com/reference/android/provider/Settings#ACTION_USAGE_ACCESS_SETTINGS)
- [Target API level requirement](https://developer.android.com/google/play/requirements/target-sdk)
