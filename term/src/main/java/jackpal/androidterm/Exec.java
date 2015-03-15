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
import java.lang.reflect.Field;

/**
 * Utility methods for creating and managing a subprocess.
 * <p>
 * Note: The native methods access a package-private
 * java.io.FileDescriptor field to get and set the raw Linux
 * file descriptor. This might break if the implementation of
 * java.io.FileDescriptor is changed.
 */

public class Exec
{
    static {
        System.loadLibrary("jackpal-androidterm4");
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
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new IOException("Unable to obtain file descriptor on this OS version: " + e.getMessage());
            }
        }
    }

    /**
     * Set the widow size for a given pty. Allows programs
     * connected to the pty learn how large their screen is.
     */
    public static void setPtyWindowSize(ParcelFileDescriptor fd, int row, int col, int xpixel, int ypixel) {
        if (!fd.getFileDescriptor().valid()) // TODO why is this called after the stream is closed?
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
        if (!fd.getFileDescriptor().valid()) // TODO why is this called after the stream is closed?
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
     * Send SIGHUP to a process group.
     */
    public static native void hangupProcessGroup(int processId);

    private static native void setPtyWindowSizeInternal(int fd, int row, int col, int xpixel, int ypixel);

    private static native void setPtyUTF8ModeInternal(int fd, boolean utf8Mode);
}

