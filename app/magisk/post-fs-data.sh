# On some devices, the system time is set too late in the boot process. This,
# for some reason, causes the package manager service to not update the cache
# entry for BCR despite the mtime of the apk being newer than the mtime of the
# cache entry [1]. This causes BCR to crash with an obscure error about the app
# theme not being derived from Theme.AppCompat. This script works around the
# issue by forcibly deleting BCR's package manager cache entry on every boot.
#
# [1] https://cs.android.com/android/platform/superproject/+/android-13.0.0_r42:frameworks/base/services/core/java/com/android/server/pm/parsing/PackageCacher.java;l=139

source "${0%/*}/boot_common.sh" /data/local/tmp/bcr_clear_package_manager_caches.log

header Timestamps
ls -ldZ "${cli_apk%/*}"
find /data/system/package_cache -name "${app_id}-*" -exec ls -ldZ {} \+

header Clear package manager caches
run_cli_apk com.chiller3.bcr.standalone.ClearPackageManagerCachesKt
