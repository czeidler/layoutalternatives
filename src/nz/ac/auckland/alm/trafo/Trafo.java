/*
 * Copyright (C) 2015 The Android Open Source Project
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
package nz.ac.auckland.alm.trafo;

import nz.ac.auckland.alm.IArea;
import nz.ac.auckland.alm.algebra.Fragment;

import java.util.List;


public class Trafo {
  static public void swapOrientation(Fragment fragment, boolean recursive) {
    swapOrientation(fragment);
    if (!recursive)
      return;
    for (IArea item : (List<IArea>)fragment.getItems()) {
      if (item instanceof Fragment)
        swapOrientation((Fragment)item, recursive);
    }
  }

  static private void swapOrientation(Fragment fragment) {
    if (fragment.isHorizontalDirection())
      fragment.setVerticalDirection();
    else
      fragment.setHorizontalDirection();
  }

  static public void makeColumn(Fragment fragment) {
    if (fragment.isVerticalDirection())
      return;
    List<IArea> child = fragment.getItems();
    int nItems = child.size();
    int nHalf = nItems / 2;
    if (nHalf == 0)
      return;

    swapOrientation(fragment);

    Fragment column1 = Fragment.verticalFragment();
    for (int i = 0; i < nHalf; i++) {
      IArea item = (IArea)fragment.getItems().remove(0);
      column1.add(item, false);
    }
    Fragment column2 = Fragment.verticalFragment();
    for (int i = 0; i < nItems - nHalf; i++) {
      IArea item = (IArea)fragment.getItems().remove(0);
      column2.add(item, false);
    }
    fragment.add(column1, false);
    fragment.add(column2, false);
  }
}

