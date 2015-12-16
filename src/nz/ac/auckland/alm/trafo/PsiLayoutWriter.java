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

import com.intellij.openapi.project.Project;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.*;


public class PsiLayoutWriter {
  static public void write(Item item, XmlFile outFile, Project project) {
    XmlElementFactory factory = XmlElementFactory.getInstance(project);
    XmlDocument xmlDocument = outFile.getDocument();
    if (xmlDocument.getRootTag() != null)
      xmlDocument.getRootTag().delete();
    xmlDocument.add(toTag(item, factory));
  }

  static private XmlTag toTag(Item item, XmlElementFactory factory) {
    if (item instanceof Group)
      return toTagGroup((Group)item, factory);

    return copyDeep(item.getTag(), factory);
  }

  static private XmlTag toTagGroup(Group group, XmlElementFactory factory) {
    XmlTag groupTag;
    if (group.getTag() != null)
      groupTag = copyShallow(group.getTag(), factory);
    else
      groupTag = factory.createTagFromText("<LinearLayout/>");

    String orientationString = "horizontal";
    if (group.getOrientation() == Group.Orientation.VERTICAL)
      orientationString = "vertical";
    groupTag.setAttribute("android:orientation", orientationString);

    for (Item item : group.getChild())
      groupTag.addSubTag(toTag(item, factory), false);

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
