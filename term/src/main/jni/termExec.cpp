/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "common.h"

#define LOG_TAG "Exec"

#include <sys/types.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <unistd.h>
#include <termios.h>
#include <signal.h>

#include "termExec.h"

static void android_os_Exec_setPtyWindowSize(JNIEnv *env, jobject clazz,
    jint fd, jint row, jint col, jint xpixel, jint ypixel)
{
    struct winsize sz;

    sz.ws_row = row;
    sz.ws_col = col;
    sz.ws_xpixel = xpixel;
    sz.ws_ypixel = ypixel;

    ioctl(fd, TIOCSWINSZ, &sz);
}

static void android_os_Exec_setPtyUTF8Mode(JNIEnv *env, jobject clazz, jint fd, jboolean utf8Mode)
{
    struct termios tios;

    tcgetattr(fd, &tios);
    if (utf8Mode) {
        tios.c_iflag |= IUTF8;
    } else {
        tios.c_iflag &= ~IUTF8;
    }
    tcsetattr(fd, TCSANOW, &tios);
}

static void android_os_Exec_hangupProcessGroup(JNIEnv *env, jobject clazz, jint procId) {
    kill(-procId, SIGHUP);
}

static const char *classPathName = "jackpal/androidterm/Exec";
static JNINativeMethod method_table[] = {
    { "setPtyWindowSizeInternal", "(IIIII)V",
        (void*) android_os_Exec_setPtyWindowSize},
    { "setPtyUTF8ModeInternal", "(IZ)V",
        (void*) android_os_Exec_setPtyUTF8Mode},
    { "hangupProcessGroup", "(I)V",
        (void*) android_os_Exec_hangupProcessGroup}
};

int init_Exec(JNIEnv *env) {
    if (!registerNativeMethods(env, classPathName, method_table,
                 sizeof(method_table) / sizeof(method_table[0]))) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}
