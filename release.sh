export RELEASE_KEYSTORE=/Users/manasvi/Desktop/apollo/android/key.jks
export RELEASE_KEY_ALIAS=key
export RELEASE_KEYSTORE_PASSPHRASE=doc@play
export RELEASE_KEY_PASSPHRASE=doc@play

./gradlew zipRelease
adb push ./app/build/distributions/release/* /storage/emulated/0/Download