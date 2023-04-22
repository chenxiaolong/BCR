# READ_CALL_LOG is a hard-restricted permission in Android 10+. It cannot be
# granted by the user unless it is exempted by the system. The most common way
# to do this is via the installer, but that's not applicable when adding new
# system apps. Instead, we talk to the permission service directly over binder
# to alter the flags. This command blocks for an arbitrary amount of time
# because it needs to wait until the primary user unlocks the device.

exec >/data/local/tmp/bcr_remove_hard_restrictions.log 2>&1

mod_dir=${0%/*}

header() {
    echo "----- ${*} -----"
}

module_prop() {
    grep "^${1}=" "${mod_dir}/module.prop" | cut -d= -f2
}

app_id=$(module_prop id)
app_version=$(module_prop version)

header Environment
echo "Timestamp: $(date)"
echo "Args: ${0} ${*}"
echo "Version: ${app_version}"
echo "UID/GID/Context: $(id)"

header Remove hard restrictions
CLASSPATH=$(find "${mod_dir}"/system/priv-app/"${app_id}" -name '*.apk') \
    app_process \
    / \
    com.chiller3.bcr.standalone.RemoveHardRestrictionsKt &
pid=${!}
wait "${pid}"
echo "Exit status: ${?}"

header Logcat
logcat -d --pid "${pid}"

header Package state
dumpsys package "${app_id}"
