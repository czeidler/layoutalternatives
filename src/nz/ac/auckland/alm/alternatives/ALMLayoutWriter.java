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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import nz.ac.auckland.alm.*;
import nz.ac.auckland.alm.algebra.*;
import nz.ac.auckland.linsolve.Variable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.android.SdkConstants.*;


class TagId {
  private List<String> myAddedIds = new ArrayList<String>();

  /** Returns the ID of this component */
  @Nullable
  static public String getId(@NonNull XmlTag tag) {
    return getId(tag, ANDROID_NS_NAME_PREFIX + ATTR_ID);
  }

  @Nullable
  static public String getId(@NonNull XmlTag tag, String attributeName) {
    String id = tag.getAttributeValue(attributeName);
    if (id != null) {
      if (id.startsWith(NEW_ID_PREFIX)) {
        return id.substring(NEW_ID_PREFIX.length());
      } else if (id.startsWith(ID_PREFIX)) {
        return id.substring(ID_PREFIX.length());
      }
    }
    return "";
  }


  /** Returns the ID, but also assigns a default id if the component does not already have an id (even if the component does
   * not need one according to {@link #needsDefaultId()} */
  public String ensureId(@NonNull XmlTag tag, @NonNull AndroidFacet facet) {
    String id = getId(tag);
    if (!id.equals("")) {
      return id;
    }

    return assignId(facet, tag, getIds(facet));
  }

  /** Looks up the existing set of id's reachable from the given module */
  private static Collection<String> getIds(@NonNull AndroidFacet facet) {
    AppResourceRepository resources = AppResourceRepository.getAppResources(facet, true);
    return resources.getItemsOfType(ResourceType.ID);
  }

  public String assignId(@NonNull AndroidFacet facet, @NonNull XmlTag tag, @NonNull Collection<String> idList) {
    String idValue = StringUtil.decapitalize(tag.getName());

    Module module = facet.getModule();
    Project project = module.getProject();
    idValue = ResourceHelper.prependResourcePrefix(module, idValue);

    String nextIdValue = idValue;
    int index = 0;

    // Ensure that we don't create something like "switch" as an id, which won't compile when used
    // in the R class
    NamesValidator validator = LanguageNamesValidation.INSTANCE.forLanguage(JavaLanguage.INSTANCE);

    while (idList.contains(nextIdValue) || myAddedIds.contains(nextIdValue) || validator != null
                                                                               && validator.isKeyword(nextIdValue, project)) {
      ++index;
      if (index == 1 && (validator == null || !validator.isKeyword(nextIdValue, project))) {
        nextIdValue = idValue;
      } else {
        nextIdValue = idValue + Integer.toString(index);
      }
    }

    String newId = idValue + (index == 0 ? "" : Integer.toString(index));
    //tag.setAttribute(ATTR_ID, ANDROID_URI, NEW_ID_PREFIX + newId);
    tag.setAttribute(ANDROID_NS_NAME_PREFIX + ATTR_ID, NEW_ID_PREFIX + newId);
    myAddedIds.add(newId);
    return newId;
  }

}


class ALMLayoutWriter {
  // ALMLayout layout params:
  public static final String ATTR_LAYOUT_ALIGN_LEFT = "layout_alignLeft";
  public static final String ATTR_LAYOUT_ALIGN_RIGHT = "layout_alignRight";
  public static final String ATTR_LAYOUT_ALIGN_TOP = "layout_alignTop";
  public static final String ATTR_LAYOUT_ALIGN_BOTTOM = "layout_alignBottom";
  public static final String ATTR_LAYOUT_TO_RIGHT_OF = "layout_toRightOf";
  public static final String ATTR_LAYOUT_TO_LEFT_OF = "layout_toLeftOf";
  public static final String ATTR_LAYOUT_BELOW = "layout_below";
  public static final String ATTR_LAYOUT_ABOVE = "layout_above";
  public static final String ATTR_LAYOUT_LEFT_TAB = "layout_leftTab";
  public static final String ATTR_LAYOUT_TOP_TAB = "layout_topTab";
  public static final String ATTR_LAYOUT_RIGHT_TAB = "layout_rightTab";
  public static final String ATTR_LAYOUT_BOTTOM_TAB = "layout_bottomTab";

  public static final String ALE_URI = AUTO_URI;

  interface ITagDirection<Tab extends Variable, OrthTab extends Variable> extends IDirection<Tab, OrthTab> {
    String getTabTag();
    String getOppositeTabTag();
    String getConnectionTag();
    String getOppositeConnectionTag();
    String getAlignTag();
    String getOppositeAlignTag();
  }

  static class LeftTagDirection extends LeftDirection implements ITagDirection<XTab, YTab> {
    @Override
    public String getTabTag() {
      return ATTR_LAYOUT_LEFT_TAB;
    }

    @Override
    public String getOppositeTabTag() {
      return ATTR_LAYOUT_RIGHT_TAB;
    }

    @Override
    public String getConnectionTag() {
      return ATTR_LAYOUT_TO_RIGHT_OF;
    }

    @Override
    public String getOppositeConnectionTag() {
      return ATTR_LAYOUT_TO_LEFT_OF;
    }

    @Override
    public String getAlignTag() {
      return ATTR_LAYOUT_ALIGN_LEFT;
    }

    @Override
    public String getOppositeAlignTag() {
      return ATTR_LAYOUT_ALIGN_RIGHT;
    }
  }

  static class RightTagDirection extends RightDirection implements ITagDirection<XTab, YTab> {
    @Override
    public String getTabTag() {
      return ATTR_LAYOUT_RIGHT_TAB;
    }

    @Override
    public String getOppositeTabTag() {
      return ATTR_LAYOUT_LEFT_TAB;
    }

    @Override
    public String getConnectionTag() {
      return ATTR_LAYOUT_TO_LEFT_OF;
    }

    @Override
    public String getOppositeConnectionTag() {
      return ATTR_LAYOUT_TO_RIGHT_OF;
    }

    @Override
    public String getAlignTag() {
      return ATTR_LAYOUT_ALIGN_RIGHT;
    }

    @Override
    public String getOppositeAlignTag() {
      return ATTR_LAYOUT_ALIGN_LEFT;
    }
  }

  static class TopTagDirection extends TopDirection implements ITagDirection<YTab, XTab> {
    @Override
    public String getTabTag() {
      return ATTR_LAYOUT_TOP_TAB;
    }

    @Override
    public String getOppositeTabTag() {
      return ATTR_LAYOUT_BOTTOM_TAB;
    }

    @Override
    public String getConnectionTag() {
      return ATTR_LAYOUT_BELOW;
    }

    @Override
    public String getOppositeConnectionTag() {
      return ATTR_LAYOUT_ABOVE;
    }

    @Override
    public String getAlignTag() {
      return ATTR_LAYOUT_ALIGN_TOP;
    }

    @Override
    public String getOppositeAlignTag() {
      return ATTR_LAYOUT_ALIGN_BOTTOM;
    }
  }

  static class BottomTagDirection extends BottomDirection implements ITagDirection<YTab, XTab> {
    @Override
    public String getTabTag() {
      return ATTR_LAYOUT_BOTTOM_TAB;
    }

    @Override
    public String getOppositeTabTag() {
      return ATTR_LAYOUT_TOP_TAB;
    }

    @Override
    public String getConnectionTag() {
      return ATTR_LAYOUT_ABOVE;
    }

    @Override
    public String getOppositeConnectionTag() {
      return ATTR_LAYOUT_BELOW;
    }

    @Override
    public String getAlignTag() {
      return ATTR_LAYOUT_ALIGN_BOTTOM;
    }

    @Override
    public String getOppositeAlignTag() {
      return ATTR_LAYOUT_ALIGN_TOP;
    }
  }

  final private XmlTag sourceRoot;
  final private LayoutSpec myLayoutSpec;
  final private AndroidFacet myFacet;
  final private TagId myTagId = new TagId();

  public ALMLayoutWriter(XmlTag sourceRoot, LayoutSpec layoutSpec, AndroidFacet facet) {
    this.sourceRoot = sourceRoot;
    this.myLayoutSpec = layoutSpec;
    this.myFacet = facet;
  }

  private static XmlAttribute getAttribute(XmlTag tag, String attributeName) {
    return tag.getAttribute("ale:" + attributeName);
  }

  private static void setAttribute(XmlTag tag, String attributeName, String value) {
    tag.setAttribute("ale:" + attributeName, value);
  }

  private static void clearAttribute(XmlTag tag, String attributeName) {
    XmlAttribute attribute = getAttribute(tag, attributeName);
    if (attribute != null)
      attribute.delete();
  }

  @NotNull
  static private String getAttrValue(XmlTag tag, String attribute) {
    XmlAttribute attr = getAttribute(tag, attribute);
    if (attr == null)
      return "";
    String value = attr.getValue();
    if (value == null)
      return "";
    return value;
  }

  static private boolean isArea(IArea area) {
    return area instanceof Area;
  }

  class AreaFilter implements Iterable<Area> {
    final List<IArea> myList;
    final Area myVeto;

    public AreaFilter(List<IArea> list) {
      this(list, null);
    }

    public AreaFilter(List<IArea> list, Area veto) {
      this.myList = list;
      this.myVeto = veto;
    }

    @Override
    public Iterator<Area> iterator() {
      return new Iterator<Area>() {
        int myPosition = 0;

        @Override
        public boolean hasNext() {
          for (; myPosition < myList.size(); myPosition++) {
            IArea area = myList.get(myPosition);
            if (area == myVeto)
              continue;
            if (isArea(area)) {
              return true;
            }
          }
          return false;
        }

        @Override
        public Area next() {
          Area area = (Area)myList.get(myPosition);
          myPosition++;
          return area;
        }

        @Override
        public void remove() {

        }
      };
    }
  }

  private XmlTag getTagFor(IArea area) {
    return (XmlTag)area.getCookie();
  }

  private Area getLastArea() {
    List<IArea> areas = myLayoutSpec.getAreas();
    for (int i = areas.size() - 1; i >=0; i--) {
      IArea area = areas.get(i);
      if (area instanceof Area)
        return (Area)area;
    }
    return null;
  }

  private <Tab extends Variable, OrthTab extends Variable>
  void writeSpecs(XmlTag tag, Area area, Map<Tab, Edge> map, List<String> tabNames, ITagDirection direction,
                  List<Area> handledAreas) {
    // Important: when new component are just added to the layout and the xml file is first read the new components don't have a id yet.
    // Thus, don't add references to items without an id! Also see pickArea.

    // Layout border: there are no border tags.
    if (direction.getTab(area) == direction.getTab(myLayoutSpec)) {
      clearAttribute(tag, direction.getTabTag());
      clearAttribute(tag, direction.getConnectionTag());
      clearAttribute(tag, direction.getAlignTag());
      return;
    }

    Edge edge = direction.getEdge(area, map);
    assert edge != null;

    // tab tags
    XmlAttribute tabTag = getAttribute(tag, direction.getTabTag());
    if (tabTag != null) {
      String tabName = tabTag.getValue();
      Iterable<Area> areas = new AreaFilter(direction.getAreas(edge));
      Iterable<Area> opAreas = new AreaFilter(direction.getOppositeAreas(edge), area);
      boolean tabFound = false;
      for (Area neighbour : areas) {
        if (getAttrValue(getTagFor(neighbour), direction.getOppositeTabTag()).equals(tabName)) {
          tabFound = true;
          break;
        }
      }
      if (!tabFound) {
        for (Area opNeighbour : opAreas) {
          if (getAttrValue(getTagFor(opNeighbour), direction.getTabTag()).equals(tabName)) {
            tabFound = true;
            break;
          }
        }
      }
      if (tabFound) {
        if (!tabNames.contains(tabName))
          tabNames.add(tabName);
        setAttribute(tag, direction.getTabTag(), tabName);
        clearAttribute(tag, direction.getConnectionTag());
        clearAttribute(tag, direction.getAlignTag());
        return;
      }
      clearAttribute(tag, direction.getTabTag());
    }

    // don't refer to the latest area
    Area lastArea = getLastArea();
    // Add either a connect, an align tag or a tab:
    Area connectToArea = null;
    String connectAttribute = null;
    String checkForDuplicatesAttribute = null;
    List<IArea> checkForDuplicatesAreas = null;
    Iterator<Area> neighbours = new AreaFilter(direction.getAreas(edge)).iterator();
    Iterator<Area> oppositeNeighbours = new AreaFilter(direction.getOppositeAreas(edge), area).iterator();
    if (neighbours.hasNext()) {
      // connect to
      connectToArea = pickArea(neighbours, lastArea);
      connectAttribute = direction.getConnectionTag();
      checkForDuplicatesAttribute = direction.getOppositeConnectionTag();
      checkForDuplicatesAreas = direction.getAreas(edge);
      clearAttribute(tag, direction.getTabTag());
      clearAttribute(tag, direction.getAlignTag());
    }
    if (connectToArea == null && oppositeNeighbours.hasNext()) {
      // align with
      connectToArea = pickArea(oppositeNeighbours, lastArea);
      connectAttribute = direction.getAlignTag();
      checkForDuplicatesAttribute = direction.getOppositeAlignTag();
      checkForDuplicatesAreas = direction.getOppositeAreas(edge);
      clearAttribute(tag, direction.getTabTag());
      clearAttribute(tag, direction.getConnectionTag());
    }
    if (connectToArea == null) {
      // reset iterators
      neighbours = new AreaFilter(direction.getAreas(edge)).iterator();
      oppositeNeighbours = new AreaFilter(direction.getOppositeAreas(edge), area).iterator();
      if (!neighbours.hasNext() && !oppositeNeighbours.hasNext()){
        // add tab
        String uniqueTabName = getUniqueTabName(tabNames, direction.getTab(area));
        tabNames.add(uniqueTabName);
        setAttribute(tag, direction.getTabTag(), uniqueTabName);
        clearAttribute(tag, direction.getConnectionTag());
        clearAttribute(tag, direction.getAlignTag());
      } else {
        // There might be no valid connectToArea. Such an area only gets incoming connections and is handled later.
        clearAttribute(tag, direction.getTabTag());
        clearAttribute(tag, direction.getConnectionTag());
        clearAttribute(tag, direction.getAlignTag());
      }
      return;
    }
    assert connectAttribute != null;
    assert checkForDuplicatesAttribute != null;
    assert checkForDuplicatesAreas != null;

    // Check for an existing valid connection and clear the attribute if there is one. This avoids redundant attributes.
    for (IArea neighbour : checkForDuplicatesAreas) {
      if (!isArea(neighbour))
        continue;
      if (!handledAreas.contains(neighbour))
        continue;
      String neighbourId = TagId.getId(getTagFor(neighbour), "ale:" + checkForDuplicatesAttribute);
      if (neighbourId.equals(TagId.getId(tag))) {
        clearAttribute(tag, connectAttribute);
        return;
      }
    }

    setAttribute(tag, connectAttribute, ID_PREFIX + myTagId.ensureId(getTagFor(connectToArea), myFacet));
  }

  static private String getUniqueTabName(List<String> tabNames, Variable tab) {
    String directionName;
    if (tab instanceof XTab)
      directionName = "x";
    else
      directionName = "y";
    for (int i = 0; ; i++) {
      String tabName = directionName + i;
      boolean containsName = false;
      for (String string : tabNames) {
        if (string.equals(tabName)) {
          containsName = true;
          break;
        }
      }
      if (!containsName)
        return tabName;
    }
  }

  private void updateConstraints() {
    AlgebraData algebraData = new AlgebraData(myLayoutSpec, null);
    Map<XTab, Edge> xTabEdgeMap = algebraData.getXTabEdges();
    Map<YTab, Edge> yTabEdgeMap = algebraData.getYTabEdges();
    final List<String> xTabNames = new ArrayList<String>();
    final List<String> yTabNames = new ArrayList<String>();

    List<Area> handledAreas = new ArrayList<Area>();
    // We have to process the children in the correct order so don't iterate over the map directly! see writeSpecs for more info
    for (Area area : algebraData.getAreas()) {
      writeSpecs(getTagFor(area), area, xTabEdgeMap, xTabNames, new LeftTagDirection(), handledAreas);
      writeSpecs(getTagFor(area), area, yTabEdgeMap, yTabNames, new TopTagDirection(), handledAreas);
      writeSpecs(getTagFor(area), area, xTabEdgeMap, xTabNames, new RightTagDirection(), handledAreas);
      writeSpecs(getTagFor(area), area, yTabEdgeMap, yTabNames, new BottomTagDirection(), handledAreas);
      handledAreas.add(area);
    }
  }

  static public void write(XmlTag sourceRoot, Fragment fragment, XmlFile outFile, Project project, AndroidFacet facet) {
    LayoutSpec layoutSpec = FragmentUtils.toLayoutSpec(fragment);
    LayoutSpec clone = layoutSpec.clone();
    layoutSpec.release();
    ALMLayoutWriter writer = new ALMLayoutWriter(sourceRoot, clone, facet);
    writer.write(outFile, project);
    clone.release();
  }

  static private void copyAttributes(XmlTag source, XmlTag target) {
    for (XmlAttribute attribute : source.getAttributes())
      target.setAttribute(attribute.getName(), attribute.getDisplayValue());
  }

  static private XmlTag copy(XmlTag tag, XmlElementFactory factory) {
    XmlTag copy = factory.createTagFromText("<" + tag.getName() + "/>");
    copyAttributes(tag, copy);
    for (PsiElement child : tag.getChildren()) {
      if (!(child instanceof XmlTag))
        continue;
      copy.add(copy((XmlTag)child, factory));
    }
    return copy;
  }

  public void write(XmlFile outFile, Project project) {
    XmlElementFactory factory = XmlElementFactory.getInstance(project);
    XmlDocument xmlDocument = outFile.getDocument();
    if (xmlDocument.getRootTag() != null)
      xmlDocument.getRootTag().delete();
    XmlTag rootTag = factory.createTagFromText("<nz.ac.auckland.alm.android.ALMLayout/>");
    rootTag.setAttribute("xmlns:android", "http://schemas.android.com/apk/res/android");
    rootTag.setAttribute("xmlns:ale", "http://schemas.android.com/apk/res-auto");
    rootTag.setAttribute("android:layout_width", "match_parent");
    rootTag.setAttribute("android:layout_height", "match_parent");
    copyAttributes(sourceRoot, rootTag);

    // copy tags
    for (IArea area : myLayoutSpec.getAreas()) {
      if (!(area instanceof Area))
        continue;
      XmlTag tagCopy = copy(((NlComponent)area.getCookie()).getTag(), factory);
      area.setCookie(tagCopy);
    }

    updateConstraints();

    // add tags to root
    for (IArea area : myLayoutSpec.getAreas()) {
      if (!(area instanceof Area))
        continue;
      rootTag.add((XmlTag)area.getCookie());
    }
    xmlDocument.add(rootTag);
  }

  static private Area pickArea(Iterator<Area> areas, Area veto) {
    while (areas.hasNext()) {
      Area area = areas.next();
      if (area == veto)
        continue;
      return area;
    }
    return null;
  }
}
