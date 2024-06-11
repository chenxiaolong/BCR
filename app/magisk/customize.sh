#!/system/bin/sh
# Delete addon.d script when installing as Magisk module
[ -n "$MODPATH" ] && rm -r "$MODPATH/system/addon.d"
