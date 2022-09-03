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

* Changing the filename format
* Support for old Android versions (support is dropped as soon as maintenance becomes cumbersome)
* Workarounds for [OEM-specific battery optimization and app killing behavior](https://dontkillmyapp.com/)
* Workarounds for devices that don't support the [`VOICE_CALL` audio source](https://developer.android.com/reference/android/media/MediaRecorder.AudioSource#VOICE_CALL) (eg. using microphone + speakerphone)
* Support for direct boot mode (the state before the device is initially unlocked after reboot)
* Support for stock, unrooted firmware

### Usage

1. Download the latest version from the [releases page](https://github.com/chenxiaolong/BCR/releases). To verify the digital signature, see the [verifying digital signatures](#verifying-digital-signatures) section.

2. Install BCR as a system app.

    * **For devices rooted with Magisk**, simply flash the zip as a Magisk module from within the Magisk app.
        * **For OnePlus devices running the stock firmware (or custom firmware based on the stock firmware)**, also extract the `.apk` from the zip and install it manually before rebooting. This is necessary to work around a bug in the firmware where the app data directory does not get created, causing BCR to open up to a blank screen.

    * **For unrooted custom firmware**, flash the zip while booted into recovery.
        * Manually extracting the files from the `system/` folder in the zip will also work as long as the files have `644` permissions and the `u:object_r:system_file:s0` SELinux label.

3. Reboot and open BCR.

4. Enable call recording and pick an output directory. If no output directory is selected or if the output directory is no longer accessible, then recordings will be saved to `/sdcard/Android/data/com.chiller3.bcr/files`.

    When enabling call recording the first time, BCR will prompt for microphone, notification (Android 13+), and contacts permissions. Microphone and notification permissions are required for BCR to be able to record phone calls in the background.

    The contacts permission is optional, but if allowed, contact names will be added to the recordings' filenames. BCR never sends contact information anywhere. In fact, it does not even have the `INTERNET` permission. However, other third party applications may be able to see the contact names if they can access the output directory.

5. To install future updates, there are a couple methods:

    * If installed via Magisk, the module can be updated right from Magisk Manager's modules tab. Flashing the new version in Magisk manually also works just as well.
    * The `.apk` can also be extracted from the zip and be directly installed. With this method, the old version exists as a system app and the new version exists as a user-installed update to the system app. This method is more convenient if BCR is baked into the Android firmware image.

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
