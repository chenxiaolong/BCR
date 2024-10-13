#!/system/bin/sh

# SPDX-FileCopyrightText: 2024 Harin Lee
# SPDX-License-Identifier: GPL-3.0-only

# Delete addon.d script when installing as Magisk module to prevent
# update_engine from executing it on A/B devices.
[ -n "$MODPATH" ] && rm -r "$MODPATH/system/addon.d"
