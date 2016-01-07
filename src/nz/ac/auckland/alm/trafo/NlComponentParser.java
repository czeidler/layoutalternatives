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
import nz.ac.auckland.alm.Area;

import java.lang.reflect.Field;
import java.lang.reflect.Method;


public class NlComponentParser {

  static public Area toArea(NlComponent component) {
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
      return new Area.Size((Integer)width, (Integer)height);
    }
    catch (Exception e) {
      return getPreferredSize(component);
    }
  }

  static public final int MATCH_PARENT = -1;
  static public final int WRAP_CONTENT = -2;

  static public final int AT_MOST = -2147483648;
  static public final int EXACTLY = 1073741824;
  static public final int UNSPECIFIED = 0;

  static public Area.Size getPreferredSize(NlComponent component) {
    Object view = component.viewInfo.getViewObject();
    try {
      Method measure = view.getClass().getMethod("measure", int.class, int.class);
      Method getMeasuredWidth = view.getClass().getMethod("getMeasuredWidth");
      Method getMeasuredHeight = view.getClass().getMethod("getMeasuredHeight");
      measure.invoke(view, makeMeasureSpec(WRAP_CONTENT, AT_MOST), makeMeasureSpec(WRAP_CONTENT, AT_MOST));
      return new Area.Size((Integer)getMeasuredWidth.invoke(view), (Integer)getMeasuredHeight.invoke(view));
    }
    catch (Exception e) {
      return new Area.Size(Area.Size.UNDEFINED, Area.Size.UNDEFINED);
    }
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
    }
    catch (Exception e) {
      return new Area.Size(Area.Size.UNDEFINED, Area.Size.UNDEFINED);
    }

    final int LARGE_SIZE = 8000;

    Area.Size maxSize;
    if (layoutParamsWidth == WRAP_CONTENT
        || layoutParamsHeight == WRAP_CONTENT)
      maxSize = getPreferredSize(component);
    else
      maxSize = new Area.Size(0, 0);

    // max width
    if (layoutParamsWidth == MATCH_PARENT)
      maxSize.setWidth(LARGE_SIZE);
    else if (layoutParamsWidth != WRAP_CONTENT)
      maxSize.setWidth(layoutParamsWidth);

    // max height
    if (layoutParamsHeight == MATCH_PARENT)
      maxSize.setHeight(LARGE_SIZE);
    else if (layoutParamsHeight != WRAP_CONTENT)
      maxSize.setHeight(layoutParamsHeight);

    return maxSize;
  }
}
