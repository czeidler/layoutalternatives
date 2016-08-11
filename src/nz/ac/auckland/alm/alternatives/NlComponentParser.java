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
import nz.ac.auckland.alm.android.AbstractViewInfoParser;

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

  static class ViewInfoParser extends AbstractViewInfoParser<NlComponent> {
    static public final int AT_MOST = -2147483648;
    static public final int EXACTLY = 1073741824;
    static public final int UNSPECIFIED = 0;

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

    static private Area.Size measureSize(NlComponent component, int width, int height, int mode) {
      Object view = component.viewInfo.getViewObject();
      try {
        Method measure = view.getClass().getMethod("measure", int.class, int.class);
        Method getMeasuredWidth = view.getClass().getMethod("getMeasuredWidth");
        Method getMeasuredHeight = view.getClass().getMethod("getMeasuredHeight");
        measure.invoke(view, makeMeasureSpec(width, mode), makeMeasureSpec(height, mode));
        return new Area.Size((Integer)getMeasuredWidth.invoke(view), (Integer)getMeasuredHeight.invoke(view));
      } catch (Exception e) {
        return new Area.Size(MATCH_PARENT, MATCH_PARENT);
      }
    }

    @Override
    protected Area.Size getLayoutParams(NlComponent component) {
      Object layoutParams = component.viewInfo.getLayoutParamsObject();
      try {
        Field width = layoutParams.getClass().getField("width");
        Field height = layoutParams.getClass().getField("height");
        return new Area.Size((Integer)width.get(layoutParams), (Integer)height.get(layoutParams));
      } catch (Exception e) {
        return new Area.Size(WRAP_CONTENT, WRAP_CONTENT);
      }
    }

    @Override
    protected String getClassName(NlComponent component) {
      return component.getTagName();
    }

    @Override
    protected Area.Size getRootViewSize(NlComponent component) {
      NlComponent rootComponent = component.getRoot();
      return new Area.Size(rootComponent.w, rootComponent.h);
    }

    @Override
    protected Area.Size getMinSizeRaw(NlComponent component) {
      Object view = component.viewInfo.getViewObject();
      try {
        Method minWidth = view.getClass().getMethod("getMinimumWidth");
        Method minHeight = view.getClass().getMethod("getMinimumHeight");
        Object width = minWidth.invoke(view);
        Object height = minHeight.invoke(view);
        return new Area.Size((Integer)width, (Integer)height);
      }
      catch (Exception e) {
        e.printStackTrace();
        return new Area.Size(-1, -1);
      }
    }

    @Override
    protected Area.Size getPreferredSizeRaw(NlComponent component) {
      //Area.Size prefSize = measureSize(component, 0, 0, UNSPECIFIED);
      Area.Size prefSize = getMaxSizeRaw(component);
      return prefSize;
    }

    @Override
    protected Area.Size getMaxSizeRaw(NlComponent component) {
      Area.Size rootSize = getRootViewSize(component);
      return measureSize(component, (int)rootSize.getWidth(), (int)rootSize.getHeight(), AT_MOST);
    }
  }

  static private int parseDimension(XmlAttribute attribute) {
      final String PIXEL = "px";
      String value = attribute.getValue();
      if (!value.endsWith(PIXEL))
        return Area.Size.UNDEFINED;
      value = value.substring(0, value.length() - PIXEL.length());
      return Integer.parseInt(value);
  }

  static private Area.Size readExplicitMinSize(NlComponent component) {
    Area.Size explicitSize = new Area.Size(Area.Size.UNDEFINED, Area.Size.UNDEFINED);
    XmlAttribute width = component.getTag().getAttribute("ale:layout_minWidth");
    XmlAttribute height = component.getTag().getAttribute("ale:layout_minHeight");
    if (width != null)
      explicitSize.setWidth(parseDimension(width));
    if (height != null)
      explicitSize.setHeight(parseDimension(height));
    return explicitSize;
  }

  static private Area.Size readExplicitPrefSize(NlComponent component) {
    Area.Size explicitPrefSize = new Area.Size(Area.Size.UNDEFINED, Area.Size.UNDEFINED);
    XmlAttribute width = component.getTag().getAttribute("ale:layout_prefWidth");
    XmlAttribute height = component.getTag().getAttribute("ale:layout_prefHeight");
    if (width != null)
      explicitPrefSize.setWidth(parseDimension(width));
    if (height != null)
      explicitPrefSize.setHeight(parseDimension(height));
    return explicitPrefSize;
  }

  static private Area toArea(NlComponent component) {
    Area area = new Area();
    area.setCookie(component);
    ViewInfoParser parser = new ViewInfoParser();
    area.setMinSize(parser.getMinSize(component, readExplicitMinSize(component)));
    area.setPreferredSize(parser.getPreferredSize(component, readExplicitPrefSize(component)));
    area.setMaxSize(parser.getMaxSize(component));
    return area;
  }


}
