/*
 * Copyright (C) 2012 Steven Luo
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

#define LOG_TAG "FileCompat"

#include <unistd.h>

#include "fileCompat.h"

static jboolean testExecute(JNIEnv *env, jobject clazz, jstring jPathString)
{
    const char *pathname = NULL;
    int result;

    /* XXX We should convert CESU-8 to UTF-8 to deal with potential non-BMP
       chars in pathname */
    pathname = env->GetStringUTFChars(jPathString, NULL);

    result = access(pathname, X_OK);

    env->ReleaseStringUTFChars(jPathString, pathname);
    return (result == 0);
}

static const char *classPathName = "jackpal/androidterm/compat/FileCompat$Api8OrEarlier";
static JNINativeMethod method_table[] = {
    { "testExecute", "(Ljava/lang/String;)Z", (void *) testExecute },
};

int init_FileCompat(JNIEnv *env) {
    if (!registerNativeMethods(env, classPathName, method_table,
                 sizeof(method_table) / sizeof(method_table[0]))) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}
