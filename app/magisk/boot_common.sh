# source "${0%/*}/boot_common.sh" <log file>

exec >"${1}" 2>&1

mod_dir=${0%/*}

header() {
    echo "----- ${*} -----"
}

module_prop() {
    grep "^${1}=" "${mod_dir}/module.prop" | cut -d= -f2
}

run_cli_apk() {
    CLASSPATH="${cli_apk}" app_process / "${@}" &
    pid=${!}
    wait "${pid}"
    echo "Exit status: ${?}"
    echo "Logcat:"
    logcat -d --pid "${pid}"
}

app_id=$(module_prop id)
app_version=$(module_prop version)
cli_apk=$(echo "${mod_dir}"/system/priv-app/"${app_id}"/app-*.apk)

header Environment
echo "Timestamp: $(date)"
echo "Script: ${0}"
echo "App ID: ${app_id}"
echo "App version: ${app_version}"
echo "CLI APK: ${cli_apk}"
echo "UID/GID/Context: $(id)"
