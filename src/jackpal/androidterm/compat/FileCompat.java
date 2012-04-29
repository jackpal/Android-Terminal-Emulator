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

package jackpal.androidterm.compat;

import java.io.File;

/**
 * Compatibility class for java.io.File
 */
public class FileCompat {
    private static class Api9OrLater {
        public static boolean canExecute(File file) {
            return file.canExecute();
        }
    }

    private static class Api8OrEarlier {
        static {
            System.loadLibrary("jackpal-androidterm4");
        }

        public static boolean canExecute(File file) {
            return testExecute(file.getAbsolutePath());
        }

        private static native boolean testExecute(String pathname);
    }

    public static boolean canExecute(File file) {
        if (AndroidCompat.SDK < 9) {
            return Api8OrEarlier.canExecute(file);
        } else {
            return Api9OrLater.canExecute(file);
        }
    }
}
