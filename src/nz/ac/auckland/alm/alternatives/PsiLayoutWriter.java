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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.*;
import nz.ac.auckland.alm.IArea;
import nz.ac.auckland.alm.algebra.Fragment;

import java.util.ArrayList;
import java.util.List;


public class PsiLayoutWriter {
  static public void write(IArea area, XmlFile outFile, Project project) {
    XmlElementFactory factory = XmlElementFactory.getInstance(project);
    XmlDocument xmlDocument = outFile.getDocument();
    if (xmlDocument.getRootTag() != null)
      xmlDocument.getRootTag().delete();
    XmlTag rootTag = toTag(area, factory, true);
    fixGroupLayoutSizesAndWeight(rootTag);
    xmlDocument.add(rootTag);
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

  static final private String WRAP_CONTENT = "wrap_content";
  static final private String MATCH_PARENT = "match_parent";
  static final private String FILL_PARENT = "fill_parent";

  static private XmlTag toTagGroup(Fragment fragment, XmlElementFactory factory, boolean rootTag) {
    XmlTag groupTag;
    if (getTag(fragment) != null)
      groupTag = copyShallow(getTag(fragment), factory);
    else {
      groupTag = factory.createTagFromText("<LinearLayout/>");
      if (rootTag)
        groupTag.setAttribute("xmlns:android", "http://schemas.android.com/apk/res/android");
    }
    if (rootTag) {
      groupTag.setAttribute("android:layout_width", MATCH_PARENT);
      groupTag.setAttribute("android:layout_height", MATCH_PARENT);
    } else {
      groupTag.setAttribute("android:layout_width", WRAP_CONTENT);
      groupTag.setAttribute("android:layout_height", WRAP_CONTENT);
    }
    String orientationString = "horizontal";
    if (fragment.isVerticalDirection())
      orientationString = "vertical";
    groupTag.setAttribute("android:orientation", orientationString);

    for (IArea item : (Iterable<IArea>)fragment.getItems())
      groupTag.addSubTag(toTag(item, factory, false), false);

    return groupTag;
  }

  static private boolean isLinearLayout(XmlTag tag) {
    return tag.getName().equals("LinearLayout");
  }

  static private boolean isHorizontal(XmlTag linearLayout) {
    return linearLayout.getAttribute("android:orientation").getValue().equals("horizontal");
  }

  static private boolean hasChildWithAttribute(XmlTag tag, String attribute, String value) {
    for (XmlTag child : tag.getSubTags()) {
      XmlAttribute xmlAttribute = child.getAttribute(attribute);
      if (xmlAttribute != null && xmlAttribute.getValue().equals(value))
        return true;
    }
    return false;
  }

  static private boolean hasChildWithMatchParent(XmlTag tag, String attribute) {
    for (XmlTag child : tag.getSubTags()) {
      XmlAttribute xmlAttribute = child.getAttribute(attribute);
      if (xmlAttribute != null && (xmlAttribute.getValue().equals(MATCH_PARENT) || xmlAttribute.getValue().equals(FILL_PARENT)))
        return true;
    }
    return false;
  }

  static private boolean hasNoneLinearLayoutWithMatchParent(XmlTag tag, String attribute) {
    for (XmlTag child : tag.getSubTags()) {
      if (isLinearLayout(child)) {
        boolean match = hasNoneLinearLayoutWithMatchParent(child, attribute);
        if (match)
          return true;
        continue;
      }
      XmlAttribute xmlAttribute = child.getAttribute(attribute);
      if (xmlAttribute != null && (xmlAttribute.getValue().equals(MATCH_PARENT) || xmlAttribute.getValue().equals(FILL_PARENT)))
        return true;
    }
    return false;
  }

  static private List<XmlTag> collectChildWithMatchParent(XmlTag tag, String attribute) {
    List<XmlTag> result = new ArrayList<XmlTag>();
    for (XmlTag child : tag.getSubTags()) {
      XmlAttribute xmlAttribute = child.getAttribute(attribute);
      if (xmlAttribute != null && (xmlAttribute.getValue().equals(MATCH_PARENT) || xmlAttribute.getValue().equals(FILL_PARENT)))
        result.add(child);
    }
    return result;
  }

  static private void setChildLinearLayoutAttribute(XmlTag tag, String attribute, String value) {
    for (XmlTag child : tag.getSubTags()) {
      if (isLinearLayout(child))
        child.setAttribute(attribute, value);
    }
  }

  static private List<XmlTag> collectChildWithAttribute(XmlTag tag, String attribute, String value) {
    List<XmlTag> result = new ArrayList<XmlTag>();
    for (XmlTag child : tag.getSubTags()) {
      XmlAttribute xmlAttribute = child.getAttribute(attribute);
      if (xmlAttribute != null && xmlAttribute.getValue().equals(value))
        result.add(child);
    }
    return result;
  }

  static private void fixGroupLayoutSizesAndWeight(XmlTag rootTag) {
    // find leafs layout with no further child layouts
    List<XmlTag> leafs = new ArrayList<XmlTag>();
    List<XmlTag> dirtyLeafs = new ArrayList<XmlTag>();
    dirtyLeafs.add(rootTag);
    while (dirtyLeafs.size() > 0) {
      XmlTag current = dirtyLeafs.remove(0);
      boolean hasChildLayout = false;
      for (XmlTag child : current.getSubTags()) {
        if (isLinearLayout(child)) {
          hasChildLayout = true;
          dirtyLeafs.add(child);
        }
      }
      // leaf found
      if (!hasChildLayout)
        leafs.add(current);
    }

    // fix attributes for all leafs
    for (XmlTag leaf : leafs) {
      XmlTag current = leaf;
      while (true) {
        // inherit match parent attribute from child (if there is a none linear layout child with match_parent)
        if (hasNoneLinearLayoutWithMatchParent(current, "android:layout_height"))
          current.setAttribute("android:layout_height", MATCH_PARENT);
        else
          current.setAttribute("android:layout_height", WRAP_CONTENT);
        if (hasNoneLinearLayoutWithMatchParent(current, "android:layout_width"))
          current.setAttribute("android:layout_width", MATCH_PARENT);
        else
          current.setAttribute("android:layout_width", WRAP_CONTENT);


        // for the leaf nodes set match parent in direction of the layout
        if (current == leaf) {
          if (isHorizontal(current)) current.setAttribute("android:layout_width", MATCH_PARENT);
          else current.setAttribute("android:layout_height", MATCH_PARENT);
        }

        // set weights for "match parent" items if there are more than one of them in a layout and
        if (isHorizontal(current)) {
          // match all siblings in orthogonal direction, this ensures all layout have sufficient extent
          if (hasChildWithMatchParent(current, "android:layout_height"))
            setChildLinearLayoutAttribute(current, "android:layout_height", MATCH_PARENT);

          List<XmlTag> competingChild = collectChildWithMatchParent(current, "android:layout_width");
          for (XmlTag child : competingChild)
            child.setAttribute("android:layout_weight", "1");
        } else {
          if (hasChildWithMatchParent(current, "android:layout_width"))
            setChildLinearLayoutAttribute(current, "android:layout_width", MATCH_PARENT);

          List<XmlTag> competingChild = collectChildWithMatchParent(current, "android:layout_height");
          for (XmlTag child : competingChild)
            child.setAttribute("android:layout_weight", "1");
        }

        if (current == rootTag)
          break;
        current = (XmlTag)current.getParent();
      }
    }

    // set root tags
    rootTag.setAttribute("android:layout_width", MATCH_PARENT);
    rootTag.setAttribute("android:layout_height", MATCH_PARENT);
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
