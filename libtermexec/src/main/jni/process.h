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

#ifndef _JACKPAL_PROCESS_H
#define _JACKPAL_PROCESS_H 1

#include <stddef.h>
#include "jni.h"
#include <android/log.h>

#define LOG_TAG "jackpal-termexec"

extern "C" {
JNIEXPORT jint JNICALL Java_jackpal_androidterm_TermExec_createSubprocessInternal
      (JNIEnv *, jclass, jstring, jobjectArray, jobjectArray, jint);

    JNIEXPORT jint JNICALL Java_jackpal_androidterm_TermExec_waitFor
      (JNIEnv *, jclass, jint);
}

#endif	/* !defined(_JACKPAL_PROCESS_H) */
