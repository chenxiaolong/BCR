# READ_CALL_LOG is a hard-restricted permission in Android 10+. It cannot be
# granted by the user unless it is exempted by the system. The most common way
# to do this is via the installer, but that's not applicable when adding new
# system apps. Instead, we talk to the permission service directly over binder
# to alter the flags. This command blocks for an arbitrary amount of time
# because it needs to wait until the primary user unlocks the device.

source "${0%/*}/boot_common.sh" /data/local/tmp/bcr_remove_hard_restrictions.log

header Remove hard restrictions
run_cli_apk com.chiller3.bcr.standalone.RemoveHardRestrictionsKt

header Package state
dumpsys package "${app_id}"
