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

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import nz.ac.auckland.alm.IArea;
import nz.ac.auckland.alm.TabArea;
import nz.ac.auckland.alm.algebra.Fragment;


public class ParsePsiLayout {
  static final private String LINEAR_LAYOUT_NAME = "LinearLayout";

  static public IArea parse(NlComponent component) {
    XmlTag element = component.getTag();
    if (element.getName().equals(LINEAR_LAYOUT_NAME))
      return parseLinearLayout(component);
    else {
      IArea item = new TabArea();
      item.setCookie(component);
      return item;
    }
  }

  static private IArea parseLinearLayout(NlComponent layoutComponent) {
    XmlTag layout = layoutComponent.getTag();
    XmlAttribute orientationAttribute = layout.getAttribute("android:orientation");
    Fragment fragment;
    if (orientationAttribute != null && orientationAttribute.getValue() != null && orientationAttribute.getValue().equals("vertical"))
      fragment = Fragment.createEmptyFragment(Fragment.verticalDirection);
    else
      fragment = Fragment.createEmptyFragment(Fragment.horizontalDirection);
    fragment.setCookie(layoutComponent);
    for (int i = 0; i < layoutComponent.getChildCount(); i++) {
      NlComponent child = layoutComponent.getChild(i);
      fragment.add(parse(child), false);
    }

    return fragment;
  }
  /*
  static public Item parse(NlComponent component) {
    XmlTag element = component.getTag();
    if (element.getName().equals(LINEAR_LAYOUT_NAME))
      return parseLinearLayout(component);
    else {
      Item item = new Item();
      item.setComponent(component);
      return item;
    }
  }

  static private Group parseLinearLayout(NlComponent layoutComponent) {
    XmlTag layout = layoutComponent.getTag();
    Group.Orientation orientation = Group.Orientation.HORIZONTAL;
    XmlAttribute orientationAttribute = layout.getAttribute("android:orientation");
    if (orientationAttribute != null && orientationAttribute.getValue() != null && orientationAttribute.getValue().equals("vertical"))
      orientation = Group.Orientation.VERTICAL;

    Group group = new Group(orientation);
    group.setComponent(layoutComponent);
    for (int i = 0; i < layoutComponent.getChildCount(); i++) {
      NlComponent child = layoutComponent.getChild(i);
      group.add(parse(child));
    }

    return group;
  }*/
}
