/*
 * Copyright (C) 2016 The Android Open Source Project
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;

import java.util.List;


public class WeakListenable<Listener> {
    final private List<WeakReference<Listener>> listeners = new ArrayList<WeakReference<Listener>>();

    public void addListener(int index, Listener listener) {
        if (hasListener(listener))
            return;
        listeners.add(index, new WeakReference<Listener>(listener));
    }

    public void addListener(Listener listener) {
        if (hasListener(listener))
            return;
        listeners.add(new WeakReference<Listener>(listener));
    }

    public boolean hasListener(Listener listener) {
        Iterator<WeakReference<Listener>> it = listeners.iterator();
        while (it.hasNext()) {
            WeakReference<Listener> listenerWeak = it.next();
            Listener listenerStrong = listenerWeak.get();
            if (listenerStrong == null) {
                it.remove();
                continue;
            }
            if (listenerWeak.get() == listener)
                return true;
        }
        return false;
    }

    public boolean removeListener(Listener listener) {
        Iterator<WeakReference<Listener>> it = listeners.iterator();
        while (it.hasNext()) {
            WeakReference<Listener> listenerWeak = it.next();
            Listener listenerStrong = listenerWeak.get();
            if (listenerStrong == null) {
                it.remove();
                continue;
            }
            if (listenerStrong == listener) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public List<Listener> getListeners() {
        List<Listener> outList = new ArrayList<Listener>();

        Iterator<WeakReference<Listener>> it = listeners.iterator();
        while (it.hasNext()) {
            WeakReference<Listener> listenerWeak = it.next();
            Listener listenerStrong = listenerWeak.get();
            if (listenerStrong == null) {
                it.remove();
                continue;
            }
            outList.add(listenerStrong);
        }

        return outList;
    }
}
