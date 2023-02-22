# Until Magisk supports overlayfs, we'll try to install to a non-overlayfs path
# that still supports privileged apps.
# https://github.com/topjohnwu/Magisk/pull/6588

has_overlays() {
    local mnt="${1}" count
    count=$(awk -v mnt="${mnt}" '$9 == "overlay" && $5 ~ mnt' /proc/self/mountinfo | wc -l)
    [ "${count}" -gt 0 ]
}

module_overlay_dir=

for candidate in \
    /system::system \
    /product::system/product \
    /system_ext::system/system_ext \
    /vendor::system/vendor
do
    mountpoint=${candidate%::*}

    if has_overlays "^${mountpoint}"; then
        echo "Cannot use ${mountpoint}: contains overlayfs mounts"
    # Magisk fails to mount files when the parent directory does not exist
    elif [ ! -d "${mountpoint}/etc/permissions" ]; then
        echo "Cannot use ${mountpoint}: etc/permissions/ does not exist"
    elif [ ! -d "${mountpoint}/priv-app" ]; then
        echo "Cannot use ${mountpoint}: priv-app/ does not exist"
    else
        echo "Using ${mountpoint} as the installation target"
        module_overlay_dir=${candidate#*::}
        break
    fi
done

if [ -z "${module_overlay_dir}" ]; then
    echo 'No suitable installation target found'
    echo 'This OS is not supported'
    rm -rv "${MODPATH}" 2>&1
    exit 1
fi

if [ "${module_overlay_dir}" != system ]; then
    echo 'Removing addon.d script since installation target is not /system'
    rm -rv "${MODPATH}/overlay/addon.d" 2>&1 || exit 1
fi

mkdir -vp "$(dirname "${MODPATH}/${module_overlay_dir}")" 2>&1 || exit 1
mv -v "${MODPATH}/overlay" "${MODPATH}/${module_overlay_dir}" 2>&1 || exit 1
