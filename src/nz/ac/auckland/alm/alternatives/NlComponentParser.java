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
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import nz.ac.auckland.alm.Area;
import nz.ac.auckland.alm.IArea;
import nz.ac.auckland.alm.algebra.Fragment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;


public class NlComponentParser {
  static final private String LINEAR_LAYOUT_NAME = "LinearLayout";

  static public IArea parse(NlComponent component) {
    XmlTag element = component.getTag();
    if (element.getName().equals(LINEAR_LAYOUT_NAME))
      return parseLinearLayout(component);
    else {
      IArea item = toArea(component);
      return item;
    }
  }

  static private Fragment parseLinearLayout(NlComponent layoutComponent) {
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

  static private Area toArea(NlComponent component) {
    Area area = new Area();
    area.setCookie(component);
    area.setMinSize(getMinSize(component));
    area.setPreferredSize(getPreferredSize(component));
    area.setMaxSize(getMaxSize(component));
    return area;
  }

  /**
   * Taken from the Android source code.
   *
   * @param size
   * @param mode
   * @return
   */
  public static int makeMeasureSpec(int size, int mode) {
    final int MODE_SHIFT = 30;
    final int MODE_MASK  = 0x3 << MODE_SHIFT;
    return (size & ~MODE_MASK) | (mode & MODE_MASK);
  }

  static public Area.Size getMinSize(NlComponent component) {
    Object view = component.viewInfo.getViewObject();
    try {
      Method minWidth = view.getClass().getMethod("getMinimumWidth");
      Method minHeight = view.getClass().getMethod("getMinimumHeight");
      Object width = minWidth.invoke(view);
      Object height = minHeight.invoke(view);
      if ((Integer)width == 0 || (Integer)height == 0)
        return getPreferredSizeRaw(component);
      return new Area.Size((Integer)width, (Integer)height);
    }
    catch (Exception e) {
      return getPreferredSizeRaw(component);
    }
  }

  static public final int MATCH_PARENT = -1;
  static public final int WRAP_CONTENT = -2;

  static public final int AT_MOST = -2147483648;
  static public final int EXACTLY = 1073741824;
  static public final int UNSPECIFIED = 0;

  static private Area.Size getPreferredSizeRaw(NlComponent component) {
    return measureSizeAtMost(component, WRAP_CONTENT, WRAP_CONTENT);
  }

  static private Area.Size measureSizeAtMost(NlComponent component, int width, int height) {
    Object view = component.viewInfo.getViewObject();
    try {
      Method measure = view.getClass().getMethod("measure", int.class, int.class);
      Method getMeasuredWidth = view.getClass().getMethod("getMeasuredWidth");
      Method getMeasuredHeight = view.getClass().getMethod("getMeasuredHeight");
      measure.invoke(view, makeMeasureSpec(width, AT_MOST), makeMeasureSpec(height, AT_MOST));
      return new Area.Size((Integer)getMeasuredWidth.invoke(view), (Integer)getMeasuredHeight.invoke(view));
    } catch (Exception e) {
      return new Area.Size(Area.Size.UNDEFINED, Area.Size.UNDEFINED);
    }
  }

  static public Area.Size getPreferredSize(NlComponent component) {
    Object layoutParams = component.viewInfo.getLayoutParamsObject();
    int layoutParamsWidth;
    int layoutParamsHeight;
    try {
      Field width = layoutParams.getClass().getField("width");
      Field height = layoutParams.getClass().getField("height");
      layoutParamsWidth = (Integer)width.get(layoutParams);
      layoutParamsHeight = (Integer)height.get(layoutParams);
    } catch (Exception e) {
      return new Area.Size(Area.Size.UNDEFINED, Area.Size.UNDEFINED);
    }

    NlComponent rootComponent = component.getRoot();
    final int rootWidth = rootComponent.w;
    final int rootHeight = rootComponent.h;

    Area.Size prefSize;
    if (layoutParamsWidth == WRAP_CONTENT
        || layoutParamsHeight == WRAP_CONTENT)
      prefSize = getPreferredSizeRaw(component);
    else
      prefSize = new Area.Size(0, 0);

    // max width
    if (layoutParamsWidth == MATCH_PARENT)
      prefSize.setWidth(rootWidth);
    else if (layoutParamsWidth != WRAP_CONTENT)
      prefSize.setWidth(layoutParamsWidth);

    // max height
    if (layoutParamsHeight == MATCH_PARENT)
      prefSize.setHeight(rootHeight);
    else if (layoutParamsHeight != WRAP_CONTENT)
      prefSize.setHeight(layoutParamsHeight);

    return prefSize;
  }

  static public Area.Size getMaxSize(NlComponent component) {
    Object layoutParams = component.viewInfo.getLayoutParamsObject();
    int layoutParamsWidth;
    int layoutParamsHeight;
    try {
      Field width = layoutParams.getClass().getField("width");
      Field height = layoutParams.getClass().getField("height");
      layoutParamsWidth = (Integer)width.get(layoutParams);
      layoutParamsHeight = (Integer)height.get(layoutParams);
    } catch (Exception e) {
      return new Area.Size(Area.Size.UNDEFINED, Area.Size.UNDEFINED);
    }

    NlComponent rootComponent = component.getRoot();
    final int rootWidth = rootComponent.w;
    final int rootHeight = rootComponent.h;

    Area.Size maxSize;
    if (layoutParamsWidth == WRAP_CONTENT
        || layoutParamsHeight == WRAP_CONTENT)
      maxSize = measureSizeAtMost(component, rootWidth, rootHeight);
    else
      maxSize = new Area.Size(0, 0);

    // max width
    if (layoutParamsWidth == MATCH_PARENT)
      maxSize.setWidth(rootWidth);
    else if (layoutParamsWidth != WRAP_CONTENT)
      maxSize.setWidth(layoutParamsWidth);

    // max height
    if (layoutParamsHeight == MATCH_PARENT)
      maxSize.setHeight(rootHeight);
    else if (layoutParamsHeight != WRAP_CONTENT)
      maxSize.setHeight(layoutParamsHeight);

    return maxSize;
  }
}
