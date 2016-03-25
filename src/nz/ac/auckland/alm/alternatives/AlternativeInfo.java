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
package nz.ac.auckland.alm.alternatives;

import nz.ac.auckland.alm.Area;
import nz.ac.auckland.alm.algebra.Fragment;
import nz.ac.auckland.alm.algebra.trafo.FragmentAlternatives;


public class AlternativeInfo {
    private FragmentAlternatives.Result result;

    public AlternativeInfo(FragmentAlternatives.Result result) {
        this.result = result;
    }

    public FragmentAlternatives.Result getResult() {
        return result;
    }

    public Fragment getFragment() {
        return result.fragment;
    }

    public Area.Size getMinSize() {
        return ((Classification)result.classification).minSize;
    }

    public Area.Size getPrefSize() {
        return ((Classification)result.classification).prefSize;
    }

    public double getPrefRatio() {
        return getPrefSize().getWidth() / getPrefSize().getHeight();
    }
}

