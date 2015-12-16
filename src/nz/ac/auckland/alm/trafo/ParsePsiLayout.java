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

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;


public class ParsePsiLayout {
  static final private String LINEAR_LAYOUT_NAME = "LinearLayout";

  static public Item parse(XmlFile file) {
    XmlTag root = file.getDocument().getRootTag();

    return parse(root);
  }


  static private Item parse(XmlTag element) {
    if (element.getName().equals(LINEAR_LAYOUT_NAME))
      return parseLinearLayout(element);
    else {
      Item item = new Item();
      item.setTag(element);
      return item;
    }
  }

  static private Group parseLinearLayout(XmlTag layout) {
    Group.Orientation orientation = Group.Orientation.HORIZONTAL;
    XmlAttribute orientationAttribute = layout.getAttribute("android:orientation");
    if (orientationAttribute != null && orientationAttribute.getValue() != null && orientationAttribute.getValue().equals("vertical"))
      orientation = Group.Orientation.VERTICAL;

    Group group = new Group(orientation);
    group.setTag(layout);
    XmlTag[] child = layout.getSubTags();
    for (XmlTag c : child)
      group.addItem(parse(c));

    return group;
  }
}
