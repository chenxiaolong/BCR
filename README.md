# Basic Call Recorder

<img src="app/images/icon.svg" alt="app icon" width="72" />

![latest release badge](https://img.shields.io/github/v/release/chenxiaolong/BCR?sort=semver)
![license badge](https://img.shields.io/github/license/chenxiaolong/BCR)

BCR is a simple Android call recording app for rooted devices or devices running custom firmware. Once enabled, it stays out of the way and automatically records incoming and outgoing calls in the background.

<img src="app/images/light.png" alt="light mode screenshot" width="200" /> <img src="app/images/dark.png" alt="dark mode screenshot" width="200" />

### Features

* Supports Android 9 through 13
* Supports output in various formats:
  * OGG/Opus - Lossy, smallest files, default on Android 10+
  * M4A/AAC - Lossy, smaller files, default on Android 9
  * FLAC - Lossless, larger files
  * WAV/PCM - Lossless, largest files, least CPU usage
* Supports Android's Storage Access Framework (can record to SD cards, USB devices, etc.)
* Quick settings toggle
* Material You dynamic theming
* No persistent notification unless a recording is in progress
* No network access permission
* No third party dependencies
* Works with call screening on Pixel devices (records the caller, but not the automated system)

### Non-features

As the name alludes, BCR intends to be a basic as possible. The project will have succeeded at its goal if the only updates it ever needs are for compatibility with new Android versions. Thus, many potentially useful features will never be implemented, such as:

* Support for old Android versions (support is dropped as soon as maintenance becomes cumbersome)
* Workarounds for [OEM-specific battery optimization and app killing behavior](https://dontkillmyapp.com/)
* Workarounds for devices that don't support the [`VOICE_CALL` audio source](https://developer.android.com/reference/android/media/MediaRecorder.AudioSource#VOICE_CALL) (eg. using microphone + speakerphone)
* Support for direct boot mode (the state before the device is initially unlocked after reboot)
* Support for stock, unrooted firmware

### Usage

1. Download the latest version from the [releases page](https://github.com/chenxiaolong/BCR/releases). To verify the digital signature, see the [verifying digital signatures](#verifying-digital-signatures) section.

2. Install BCR as a system app.

    * **For devices rooted with Magisk**, simply flash the zip as a Magisk module from within the Magisk app.
        * **For OnePlus and Realme devices running the stock firmware (or custom firmware based on the stock firmware)**, also extract the `.apk` from the zip and install it manually before rebooting. This is necessary to work around a bug in the firmware where the app data directory does not get created, causing BCR to open up to a blank screen.

    * **For unrooted custom firmware**, flash the zip while booted into recovery.
        * **NOTE**: If the custom firmware's `system` partition is formatted with `erofs`, then the filesystem is read-only and it is not possible to use this method.
        * Manually extracting the files from the `system/` folder in the zip will also work as long as the files have `644` permissions and the `u:object_r:system_file:s0` SELinux label.

3. Reboot and open BCR.

4. Enable call recording and pick an output directory. If no output directory is selected or if the output directory is no longer accessible, then recordings will be saved to `/sdcard/Android/data/com.chiller3.bcr/files`.

    When enabling call recording the first time, BCR will prompt for microphone, notification (Android 13+), contacts, and phone permissions. Only microphone and notification permissions are required basic call recording functionality. If additional permissions are granted, more information is added to the output filename. For example, the contacts permission will cause the contact name to be added to the filename and the phone permission will cause the SIM slot (if multiple SIMs are active) to be added to the filename.

    See the [permissions section](#permissions) below for more details about the permissions.

5. To install future updates, there are a couple methods:

    * If installed via Magisk, the module can be updated right from Magisk Manager's modules tab. Flashing the new version in Magisk manually also works just as well.
    * The `.apk` can also be extracted from the zip and be directly installed. With this method, the old version exists as a system app and the new version exists as a user-installed update to the system app. This method is more convenient if BCR is baked into the Android firmware image.

### Permissions

* `CAPTURE_AUDIO_OUTPUT` (**automatically granted by system app permissions**)
  * Needed to capture the call audio stream.
* `CONTROL_INCALL_EXPERIENCE` (**automatically granted by system app permissions**)
  * Needed to monitor the phone call state for starting and stopping the recording and gathering call information for the output filename.
* `RECORD_AUDIO` (**must be granted by the user**)
  * Needed to capture the call audio stream.
* `FOREGROUND_SERVICE` (**automatically granted at install time**)
  * Needed to run the call recording service.
* `POST_NOTIFICATIONS` (**must be granted by the user on Android 13+**)
  * Needed to show notifications.
  * A notification is required for running the call recording service in foreground mode or else Android will not allow access to the call audio stream.
* `READ_CONTACTS` (**optional**)
  * If allowed, the contact name is added to the output filename.
* `READ_PHONE_STATE` (**optional**)
  * If allowed, the SIM slot for devices with multiple active SIMs is added to the output filename.
* `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (**optional**)
  * If allowed, request Android to disable battery optimizations (app killing) for BCR.
  * This is usually not needed. The way BCR hooks into the telephony system makes it unlikely to be killed.
  * OEM Android builds that stray further from AOSP may ignore this.
* `VIBRATE` (**automatically granted at install time**)
  * If vibration is enabled for BCR's notifications in Android's settings, BCR will perform the vibration. Android itself does not respect the vibration option when a phone call is active.

Note that `INTERNET` is _not_ in the list. BCR does not and will never access the network. BCR will never communicate with other apps either, except if the user explicitly taps on the `Open` or `Share` buttons in the notification shown when a recording completes. In that scenario, the target app is granted access to that single recording only.

### Advanced features

This section describes BCR's advanced features that are hidden or only accessible via a config file.

#### Debug mode

BCR has a hidden debug mode that can be enabled or disabled by long pressing the version number.

When debug mode is enabled, BCR will write a log file to the output directory after a call recording completes. It is named the same way as the audio file. The log file contains the same messages as what `adb logcat` would show, except messages not relevant to BCR are filtered out (BCR does not have permission to access those messages anyway).

Within the log file, BCR aims to never log any sensitive information. Information about the current call, like the phone number, are replaced with placeholders instead, like `<phone number>`. However, other information can't be easily redacted this way will be truncated instead. For example, when the file rentention feature cleans up old files, filenames, like `20230101_010203.456+0000_out_1234567890_John_Doe.oga`, are logged as `20<...>ga`.

When reporting bugs, please include the log file as it is extremely helpful for identifying what might be wrong. (But please double check the log file to ensure there's no sensitive information!)

#### Customizing the output filename

By default, BCR uses a filename template that includes the call timestamp, call direction, SIM slot, phone number, caller ID, and contact name. This can be customized, but only by editing a config file. To do so, the easiest way is to copy [the default config](./app/src/main/res/raw/filename_template.properties) to `bcr.properties` in the output directory and then edit it to your liking. Details about the available fields are documented in the default config file.

For example, to customize the filename template to `<date as yyyyMMdd_HHmmss>_<phone number>_<caller ID>`, use the following config:

```properties
filename.0.text = ${date:yyyyMMdd_HHmmss}

filename.1.text = ${phone_number}
filename.1.prefix = _

filename.2.text = ${caller_name}
filename.2.prefix = _
```

The are a couple limitations to note:

* The date must always be at the beginning of the filename. This is required for the file rentention feature to work.
* If the date format is changed (eg. from the default to `yyyyMMdd_HHmmss`), then you must manually rename the old recordings to use the new date format or they may be handled incorrectly by the file rentention feature. To be safe, move the old recordings to a different folder while testing (or set the file rentention to `Keep all`).

If the config file has any error, BCR will use the default configuration. This ensures that recordings won't fail if the configuration is incorrect. To troubleshoot issues with the filename template, [enable debug mode](#debug-mode), and make a call. Then, search the log file for `FilenameTemplate`.

### How it works

BCR relies heavily on system app permissions in order to function properly. This is primarily because of two permissions:

* `CONTROL_INCALL_EXPERIENCE`

    This permission allows Android's telephony service to bind to BCR's `InCallService` without BCR being a wearable companion app, a car UI, or the default dialer. Once bound, the service will receive callbacks for call change events (eg. incoming call in the ringing state). This method is much more reliable than using the `READ_PHONE_STATE` permission and relying on `android.intent.action.PHONE_STATE` broadcasts.

    This method has a couple additional benefits. Due to the way that the telephony service binds to BCR's `InCallService`, the service can bring itself in and out of the foreground as needed when a call is in progress and access the audio stream without hitting Android 12+'s background microphone access limitations. It also does not require the service to be manually started from an `ACTION_BOOT_COMPLETED` broadcast receiver and thus is not affected by that broadcast's delays during initial boot.

* `CAPTURE_AUDIO_OUTPUT`

    This permission is used to record from the `VOICE_CALL` audio stream. This stream, along with some others, like `VOICE_DOWNLINK` and `VOICE_UPLINK`, cannot be accessed without this system permission.

With these two permissions, BCR can reliably detect phone calls and record from the call's audio stream. The recording process pulls PCM s16le raw audio and uses Android's built-in encoders to produce the compressed output file.

### Verifying digital signatures

Both the zip file and the APK contained within are digitally signed.

To verify the signature of the zip file, first retrieve the public key: `2233C479609BDCEC43BE9232F6A3B19090EFF32C`. This is the same key used to sign the git tags in this repository.

```bash
gpg --recv-key 2233C479609BDCEC43BE9232F6A3B19090EFF32C
```

Then, verify the signature of the zip file.

```bash
gpg --verify BCR-<version>-release.zip.asc BCR-<version>-release.zip
```

The command output should include both `Good signature` and the GPG fingerprint listed above.

To verify the signature of the APK, extract it from the zip and then run:

```
apksigner verify --print-certs system/priv-app/com.chiller3.bcr/app-release.apk
```

The SHA-256 digest of the APK signing certificate is:

```
d16f9b375df668c58ef4bb855eae959713d6d02e45f7f2c05ce2c27ae944f4f9
```

### Building from source

BCR can be built like most other Android apps using Android Studio or the gradle command line.

To build the APK:

```bash
./gradlew assembleDebug
```

To build the Magisk module zip (which automatically runs the `assembleDebug` task if needed):

```bash
./gradlew zipDebug
```

The output file is written to `app/build/distributions/debug/`. The APK will be signed with the default autogenerated debug key.

To create a release build with a specific signing key, set up the following environment variables:

```bash
export RELEASE_KEYSTORE=/path/to/keystore.jks
export RELEASE_KEY_ALIAS=alias_name

read -r -s RELEASE_KEYSTORE_PASSPHRASE
read -r -s RELEASE_KEY_PASSPHRASE
export RELEASE_KEYSTORE_PASSPHRASE
export RELEASE_KEY_PASSPHRASE
```

and then build the release zip:

```bash
./gradlew zipRelease
```

### Contributing

Bug fix and translation pull requests are welcome and much appreciated!

If you are interested in implementing a new feature and would like to see it included in BCR, please open an issue to discuss it first. I intend for BCR to be as simple and low-maintenance as possible, so I am not too inclined to add any new features, but I could be convinced otherwise.

### License

BCR is licensed under GPLv3. Please see [`LICENSE`](./LICENSE) for the full license text.
