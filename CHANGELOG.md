<!--
    When adding new changelog entries, use [Issue #0] to link to issues and
    [PR #0 @user] to link to pull requests. Then run:

        ./gradlew changelogUpdateLinks

    to update the actual links at the bottom of the file.
-->

### Unreleased

* Fix removal of restrictions for `READ_CALL_LOG` permission on Android 14 QPR2 ([PR #528 @chenxiaolong])
  * This only affects fresh installs of BCR. Android normally blocks users from granting this permission, even from Android's Settings app. The removal of this restriction now works on Android 14 QPR2 and newer.
* Update Turkish translations ([PR #521 @symbuzzer])

### Version 1.62

* Automatically detect sample rates supported by the output format's encoder ([Issue #507], [PR #508 @chenxiaolong])
  * Fixes recording failures on Redmi devices due to the OEM removing support for all non-48kHz sample rates from Android's standard Opus encoder.
* Fix crash when Android returns a null SubscriptionInfo ([Issue #513], [PR #516 @chenxiaolong])
  * This can happen for third party applications, like SIP clients, that integrate with Android's telephony framework.
* Update Turkish translations ([PR #517 @symbuzzer])
* Disable recording telecom-integrated applications by default ([Issue #513], [PR #518 @chenxiaolong])
  * This includes apps, like SIP clients, where recording is unlikely to work properly on any device.
* Discard recordings that contain complete silence ([Issue #513], [PR #519 @chenxiaolong])
* Fix typo in Italian translations ([PR #522 @nicorac])
* Update Traditional Chinese (zh-TW) translations ([PR #523 @anenasa])

### Version 1.61

* Update Turkish translations ([PR #503 @symbuzzer])
* Fix crash if the call log reports an empty phone number for private calls ([Issue #455], [PR #505 @chenxiaolong])
* Always save log file when an error occurs ([PR #506 @chenxiaolong])
  * It is no longer necessary to manually enable debug mode unless you also want to save the logs for successful recordings.

### Version 1.60

* Make the sample rate option format-specific instead of global ([PR #496 @chenxiaolong])
  * The default sample rate is now also 16 kHz for every format that supports it
* Add support for AMR-WB and AMR-NB ([Issue #264], [PR #497 @chenxiaolong])
  * The patents have now expired in North America and Europe.
  * Note that this is a lossy recording even though the raw phone call audio signal is AMR. The modem always decodes the raw AMR audio to PCM. When AMR output in BCR is selected, the PCM audio is reencoded back to AMR.

Behind the scenes changes:

* Fix missing Gradle verification checksums when building from source on non-Linux OSs ([Issue #491], [PR #493 @chenxiaolong])
* Update dependencies ([PR #498 @chenxiaolong])

### Version 1.59

* Fix removal of Android's call log permission restriction for fresh installs in Android 14 ([PR #481 @chenxiaolong])

### Version 1.58

* Update Polish translations ([PR #476 @phyrz91])
* Add Italian translations ([PR #480 @DHD2280])

### Version 1.57

* Update Chinese translations ([PR #469 @Pr0pHesyer])
* Update Portuguese (Portugal) translations ([PR #474 @MicroDJS])

### Version 1.56

* Update Slovak translations ([PR #456 @pvagner])
* Update all dependencies ([PR #457 @PatrykMis])
* Update Russian translations ([PR #458 @groozchique])
* Add Hindi translations and improve documentation ([PR #459 @y0geshx])
* Update Hebrew translations ([PR #460 @htht2001])

### Version 1.55

* Update Russian translations ([PR #450 @rze0])
* Update French translations ([PR #453 @NSO73])
* Enable memory tagging extensions for ARMv9 devices ([PR #454 @chenxiaolong])

### Version 1.54

* Add support for call redirection apps that use regular phone calls behind the scenes, like Google Voice ([Issue #433], [PR #444 @chenxiaolong])
  * Please see [the documentation for this feature](./README.md#call-redirection) because there are many limitations.

### Version 1.53

It's Android 14 release day! All previous versions of BCR should work on Android 14. This release is just the first one that targets API 34.

Changes:

* Make auto-record rules UI a bit more intuitive ([Issue #429], [PR #430 @chenxiaolong])
* Update Turkish translations ([PR #434 @symbuzzer])
* Update dependencies and target API 34 ([PR #401 @chenxiaolong])

### Version 1.52

* Instead of graying out `Disable battery optimizations`, take the user to Android's Settings ([Issue #422], [PR #424 @chenxiaolong])
  * Android does not provide a way for apps to directly re-enable battery optimizations.

### Version 1.51

* Update Russian translations ([PR #419 @rze0])
* Add support for direct install from recovery on devices that use A/B and/or dynamic partitions ([Issue #338], [PR #420 @chenxiaolong])

### Version 1.50

* Update Turkish translations ([PR #400 @symbuzzer])
* Work around gradle dependency verification blocking sources and javadocs from loading in Android Studio ([PR #402 @chenxiaolong])
* Update Simplified Chinese translations ([PR #404 @Yee2])
* Update Polish translations ([PR #407 @PatrykMis])
* Use standard OK/Cancel text from Android's resources ([PR #408 @PatrykMis])
* Update all dependencies ([PR #414 @PatrykMis])
* Update Russian translations ([PR #418 @rze0])

### Version 1.49

* Update Turkish translations ([PR #385 @symbuzzer])
* Add output format and encoding information to the metadata file ([Issue #380], [PR #387 @chenxiaolong])
* Update dependency verification metadata for building on Windows ([PR #391 @quyenvsp])
* Fix rare crash in quick settings tile caused by an Android bug ([PR #392 @PatrykMis])
* Update all dependencies ([PR #393 @PatrykMis])
* Fix stale notifications not being dismissed when changing the output directory ([PR #395 @chenxiaolong])
* Work around crash when deleting a recording due to an upstream Android 13 bug ([Issue #388], [PR #396 @chenxiaolong])
* Fix possible unexpected behavior in old notifications if BCR is killed and restarted by Android ([PR #397 @chenxiaolong])
* Fix `{direction}` filename template variable never being defined ([Issue #394], [PR #398 @chenxiaolong])
* Add new `{sim_slot:always}` filename template option to get the SIM slot even if there's only one active SIM ([Issue #394], [PR #398 @chenxiaolong])

### Version 1.48

* Update Turkish translations ([PR #374 @symbuzzer])
* Add support for writing a JSON file containing the call metadata ([Issue #135], [Issue #380], [PR #382 @chenxiaolong], [PR #384 @chenxiaolong])
* Long pressing the output directory will now open it in the system file manager ([Issue #135], [PR #383 @chenxiaolong])

Non-user-facing changes:

* Update dependencies ([PR #371 @PatrykMis], [PR #372 @PatrykMis], [PR #375 @PatrykMis])
* Enable checksum verification for dependencies ([PR #373 @chenxiaolong])
* Prepare for API 34 update ([Issue #376], [PR #377 @PatrykMis])
* Migrate dependency declarations to version catalogs ([PR #381 @PatrykMis])

### Version 1.47

* Update Turkish translations ([PR #362 @symbuzzer])
* Fix regression in version 1.46 that reintroduced an issue where M4A/AAC output files sometimes get an incorrect `.mp3` file extension (previously [Issue #292], [Issue #367], [PR #368 @chenxiaolong])
* Update all dependencies ([PR #369 @chenxiaolong])
* Remove migration of the old `Initially paused` option to the auto-record rules ([PR #370 @chenxiaolong])
  * If you used `Initially paused` in the past, upgrade to 1.46 first and then upgrade to this version.

### Version 1.46

* Add support for subdirectories in the filename template (`/` character) ([Issue #357], [PR #361 @chenxiaolong])
  * See [the documentation](./README.md#subdirectories) for details.

### Version 1.45

* Update all dependencies ([PR #354 @PatrykMis])
* Add Ukrainian translations ([Issue #352], [PR #356 @Hutsul-coder])
* Add German translations ([PR #358 @newsn23])
* Remove migration of the old `bcr.properties` config file to the new filename templates ([PR #359 @chenxiaolong])
  * If you used `bcr.properties` in the past, upgrade to 1.44 first and then upgrade to this version.

### Version 1.44

**IMPORTANT:** For folks who upgraded from an old BCR version to 1.42 or 1.43, please double check to make sure the auto-record rules are what you expect.

* Update Polish translations ([PR #349 @phyrz91])
* Fix incorrect/opposite logic in the migration of the old "Initially paused" option ([Issue #350], [PR #351 @chenxiaolong])
  * Users who toggled the "Initially paused" option in the past (even turning on and then back off) had the setting migrated to the opposite auto-record behavior. For example, initially paused disabled was migrated to auto-record disabled (instead of enabled).

### Version 1.43

* Update Russian translations ([PR #333 @bogachenko])
* Add Traditional Chinese translations ([PR #334 @anenasa])
* Update Simplified Chinese translations ([PR #347 @Yee2])

### Version 1.42

* Update all dependencies ([PR #311 @PatrykMis], [PR #315 @PatrykMis], [PR #329 @chenxiaolong])
* Update Russian translations ([PR #319 @bogachenko], [PR #325 @bogachenko])
* Work around crash due to broken Android package manager caching on custom ROMs that set the system time too late in the boot process ([Issue #275], [Issue #303], [Issue #307], [Issue #314], [PR #323 @chenxiaolong])
* Show a separate notification for each call when using call waiting ([PR #324 @chenxiaolong])
  * Pausing/resuming can now be done per call and the notification will correctly show that the background call is hold
* Add support for auto-record rules ([Issue #93], [Issue #320], [PR #327 @chenxiaolong])
  * This replaces the old "Initially paused" setting, which will automatically be migrated to the new rules system.
* Update Turkish translations ([PR #330 @symbuzzer])

Non-user-facing changes:

* Reorganize source files ([PR #328 @chenxiaolong])
* Add CI workflow for pull requests ([PR #331 @chenxiaolong])

### Version 1.41

* Remove workaround for overlayfs ([PR #306 @chenxiaolong])
  * MIUI users must update to Magisk v26.0 or newer now that the issue is properly fixed there
* Work around `READ_CALL_LOG` being a hard restricted permission in Android 10+ ([Issue #304], [PR #308 @chenxiaolong], [PR #309 @chenxiaolong])
  * For Magisk installs, this is done automatically. For direct installs (from recovery), see the updated documentation.
* Minor formatting fixes in translation files ([PR #310 @chenxiaolong])

### Version 1.40

* Update all dependencies ([PR #289 @PatrykMis], [PR #294 @PatrykMis])
* Improve conference call handling ([PR #285 @chenxiaolong])
  * Recording a conference call will no longer incorrectly produce extra files for each participant in the call.
  * When using call waiting, the recording is paused for the inactive call so that it doesn't capture the audio for the wrong call.
  * BCR will manually look up the contact name due to an AOSP bug where the call's contact name field is sometimes null for conference calls. As a result of this change, adding the contact name to the output filename is now supported in Android <11.
* Update Turkish translations ([PR #286 @symbuzzer])
* Fix M4A/AAC output files sometimes getting an incorrect `.mp3` file extension due to an Android bug ([Issue #292], [PR #293 @chenxiaolong])
* Introduce new output filename templating engine ([Issue #288], [PR #296 @chenxiaolong], [PR #300 @chenxiaolong])
  * See [the documentation](./README.md#filename-template) for details.
  * This completely replaces the old hidden `bcr.properties` templates. For folks who previously used the old templates, the configuration will be automatically migrated to the new template system.
* Add support for querying the name from the call log ([Issue #291], [PR #298 @chenxiaolong])
  * This requires the optional `READ_CALL_LOGS` permission to be granted.
* Add support for formatting the phone number as digits only or with the country-specific style ([Issue #290], [PR #299 @chenxiaolong])
* Replace slider UI components with chips and the ability to set custom values ([Issue #295], [PR #301 @chenxiaolong])
* Remove outline "Reset to defaults" button to make it less prominent ([PR #302 @chenxiaolong])

Non-user-facing changes:

* Remove use of deprecated method in gradle build script ([Issue #294], [PR #297 @chenxiaolong])

### Version 1.39

* Update Russian translations ([PR #277 @bogachenko])
* Add Simplified Chinese translations ([PR #283 @Yee2])

### Version 1.38

* Update all dependencies ([PR #266 @PatrykMis], [PR #272 @PatrykMis])
* Mark quick settings tile as toggleable for accessibility ([PR #270 @PatrykMis])
* Add support for Android 13's per-app language preferences ([PR #271 @PatrykMis])
* Fix crash when changing the output directory if the previous output directory was associated with a cloud provider app that is no longer installed ([PR #273 @chenxiaolong])
* Show friendly path name instead of `content://` when the output directory points to a cloud provider app ([PR #274 @chenxiaolong])

### Version 1.37

* Fix custom filename templates breaking after version 1.35 in release builds ([Issue #260], [PR #261 @chenxiaolong])

### Version 1.36

* Fix loss of file extension when the output file needs to be renamed ([PR #259 @chenxiaolong])

### Version 1.35

* Fix missing BCR app when doing a direct (non-Magisk module) installation ([Issue #253], [PR #254 @chenxiaolong])
  * This bug was introduced in version 1.34 and was caused by an oversight when adding the workaround for overlayfs.
* Work around absurdly slow SAF (Android Storage Access Framework) on some devices ([Issue #252], [PR #257 @chenxiaolong])
  * This fixes audio being chopped off the beginning of the call recording. Some devices' SAF implementations are slow to the point where checking the existence of a file may take upwards of 8 seconds (vs. 2ms with native file access).
  * This only affected users who picked a custom output directory. The default output directory uses native file access instead of SAF.
* Fix caller ID and contact name potentially ending up in the log file if they change during the middle of a call ([PR #258 @chenxiaolong])

### Version 1.34

* Write `crash.log` to output directory if BCR crashes outside of the scope of a phone call ([Issue #243], [PR #245 @chenxiaolong])
* Set default notification importance to high for the persistent notification during a all ([Issue #248], [PR #249 @chenxiaolong])
  * This makes it easier to access the pause/resume button in the notification.
  * This change only affects new installs and the user's notification preferences in Android's settings will always take precedence.
* Work around broken NFC and possible bootloops on MIUI when `/system` contains overlayfs mount points ([Issue #242], [Issue #246], [PR #250 @chenxiaolong])
  * This is only a workaround for a bug in Magisk's mount logic. The actual Magisk bug will be fixed by: https://github.com/topjohnwu/Magisk/pull/6588.

### Version 1.33

* Fix crash caused by a workaround for a old material3 library bug that has since been fixed ([Issue #240], [PR #241 @chenxiaolong])

### Version 1.32

* Add Hebrew translations ([PR #232 @Mosheh65])

### Version 1.31

* For the OGG/Opus bitrate option, reduce the number of steps between 6 kbps and 510 kbps from 253 to 25 ([Issue #237], [PR #239 @chenxiaolong])

Non-user-facing changes:

* Updated all dependencies ([PR #238 @chenxiaolong])

Signing changes:

* Switch from GPG signing to SSH signing for new release zips, git commits, and git tags ([PR #229 @chenxiaolong])
  * The goal is to switch to stronger cryptography and rely on a simpler tool that everybody already has installed (including on Windows). For folks who were previously verifying signatures using GPG, please see the updated documentation for how to verify signatures with SSH.
  * For folks who want to verify that this change is legitimate, see commit 0bc3935fe2a1b6e3d56049503db521877501edc1. That commit, which introduced this change, was signed using the original GPG key.
  * The APK signing key remains unchanged.

### Version 1.30

* Update Slovak translations ([PR #217 @pvagner])
* Update Polish translations ([PR #219 @Twoomatch])
* Improve English description of `initially paused` preference ([PR #220 @Twoomatch])
* Update Spanish translations ([PR #222 @nmayorga092])

Magisk module updater changes:

* Show changelog for the correct version, excluding unreleased changes ([PR #223 @chenxiaolong])

Documentation changes:

* README.md: Fix typos ([PR #221 @Twoomatch])

### Version 1.29

* Add a new option for starting the recording in the paused state ([Issue #198], [PR #211 @chenxiaolong])
  * If enabled, this allows the user to choose whether a call is recorded. If a recording starts in the paused state and is never resumed, then the empty output file is not saved.

Documentation changes:

* README.md: Document hidden/advanced features ([Issue #212], [PR #213 @chenxiaolong])

### Version 1.28

* Inform the user that the device/firmware might not support call recording if an error origates from Android's internal components ([PR #206 @chenxiaolong])
* Fix crash if any filename template value (eg. caller/contact name) contains \ or $ ([Issue #207], [PR #209 @chenxiaolong])
* Add support for pausing/resuming the recording ([Issue #198], [PR #210 @chenxiaolong])
  * The button is in the `Call recording in progress` notification

### Version 1.27

* Update Polish translations ([PR #192 @Twoomatch])
* Update Spanish translations ([PR #201 @nmayorga092])
* Fix silent crash causing recording to not happen when debug mode is enabled ([Issue #195], [PR #203 @chenxiaolong])
  * The bug was introduced in version 1.26
* Add support for changing the filename timestamp format ([Issue #204], [PR #205 @chenxiaolong])
  * Similar to the existing output filename options, this can only be done via the `bcr.properties` config file

Non-user-facing changes:

* Update Kotlin and AndroidX ([PR #196 @PatrykMis])
* Update Build Tools ([PR #199 @PatrykMis])

### Version 1.26

* Update Turkish and Russian translations ([PR #188 @EleoXDA])
* Add hidden feature to customize the output filename ([PR #189 @chenxiaolong])
  * To avoid complicating BCR's code, there is no UI option for this
  * To customize the output filenames, copy [the default template](./app/src/main/res/raw/filename_template.properties) to `bcr.properties` in the output directory and edit the file

### Version 1.25

* Add SIM slot ID to the filename if there are multiple active SIMs ([Issue #177], [PR #178 @chenxiaolong])
* Fix share action in the recording complete notification always referencing an old recording ([PR #181 @chenxiaolong])
* Add a new Delete action alongside Open and Share in the notifications ([Issue #179], [PR #182 @chenxiaolong])
* Allow changing output settings when call recording is disabled ([PR #183 @chenxiaolong])

Documentation changes:

* README.md: Explain what every permission is used for ([PR #180 @chenxiaolong])

Non-user-facing changes:

* Update gradle wrapper to 7.6.0 ([PR #174 @PatrykMis])

### Version 1.24

* Notification improvements ([PR #169 @chenxiaolong])
  * A notification is now shown when a recording completes, with options for opening or sharing the recording in a 3rd party app.
    * **NOTE: Manual action required.** For opening/sharing recordings to work, reset the output directory to the default and then select the output directory again. This is required because BCR previously only requested write access to the output directory, but not read access.
    * These new notifications can be disabled in Android's settings by turning off the `Success alerts` notification channel.
  * The file path in error notifications is now human readable instead of a URL-encoded `content://...`.
* BCR will explicitly vibrate if vibration is enabled for its notification channels ([PR #167 @quyenvsp], [PR #171 @chenxiaolong])
  * This is needed because Android will not respect the notification vibration option during a phone call.

Non-user-facing changes:

* Updated all dependencies ([PR #160 @PatrykMis])
* Fixed Gradle non-laziness, causing the execution of specific tasks to be slower ([PR #168 @chenxiaolong])

### Version 1.23

* Update all dependencies and fix build system lint issues ([PR #155 @PatrykMis])
* Add Slovak translation ([PR #161 @pvagner])

### Version 1.22

* (Direct installs only) Add `/system/addon.d/` script to persist installation across OS updates ([Issue #142], [PR #144 @chenxiaolong])
  * Only applies to LineageOS-based firmware
* Improve logging in debug mode ([Issue #143], [PR #145 @chenxiaolong], [PR #147 @chenxiaolong], [PR #148 @chenxiaolong])
  * Run logcat interactively for the duration of the call to ensure no lost log messages due to logcat overflow
  * Include BCR version number in the logs
* Improve output file writing reliability ([Issue #143], [PR #146 @chenxiaolong], [PR #149 @chenxiaolong], [PR #150 @chenxiaolong])
* Improve call disconnection detection on buggy firmware ([Issue #143], [PR #151 @chenxiaolong])
  * Works around Samsung OneUI's telephony framework bug where Android does not notify apps (including their own) when a call disconnects
* Use non-blocking reads from call audio stream ([Issue #143], [PR #152 @chenxiaolong])
  * Fixes recordings not stopping until another call becomes active on Samsung OneUI because `AudioRecord.read()` blocks forever as soon as a call disconnects

### Version 1.21

* (Direct installs only) Explicitly remount system as writable and ignore `ENOENT` errors during cleanup ([Issue #108], [Issue #138], [PR #139 @chenxiaolong])
* Updated all dependencies ([PR #140 @chenxiaolong])

### Version 1.20

* Update dependencies ([PR #121 @PatrykMis], [PR #132 @PatrykMis])
* Perform a direct install if `/system/bin/recovery` exists in the environment ([Issue #131], [PR #133 @chenxiaolong])
  * Previously, only `/sbin/recovery` was used to detect if booted into recovery

### Version 1.19

* Add support for flashing via recovery ([Issue #128], [PR #130 @chenxiaolong])
  * This is for unrooted (non-Magisk) installs only. BCR will be installed to the system partition directly when flashed via recovery.

### Version 1.18

* Update gradle wrapper to 7.5.1 ([PR #116 @PatrykMis])
* Fix plurals in Russian translations ([PR #117 @EleoXDA])
* Add French translation ([PR #120 @NSO73])

### Version 1.17

* Update dependencies and gradle wrapper ([PR #112 @PatrykMis])
* Update Polish translations ([PR #112 @PatrykMis])
* Fix silent crash when receiving a call from a private number ([Issue #111], [PR #114 @chenxiaolong])

### Version 1.16

* Update Turkish translations ([PR #106 @EleoXDA])
* Update Russian translations ([PR #107 @EleoXDA])

### Version 1.15

* Update Spanish translations ([PR #92 @nmayorga092])
* Update Turkish translations ([PR #97 @EleoXDA])

### Version 1.14

* Add support for a basic file retention policy ([Issue #25], [Issue #81], [Issue #88], [PR #90 @chenxiaolong])

Non-user-facing changes:

* Improve type-safety when loading and saving preferences ([PR #91 @chenxiaolong])

### Version 1.13

* Add Polish translations ([PR #76 @uvzen])
* Target stable API 33 (Tiramisu) ([PR #82 @chenxiaolong])
* Add optional contacts permission to initial permissions prompt ([Issue #78], [Issue #80], [PR #84 @chenxiaolong])
  * If allowed, contact names are added to the output filenames.
  * This feature was implemented in version 1.5, but required the user to manually enable from the system settings.

Non-user-facing changes:

* Update Android gradle plugin to 7.2.1 and Kotlin to 1.7.0 ([PR #83 @chenxiaolong])

### Version 1.12

* Fix potential crash when showing user-friendly output directory path ([PR #74 @chenxiaolong])
  * Fixes regression in version 1.11

### Version 1.11

* Fix persistent notification icon being too small ([PR #67 @chenxiaolong])
* Increase internal buffer sizes to reduce chance of encoding slowdowns causing audible artifacts ([Issue #39], [Issue #54], [PR #69 @chenxiaolong], [PR #73 @chenxiaolong])
  * The native sample rate option had to be removed for this change
* Show user-friendly path instead of a raw URI for the output directory ([PR #71 @chenxiaolong])
* Work around SAF slowness by recording to the default directory and then moving to the user directory after recording is completed ([Issue #39], [Issue #54], [PR #72 @chenxiaolong])

Non-user-facing changes:

* Change Container abstract class to an interface ([PR #68 @chenxiaolong])
* Update all gradle dependencies ([PR #70 @chenxiaolong])

### Version 1.10

* Update Spanish translations ([PR #64 @nmayorga092])
* Add hidden debug mode, which saves logs for each recording (long press version number to enable) ([PR #65 @chenxiaolong])
* Set recording thread priority to THREAD_PRIORITY_URGENT_AUDIO ([Issue #39], [Issue #54], [PR #66 @chenxiaolong])

### Version 1.9

* Improve buffering to reduce chance of audio drops ([Issue #39], [Issue #54], [PR #61 @chenxiaolong])

### Version 1.8

* Update Turkish translations ([PR #58 @fnldstntn])
* Fix overlapping audio and other audible artifacts when using an encoded format (OPUS, AAC, or FLAC) ([Issue #39], [Issue #54], [PR #59 @chenxiaolong])

### Version 1.7

* Change output format button group to material chips to prevent text from being cut off with narrower screen widths ([Issue #52], [PR #55 @chenxiaolong])
* Add support for configuring the capture sample rate ([PR #56 @chenxiaolong])
* Send notification if an error occurs during call recording ([PR #57 @chenxiaolong])

### Version 1.6

* Enable minification (without obfuscation) to shrink the download size by ~64% ([PR #45 @chenxiaolong])
* Update Turkish translations ([PR #46 @fnldstntn])
* Add support for WAV/PCM output for troubleshooting (bypasses encoding/compression pipeline) ([PR #48 @chenxiaolong])

Non-user-facing changes:

* Improve output format parameter abstraction ([PR #49 @chenxiaolong])
* Use view binding instead of findViewById where possible ([PR #50 @chenxiaolong])

### Version 1.5

* Optionally add contact name to output filename if Contacts permission is granted (Android 11+) ([Issue #28], [PR #42 @chenxiaolong])
* Add Spanish translation ([PR #41 @nmayorga092])
* Redact sensitive information from logcat logs ([PR #43 @chenxiaolong])

### Version 1.4

* Add support for configurable output formats: OGG/Opus (Android 10+), M4A/AAC, FLAC ([Issue #21], [PR #29 @chenxiaolong], [PR #32 @chenxiaolong], [PR #34 @chenxiaolong], [PR #35 @chenxiaolong], [PR #38 @chenxiaolong])
* README.md: Remove mention of cloud storage. Android's Storage Access Framework does not support cloud storage when opening folders ([Issue #30], [PR #31 @chenxiaolong])
* Add full changelog text for updates from Magisk Manager ([PR #36 @chenxiaolong])

Non-user-facing changes:

* Fix minor compiler warnings ([PR #37 @chenxiaolong])

### Version 1.3

* Write audio duration to FLAC metadata after recording is complete ([Issue #19], [PR #20 @chenxiaolong])
* Add Turkish translations ([Issue #18], [PR #22 @fnldstntn])

Non-user-facing changes:

* Don't add irrelevant update metadata to release zips ([PR #23 @chenxiaolong])
* Fix serialization exception when running the `updateJson` gradle tasks ([PR #24 @chenxiaolong])

### Version 1.2

* Fix typo and improve wording of battery optimization preference ([PR #4 @EleoXDA])
* Add Russian translations ([PR #7 @marat2509])
* Add support for API 28 (Android 9) ([Issue #6], [PR #10 @chenxiaolong])
* Add incoming/outgoing tag to filenames ([Issue #3], [PR #11 @chenxiaolong])
* Add caller ID to filenames for incoming calls (Android 10+ only) ([Issue #3], [PR #13 @chenxiaolong])
* Fix filename timestamps to match the call log exactly ([Issue #3], [PR #12 @chenxiaolong])
* The about link in the app now links to the exact commit the version was built from ([PR #15 @chenxiaolong])
* Add support for Magisk's built-in module updater ([PR #16 @chenxiaolong])

Non-user-facing changes:

* Update gradle and Android gradle plugin dependencies ([PR #9 @chenxiaolong])
* Add git commit to version number for debug builds ([PR #14 @chenxiaolong])
* Ensure custom gradle tasks (`moduleProp`, `permissionsXml`, `zip`, and `updateJson`) rebuild when input variables (eg. git commit) change ([PR #17 @chenxiaolong])

### Version 1.1

* Target Android SDK 32. BCR was previously targeting the Tiramisu (33) preview SDK, which made it not installable on stable Android versions. ([Issue #1], [PR #2 @chenxiaolong])

### Version 1.0

* Initial release

<!-- Do not manually edit the lines below. Use `./gradlew changelogUpdateLinks` to regenerate. -->
[Issue #1]: https://github.com/chenxiaolong/BCR/issues/1
[Issue #3]: https://github.com/chenxiaolong/BCR/issues/3
[Issue #6]: https://github.com/chenxiaolong/BCR/issues/6
[Issue #18]: https://github.com/chenxiaolong/BCR/issues/18
[Issue #19]: https://github.com/chenxiaolong/BCR/issues/19
[Issue #21]: https://github.com/chenxiaolong/BCR/issues/21
[Issue #25]: https://github.com/chenxiaolong/BCR/issues/25
[Issue #28]: https://github.com/chenxiaolong/BCR/issues/28
[Issue #30]: https://github.com/chenxiaolong/BCR/issues/30
[Issue #39]: https://github.com/chenxiaolong/BCR/issues/39
[Issue #52]: https://github.com/chenxiaolong/BCR/issues/52
[Issue #54]: https://github.com/chenxiaolong/BCR/issues/54
[Issue #78]: https://github.com/chenxiaolong/BCR/issues/78
[Issue #80]: https://github.com/chenxiaolong/BCR/issues/80
[Issue #81]: https://github.com/chenxiaolong/BCR/issues/81
[Issue #88]: https://github.com/chenxiaolong/BCR/issues/88
[Issue #93]: https://github.com/chenxiaolong/BCR/issues/93
[Issue #108]: https://github.com/chenxiaolong/BCR/issues/108
[Issue #111]: https://github.com/chenxiaolong/BCR/issues/111
[Issue #128]: https://github.com/chenxiaolong/BCR/issues/128
[Issue #131]: https://github.com/chenxiaolong/BCR/issues/131
[Issue #135]: https://github.com/chenxiaolong/BCR/issues/135
[Issue #138]: https://github.com/chenxiaolong/BCR/issues/138
[Issue #142]: https://github.com/chenxiaolong/BCR/issues/142
[Issue #143]: https://github.com/chenxiaolong/BCR/issues/143
[Issue #177]: https://github.com/chenxiaolong/BCR/issues/177
[Issue #179]: https://github.com/chenxiaolong/BCR/issues/179
[Issue #195]: https://github.com/chenxiaolong/BCR/issues/195
[Issue #198]: https://github.com/chenxiaolong/BCR/issues/198
[Issue #204]: https://github.com/chenxiaolong/BCR/issues/204
[Issue #207]: https://github.com/chenxiaolong/BCR/issues/207
[Issue #212]: https://github.com/chenxiaolong/BCR/issues/212
[Issue #237]: https://github.com/chenxiaolong/BCR/issues/237
[Issue #240]: https://github.com/chenxiaolong/BCR/issues/240
[Issue #242]: https://github.com/chenxiaolong/BCR/issues/242
[Issue #243]: https://github.com/chenxiaolong/BCR/issues/243
[Issue #246]: https://github.com/chenxiaolong/BCR/issues/246
[Issue #248]: https://github.com/chenxiaolong/BCR/issues/248
[Issue #252]: https://github.com/chenxiaolong/BCR/issues/252
[Issue #253]: https://github.com/chenxiaolong/BCR/issues/253
[Issue #260]: https://github.com/chenxiaolong/BCR/issues/260
[Issue #264]: https://github.com/chenxiaolong/BCR/issues/264
[Issue #275]: https://github.com/chenxiaolong/BCR/issues/275
[Issue #288]: https://github.com/chenxiaolong/BCR/issues/288
[Issue #290]: https://github.com/chenxiaolong/BCR/issues/290
[Issue #291]: https://github.com/chenxiaolong/BCR/issues/291
[Issue #292]: https://github.com/chenxiaolong/BCR/issues/292
[Issue #294]: https://github.com/chenxiaolong/BCR/issues/294
[Issue #295]: https://github.com/chenxiaolong/BCR/issues/295
[Issue #303]: https://github.com/chenxiaolong/BCR/issues/303
[Issue #304]: https://github.com/chenxiaolong/BCR/issues/304
[Issue #307]: https://github.com/chenxiaolong/BCR/issues/307
[Issue #314]: https://github.com/chenxiaolong/BCR/issues/314
[Issue #320]: https://github.com/chenxiaolong/BCR/issues/320
[Issue #338]: https://github.com/chenxiaolong/BCR/issues/338
[Issue #350]: https://github.com/chenxiaolong/BCR/issues/350
[Issue #352]: https://github.com/chenxiaolong/BCR/issues/352
[Issue #357]: https://github.com/chenxiaolong/BCR/issues/357
[Issue #367]: https://github.com/chenxiaolong/BCR/issues/367
[Issue #376]: https://github.com/chenxiaolong/BCR/issues/376
[Issue #380]: https://github.com/chenxiaolong/BCR/issues/380
[Issue #388]: https://github.com/chenxiaolong/BCR/issues/388
[Issue #394]: https://github.com/chenxiaolong/BCR/issues/394
[Issue #422]: https://github.com/chenxiaolong/BCR/issues/422
[Issue #429]: https://github.com/chenxiaolong/BCR/issues/429
[Issue #433]: https://github.com/chenxiaolong/BCR/issues/433
[Issue #455]: https://github.com/chenxiaolong/BCR/issues/455
[Issue #491]: https://github.com/chenxiaolong/BCR/issues/491
[Issue #507]: https://github.com/chenxiaolong/BCR/issues/507
[Issue #513]: https://github.com/chenxiaolong/BCR/issues/513
[PR #2 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/2
[PR #4 @EleoXDA]: https://github.com/chenxiaolong/BCR/pull/4
[PR #7 @marat2509]: https://github.com/chenxiaolong/BCR/pull/7
[PR #9 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/9
[PR #10 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/10
[PR #11 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/11
[PR #12 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/12
[PR #13 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/13
[PR #14 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/14
[PR #15 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/15
[PR #16 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/16
[PR #17 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/17
[PR #20 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/20
[PR #22 @fnldstntn]: https://github.com/chenxiaolong/BCR/pull/22
[PR #23 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/23
[PR #24 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/24
[PR #29 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/29
[PR #31 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/31
[PR #32 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/32
[PR #34 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/34
[PR #35 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/35
[PR #36 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/36
[PR #37 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/37
[PR #38 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/38
[PR #41 @nmayorga092]: https://github.com/chenxiaolong/BCR/pull/41
[PR #42 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/42
[PR #43 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/43
[PR #45 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/45
[PR #46 @fnldstntn]: https://github.com/chenxiaolong/BCR/pull/46
[PR #48 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/48
[PR #49 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/49
[PR #50 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/50
[PR #55 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/55
[PR #56 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/56
[PR #57 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/57
[PR #58 @fnldstntn]: https://github.com/chenxiaolong/BCR/pull/58
[PR #59 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/59
[PR #61 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/61
[PR #64 @nmayorga092]: https://github.com/chenxiaolong/BCR/pull/64
[PR #65 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/65
[PR #66 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/66
[PR #67 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/67
[PR #68 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/68
[PR #69 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/69
[PR #70 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/70
[PR #71 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/71
[PR #72 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/72
[PR #73 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/73
[PR #74 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/74
[PR #76 @uvzen]: https://github.com/chenxiaolong/BCR/pull/76
[PR #82 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/82
[PR #83 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/83
[PR #84 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/84
[PR #90 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/90
[PR #91 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/91
[PR #92 @nmayorga092]: https://github.com/chenxiaolong/BCR/pull/92
[PR #97 @EleoXDA]: https://github.com/chenxiaolong/BCR/pull/97
[PR #106 @EleoXDA]: https://github.com/chenxiaolong/BCR/pull/106
[PR #107 @EleoXDA]: https://github.com/chenxiaolong/BCR/pull/107
[PR #112 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/112
[PR #114 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/114
[PR #116 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/116
[PR #117 @EleoXDA]: https://github.com/chenxiaolong/BCR/pull/117
[PR #120 @NSO73]: https://github.com/chenxiaolong/BCR/pull/120
[PR #121 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/121
[PR #130 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/130
[PR #132 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/132
[PR #133 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/133
[PR #139 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/139
[PR #140 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/140
[PR #144 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/144
[PR #145 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/145
[PR #146 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/146
[PR #147 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/147
[PR #148 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/148
[PR #149 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/149
[PR #150 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/150
[PR #151 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/151
[PR #152 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/152
[PR #155 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/155
[PR #160 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/160
[PR #161 @pvagner]: https://github.com/chenxiaolong/BCR/pull/161
[PR #167 @quyenvsp]: https://github.com/chenxiaolong/BCR/pull/167
[PR #168 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/168
[PR #169 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/169
[PR #171 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/171
[PR #174 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/174
[PR #178 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/178
[PR #180 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/180
[PR #181 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/181
[PR #182 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/182
[PR #183 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/183
[PR #188 @EleoXDA]: https://github.com/chenxiaolong/BCR/pull/188
[PR #189 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/189
[PR #192 @Twoomatch]: https://github.com/chenxiaolong/BCR/pull/192
[PR #196 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/196
[PR #199 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/199
[PR #201 @nmayorga092]: https://github.com/chenxiaolong/BCR/pull/201
[PR #203 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/203
[PR #205 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/205
[PR #206 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/206
[PR #209 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/209
[PR #210 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/210
[PR #211 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/211
[PR #213 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/213
[PR #217 @pvagner]: https://github.com/chenxiaolong/BCR/pull/217
[PR #219 @Twoomatch]: https://github.com/chenxiaolong/BCR/pull/219
[PR #220 @Twoomatch]: https://github.com/chenxiaolong/BCR/pull/220
[PR #221 @Twoomatch]: https://github.com/chenxiaolong/BCR/pull/221
[PR #222 @nmayorga092]: https://github.com/chenxiaolong/BCR/pull/222
[PR #223 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/223
[PR #229 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/229
[PR #232 @Mosheh65]: https://github.com/chenxiaolong/BCR/pull/232
[PR #238 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/238
[PR #239 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/239
[PR #241 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/241
[PR #245 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/245
[PR #249 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/249
[PR #250 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/250
[PR #254 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/254
[PR #257 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/257
[PR #258 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/258
[PR #259 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/259
[PR #261 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/261
[PR #266 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/266
[PR #270 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/270
[PR #271 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/271
[PR #272 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/272
[PR #273 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/273
[PR #274 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/274
[PR #277 @bogachenko]: https://github.com/chenxiaolong/BCR/pull/277
[PR #283 @Yee2]: https://github.com/chenxiaolong/BCR/pull/283
[PR #285 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/285
[PR #286 @symbuzzer]: https://github.com/chenxiaolong/BCR/pull/286
[PR #289 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/289
[PR #293 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/293
[PR #294 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/294
[PR #296 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/296
[PR #297 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/297
[PR #298 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/298
[PR #299 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/299
[PR #300 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/300
[PR #301 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/301
[PR #302 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/302
[PR #306 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/306
[PR #308 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/308
[PR #309 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/309
[PR #310 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/310
[PR #311 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/311
[PR #315 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/315
[PR #319 @bogachenko]: https://github.com/chenxiaolong/BCR/pull/319
[PR #323 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/323
[PR #324 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/324
[PR #325 @bogachenko]: https://github.com/chenxiaolong/BCR/pull/325
[PR #327 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/327
[PR #328 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/328
[PR #329 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/329
[PR #330 @symbuzzer]: https://github.com/chenxiaolong/BCR/pull/330
[PR #331 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/331
[PR #333 @bogachenko]: https://github.com/chenxiaolong/BCR/pull/333
[PR #334 @anenasa]: https://github.com/chenxiaolong/BCR/pull/334
[PR #347 @Yee2]: https://github.com/chenxiaolong/BCR/pull/347
[PR #349 @phyrz91]: https://github.com/chenxiaolong/BCR/pull/349
[PR #351 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/351
[PR #354 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/354
[PR #356 @Hutsul-coder]: https://github.com/chenxiaolong/BCR/pull/356
[PR #358 @newsn23]: https://github.com/chenxiaolong/BCR/pull/358
[PR #359 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/359
[PR #361 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/361
[PR #362 @symbuzzer]: https://github.com/chenxiaolong/BCR/pull/362
[PR #368 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/368
[PR #369 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/369
[PR #370 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/370
[PR #371 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/371
[PR #372 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/372
[PR #373 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/373
[PR #374 @symbuzzer]: https://github.com/chenxiaolong/BCR/pull/374
[PR #375 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/375
[PR #377 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/377
[PR #381 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/381
[PR #382 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/382
[PR #383 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/383
[PR #384 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/384
[PR #385 @symbuzzer]: https://github.com/chenxiaolong/BCR/pull/385
[PR #387 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/387
[PR #391 @quyenvsp]: https://github.com/chenxiaolong/BCR/pull/391
[PR #392 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/392
[PR #393 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/393
[PR #395 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/395
[PR #396 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/396
[PR #397 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/397
[PR #398 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/398
[PR #400 @symbuzzer]: https://github.com/chenxiaolong/BCR/pull/400
[PR #401 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/401
[PR #402 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/402
[PR #404 @Yee2]: https://github.com/chenxiaolong/BCR/pull/404
[PR #407 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/407
[PR #408 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/408
[PR #414 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/414
[PR #418 @rze0]: https://github.com/chenxiaolong/BCR/pull/418
[PR #419 @rze0]: https://github.com/chenxiaolong/BCR/pull/419
[PR #420 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/420
[PR #424 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/424
[PR #430 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/430
[PR #434 @symbuzzer]: https://github.com/chenxiaolong/BCR/pull/434
[PR #444 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/444
[PR #450 @rze0]: https://github.com/chenxiaolong/BCR/pull/450
[PR #453 @NSO73]: https://github.com/chenxiaolong/BCR/pull/453
[PR #454 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/454
[PR #456 @pvagner]: https://github.com/chenxiaolong/BCR/pull/456
[PR #457 @PatrykMis]: https://github.com/chenxiaolong/BCR/pull/457
[PR #458 @groozchique]: https://github.com/chenxiaolong/BCR/pull/458
[PR #459 @y0geshx]: https://github.com/chenxiaolong/BCR/pull/459
[PR #460 @htht2001]: https://github.com/chenxiaolong/BCR/pull/460
[PR #469 @Pr0pHesyer]: https://github.com/chenxiaolong/BCR/pull/469
[PR #474 @MicroDJS]: https://github.com/chenxiaolong/BCR/pull/474
[PR #476 @phyrz91]: https://github.com/chenxiaolong/BCR/pull/476
[PR #480 @DHD2280]: https://github.com/chenxiaolong/BCR/pull/480
[PR #481 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/481
[PR #493 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/493
[PR #496 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/496
[PR #497 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/497
[PR #498 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/498
[PR #503 @symbuzzer]: https://github.com/chenxiaolong/BCR/pull/503
[PR #505 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/505
[PR #506 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/506
[PR #508 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/508
[PR #516 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/516
[PR #517 @symbuzzer]: https://github.com/chenxiaolong/BCR/pull/517
[PR #518 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/518
[PR #519 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/519
[PR #521 @symbuzzer]: https://github.com/chenxiaolong/BCR/pull/521
[PR #522 @nicorac]: https://github.com/chenxiaolong/BCR/pull/522
[PR #523 @anenasa]: https://github.com/chenxiaolong/BCR/pull/523
[PR #528 @chenxiaolong]: https://github.com/chenxiaolong/BCR/pull/528
