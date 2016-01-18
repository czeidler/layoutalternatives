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
import com.intellij.openapi.project.Project;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.*;
import nz.ac.auckland.alm.IArea;
import nz.ac.auckland.alm.algebra.Fragment;

import java.util.List;


public class PsiLayoutWriter {
  static public void write(IArea area, XmlFile outFile, Project project) {
    XmlElementFactory factory = XmlElementFactory.getInstance(project);
    XmlDocument xmlDocument = outFile.getDocument();
    if (xmlDocument.getRootTag() != null)
      xmlDocument.getRootTag().delete();
    xmlDocument.add(toTag(area, factory, true));
  }

  static private XmlTag toTag(IArea area, XmlElementFactory factory, boolean rootTag) {
    if (area instanceof Fragment)
      return toTagGroup((Fragment)area, factory, rootTag);

    return copyDeep(getTag(area), factory);
  }

  static private XmlTag getTag(IArea area) {
    if (area.getCookie() == null)
      return null;
    return ((NlComponent)area.getCookie()).getTag();
  }

  static private XmlTag toTagGroup(Fragment fragment, XmlElementFactory factory, boolean rootTag) {
    XmlTag groupTag;
    if (getTag(fragment) != null)
      groupTag = copyShallow(getTag(fragment), factory);
    else {
      groupTag = factory.createTagFromText("<LinearLayout/>");
      groupTag.setAttribute("android:layout_width", "wrap_content");
      groupTag.setAttribute("android:layout_height", "wrap_content");
      if (rootTag)
        groupTag.setAttribute("xmlns:android", "http://schemas.android.com/apk/res/android");
    }

    String orientationString = "horizontal";
    if (fragment.isVerticalDirection())
      orientationString = "vertical";
    groupTag.setAttribute("android:orientation", orientationString);

    for (IArea item : (List<IArea>)fragment.getItems())
      groupTag.addSubTag(toTag(item, factory, false), false);

    return groupTag;
  }

  static private XmlTag copyShallow(XmlTag tag, XmlElementFactory factory) {
    XmlTag copy = factory.createTagFromText("<" + tag.getName() + "/>");
    for (XmlAttribute attribute : tag.getAttributes())
      copy.setAttribute(attribute.getName(), attribute.getDisplayValue());

    return copy;
  }

  static private XmlTag copyDeep(XmlTag tag, XmlElementFactory factory) {
    return (XmlTag)tag.copy();
    //XmlTag copy = copyShallow(tag, factory);
    //return copy;
  }

}
