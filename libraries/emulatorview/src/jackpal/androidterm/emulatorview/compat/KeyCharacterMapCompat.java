/*
 * Copyright (C) 2011 Jack Palevich
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

package jackpal.androidterm.emulatorview.compat;

import android.view.KeyCharacterMap;

public abstract class KeyCharacterMapCompat {
    public static final int MODIFIER_BEHAVIOR_CHORDED = 0;
    public static final int MODIFIER_BEHAVIOR_CHORDED_OR_TOGGLED = 1;

    public static KeyCharacterMapCompat wrap(Object map) {
        if (map != null) {
            if (AndroidCompat.SDK >= 11) {
                return new KeyCharacterMapApi11OrLater(map);
            }
        }
        return null;
    }

    private static class KeyCharacterMapApi11OrLater
        extends KeyCharacterMapCompat {
        private KeyCharacterMap mMap;
        public KeyCharacterMapApi11OrLater(Object map) {
            mMap = (KeyCharacterMap) map;
        }
        public int getModifierBehaviour() {
            return mMap.getModifierBehavior();
        }
    }

    public abstract int getModifierBehaviour();
}
