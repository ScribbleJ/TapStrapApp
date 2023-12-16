#!/bin/bash
sed -n 's/^KEYCODE_\([0-9A-Z_]*\)/        "\1"     to { handleUnprintable(KeyEvent.KEYCODE_\1) },/p' ./keycodes.txt
