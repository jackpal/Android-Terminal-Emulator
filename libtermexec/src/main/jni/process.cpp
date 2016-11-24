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

#include "process.h"

#include <sys/types.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <unistd.h>
#include <termios.h>
#include <signal.h>

typedef unsigned short char16_t;

class String8 {
public:
    String8() {
        mString = 0;
    }

    ~String8() {
        if (mString) {
            free(mString);
        }
    }

    void set(const char16_t* o, size_t numChars) {
        if (mString) {
            free(mString);
        }
        mString = (char*) malloc(numChars + 1);
        if (!mString) {
            return;
        }
        for (size_t i = 0; i < numChars; i++) {
            mString[i] = (char) o[i];
        }
        mString[numChars] = '\0';
    }

    const char* string() {
        return mString;
    }
private:
    char* mString;
};

static int throwOutOfMemoryError(JNIEnv *env, const char *message)
{
    jclass exClass;
    const char *className = "java/lang/OutOfMemoryError";

    exClass = env->FindClass(className);
    return env->ThrowNew(exClass, message);
}

static int throwIOException(JNIEnv *env, int errnum, const char *message)
{
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s errno %s(%d)",
        message, strerror(errno), errno);

    if (errnum != 0) {
        const char *s = strerror(errnum);
        if (strcmp(s, "Unknown error") != 0)
            message = s;
    }

    jclass exClass;
    const char *className = "java/io/IOException";

    exClass = env->FindClass(className);
    return env->ThrowNew(exClass, message);
}

static void closeNonstandardFileDescriptors() {
    // Android uses shared memory to communicate between processes. The file descriptor is passed
    // to child processes using the environment variable ANDROID_PROPERTY_WORKSPACE, which is of
    // the form "properties_fd,sizeOfSharedMemory"
    int properties_fd = -1;
    char* properties_fd_string = getenv("ANDROID_PROPERTY_WORKSPACE");
    if (properties_fd_string != NULL) {
        properties_fd = atoi(properties_fd_string);
    }
    DIR *dir = opendir("/proc/self/fd");
    if(dir != NULL) {
        int dir_fd = dirfd(dir);

        while(true) {
            struct dirent *entry = readdir(dir);
            if(entry == NULL) {
                break;
            }

            int fd = atoi(entry->d_name);
            if(fd > STDERR_FILENO && fd != dir_fd && fd != properties_fd) {
                close(fd);
            }
        }

        closedir(dir);
    }
}

static int create_subprocess(JNIEnv *env, const char *cmd, char *const argv[], char *const envp[], int masterFd)
{
    // same size as Android 1.6 libc/unistd/ptsname_r.c
    char devname[64];
    pid_t pid;

    fcntl(masterFd, F_SETFD, FD_CLOEXEC);

    // grantpt is unnecessary, because we already assume devpts by using /dev/ptmx
    if(unlockpt(masterFd)){
        throwIOException(env, errno, "trouble with /dev/ptmx");
        return -1;
    }
    memset(devname, 0, sizeof(devname));
    // Early (Android 1.6) bionic versions of ptsname_r had a bug where they returned the buffer
    // instead of 0 on success.  A compatible way of telling whether ptsname_r
    // succeeded is to zero out errno and check it after the call
    errno = 0;
    int ptsResult = ptsname_r(masterFd, devname, sizeof(devname));
    if (ptsResult && errno) {
        throwIOException(env, errno, "ptsname_r returned error");
        return -1;
    }

    pid = fork();
    if(pid < 0) {
        throwIOException(env, errno, "fork failed");
        return -1;
    }

    if(pid == 0){
        int pts;

        setsid();

        pts = open(devname, O_RDWR);
        if(pts < 0) exit(-1);

        ioctl(pts, TIOCSCTTY, 0);

        dup2(pts, 0);
        dup2(pts, 1);
        dup2(pts, 2);

        closeNonstandardFileDescriptors();

        if (envp) {
            for (; *envp; ++envp) {
                putenv(*envp);
            }
        }

        execv(cmd, argv);
        exit(-1);
    } else {
        return (int) pid;
    }
}

extern "C" {

JNIEXPORT void JNICALL Java_jackpal_androidterm_TermExec_sendSignal(JNIEnv *env, jobject clazz,
    jint procId, jint signal)
{
    kill(procId, signal);
}

JNIEXPORT jint JNICALL Java_jackpal_androidterm_TermExec_waitFor(JNIEnv *env, jclass clazz, jint procId) {
    int status;
    waitpid(procId, &status, 0);
    int result = 0;
    if (WIFEXITED(status)) {
        result = WEXITSTATUS(status);
    }
    return result;
}

JNIEXPORT jint JNICALL Java_jackpal_androidterm_TermExec_createSubprocessInternal(JNIEnv *env, jclass clazz,
    jstring cmd, jobjectArray args, jobjectArray envVars, jint masterFd)
{
    const jchar* str = cmd ? env->GetStringCritical(cmd, 0) : 0;
    String8 cmd_8;
    if (str) {
        cmd_8.set(str, env->GetStringLength(cmd));
        env->ReleaseStringCritical(cmd, str);
    }

    jsize size = args ? env->GetArrayLength(args) : 0;
    char **argv = NULL;
    String8 tmp_8;
    if (size > 0) {
        argv = (char **)malloc((size+1)*sizeof(char *));
        if (!argv) {
            throwOutOfMemoryError(env, "Couldn't allocate argv array");
            return 0;
        }
        for (int i = 0; i < size; ++i) {
            jstring arg = reinterpret_cast<jstring>(env->GetObjectArrayElement(args, i));
            str = env->GetStringCritical(arg, 0);
            if (!str) {
                throwOutOfMemoryError(env, "Couldn't get argument from array");
                return 0;
            }
            tmp_8.set(str, env->GetStringLength(arg));
            env->ReleaseStringCritical(arg, str);
            argv[i] = strdup(tmp_8.string());
        }
        argv[size] = NULL;
    }

    size = envVars ? env->GetArrayLength(envVars) : 0;
    char **envp = NULL;
    if (size > 0) {
        envp = (char **)malloc((size+1)*sizeof(char *));
        if (!envp) {
            throwOutOfMemoryError(env, "Couldn't allocate envp array");
            return 0;
        }
        for (int i = 0; i < size; ++i) {
            jstring var = reinterpret_cast<jstring>(env->GetObjectArrayElement(envVars, i));
            str = env->GetStringCritical(var, 0);
            if (!str) {
                throwOutOfMemoryError(env, "Couldn't get env var from array");
                return 0;
            }
            tmp_8.set(str, env->GetStringLength(var));
            env->ReleaseStringCritical(var, str);
            envp[i] = strdup(tmp_8.string());
        }
        envp[size] = NULL;
    }

    int ptm = create_subprocess(env, cmd_8.string(), argv, envp, masterFd);

    if (argv) {
        for (char **tmp = argv; *tmp; ++tmp) {
            free(*tmp);
        }
        free(argv);
    }
    if (envp) {
        for (char **tmp = envp; *tmp; ++tmp) {
            free(*tmp);
        }
        free(envp);
    }

    return ptm;
}

}
