# Until Magisk supports overlayfs, we'll try to install to a non-overlayfs path
# that still supports privileged apps.
# https://github.com/topjohnwu/Magisk/pull/6588

has_overlays() {
    local mnt="${1}" count
    count=$(awk -v mnt="${mnt}" '$9 == "overlay" && $5 ~ mnt' /proc/self/mountinfo | wc -l)
    [ "${count}" -gt 0 ]
}

target=

for mountpoint in /system /product /system_ext /vendor; do
    if has_overlays "^${mountpoint}"; then
        echo "Cannot use ${mountpoint}: contains overlayfs mounts"
    # Magisk fails to mount files when the parent directory does not exist
    elif [ ! -d "${mountpoint}/etc/permissions" ]; then
        echo "Cannot use ${mountpoint}: etc/permissions/ does not exist"
    elif [ ! -d "${mountpoint}/priv-app" ]; then
        echo "Cannot use ${mountpoint}: priv-app/ does not exist"
    else
        echo "Using ${mountpoint} as the installation target"
        target=${mountpoint}
        break
    fi
done

if [ -z "${target}" ]; then
    echo 'No suitable installation target found'
    echo 'This OS is not supported'
    rm -rv "${MODPATH}" 2>&1
    exit 1
fi

if [ "${target}" != /system ]; then
    echo 'Removing addon.d script since installation target is not /system'
    rm -rv "${MODPATH}/system/addon.d" 2>&1 || exit 1

    echo "Adjusting overlay for installation to ${target}"
    mv -v "${MODPATH}/system" "${MODPATH}/${target#/}" 2>&1 || exit 1
    mkdir -v "${MODPATH}/system" 2>&1 || exit 1
    mv -v "${MODPATH}/${target#/}" "${MODPATH}/system/${target#/}" 2>&1 || exit 1
fi
