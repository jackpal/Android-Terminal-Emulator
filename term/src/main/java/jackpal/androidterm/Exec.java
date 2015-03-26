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

package jackpal.androidterm;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.IllegalStateException;
import java.lang.Process;
import java.lang.reflect.Field;

/**
 * Utility methods for managing a pty file descriptor.
 */
public class Exec
{
    // Warning: bump the library revision, when an incompatible change happens
    static {
        System.loadLibrary("jackpal-androidterm5");
    }

    private static Field descriptorField;

    private static void cacheDescField() throws NoSuchFieldException {
        if (descriptorField != null)
            return;

        descriptorField = FileDescriptor.class.getDeclaredField("descriptor");
        descriptorField.setAccessible(true);
    }

    private static int getIntFd(ParcelFileDescriptor parcelFd) throws IOException {
        if (Build.VERSION.SDK_INT >= 12)
            return FdHelperHoneycomb.getFd(parcelFd);
        else {
            try {
                cacheDescField();

                return descriptorField.getInt(parcelFd.getFileDescriptor());
            } catch (Exception e) {
                throw new IOException("Unable to obtain file descriptor on this OS version: " + e.getMessage());
            }
        }
    }

    /**
     * Set the widow size for a given pty. Allows programs
     * connected to the pty learn how large their screen is.
     */
    public static void setPtyWindowSize(ParcelFileDescriptor fd, int row, int col, int xpixel, int ypixel) {
        // If the tty goes away too quickly, this may get called after it's descriptor is closed
        if (!fd.getFileDescriptor().valid())
            return;

        try {
            setPtyWindowSizeInternal(getIntFd(fd), row, col, xpixel, ypixel);
        } catch (IOException e) {
            // pretend that everything is ok...
            Log.e("exec", "Failed to set window size due to " + e.getMessage());
        }
    }

    /**
     * Set or clear UTF-8 mode for a given pty.  Used by the terminal driver
     * to implement correct erase behavior in cooked mode (Linux >= 2.6.4).
     */
    public static void setPtyUTF8Mode(ParcelFileDescriptor fd, boolean utf8Mode) {
        // If the tty goes away too quickly, this may get called after it's descriptor is closed
        if (!fd.getFileDescriptor().valid())
            return;

        try {
            setPtyUTF8ModeInternal(getIntFd(fd), utf8Mode);
        } catch (IOException e) {
            // pretend that everything is ok...
            Log.e("exec", "Failed to set UTF mode due to " + e.getMessage());
        }
    }

    /**
     * Close a given file descriptor.
     */
    public static void close(ParcelFileDescriptor fd) {
        try {
            fd.close();
        } catch (IOException e) {
            // ok
        }
    }

    /**
     * Send SIGHUP to a process group, SIGHUP notifies a terminal client, that the terminal have been disconnected,
     * and usually results in client's death, unless it's process is a daemon or have been somehow else detached
     * from the terminal (for example, by the "nohup" utility).
     */
    public static void hangupProcessGroup(int processId) {
        TermExec.sendSignal(-processId, 1);
    }

    private static native void setPtyWindowSizeInternal(int fd, int row, int col, int xpixel, int ypixel)
            throws IOException;

    private static native void setPtyUTF8ModeInternal(int fd, boolean utf8Mode)
            throws IOException;
}

