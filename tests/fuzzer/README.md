This directory contains files to help reproduce issue 145

fuzzer.go generates random strings of UTF-encoded characters.

issue145repro.txt - some text from a run of fuzzer.go.

Created by ./fuzzer | head -c 983 | tail -c 6 >issue145repro.txt

This is a minimal repro case for

https://github.com/jackpal/Android-Terminal-Emulator/issues/145

Repro steps
-----------

On a PC:

adb shell mkdir /data/local
adb push issue145repro.txt /data/local/issue145repro.txt

Run ATE

Configure ATE's preferences for UTF8

On ATE:

cat /data/local/issue145repro.txt
