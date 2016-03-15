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

import com.android.tools.idea.uibuilder.model.NlComponent;
import nz.ac.auckland.alm.IArea;
import nz.ac.auckland.alm.algebra.Fragment;
import nz.ac.auckland.alm.algebra.trafo.GroupDetector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class FilteredGroupDetector extends GroupDetector {
  public FilteredGroupDetector(Comparator<IArea> comparator) {
    super(comparator);
  }

  private boolean isLabelText(Fragment fragment) {
    if (fragment.size() != 2)
      return false;

    NlComponent component0 = (NlComponent)fragment.getItemAt(0).getCookie();
    NlComponent component1 = (NlComponent)fragment.getItemAt(1).getCookie();
    if (component0 == null || component1 == null)
      return false;

    if (!component0.getTagName().equals("TextView"))
      return false;
    if (!component1.getTagName().equals("EditText"))
      return false;

    return true;
  }
  private Float classifiy(Fragment fragment) {
    if (isLabelText(fragment))
      return 1f;

    Float value = new Float(0);
    for (IArea area : (Iterable<IArea>)fragment.getItems()) {
      if (area instanceof Fragment)
        value += classifiy((Fragment)area);
    }

    return value;
  }

  @Override
  public List<Fragment> detect(Fragment fragment) {
    List<Fragment> groups = super.detect(fragment);
    if (groups.size() <= 1) {
      groups.add(fragment);
      return groups;
    }
    Collections.sort(groups, new Comparator<Fragment>() {
      @Override
      public int compare(Fragment fragment0, Fragment fragment1) {
        return classifiy(fragment1).compareTo(classifiy(fragment0));
      }
    });
    List<Fragment> out = new ArrayList<Fragment>();
    out.add(groups.get(0));
    out.add(fragment);
    return out;
  }
}
