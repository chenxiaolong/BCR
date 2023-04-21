# READ_CALL_LOG is a hard-restricted permission in Android 10+. It cannot be
# granted by the user unless it is exempted by the system. The most common way
# to do this is via the installer, but that's not applicable when adding new
# system apps. Instead, we talk to the permission service directly over binder
# to alter the flags. This requires flashing a second time after a reboot so
# that the package manager is already aware of BCR.

app_id=$(grep '^id=' "${MODPATH}/module.prop" | cut -d= -f2)

CLASSPATH=$(find "${MODPATH}"/system/priv-app/"${app_id}" -name '*.apk') \
    app_process \
    / \
    com.chiller3.bcr.standalone.RemoveHardRestrictionsKt \
    2>&1

case "${?}" in
0|2)
    exit 0
    ;;
*)
    rm -rv "${MODPATH}" 2>&1
    exit 1
    ;;
esac
