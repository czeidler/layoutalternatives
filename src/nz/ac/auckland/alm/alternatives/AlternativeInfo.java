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

import com.android.tools.idea.editors.hprof.jdi.DoubleValueImpl;
import com.android.tools.idea.uibuilder.editor.NlEditor;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.psi.xml.XmlFile;
import nz.ac.auckland.alm.Area;
import nz.ac.auckland.alm.IArea;
import nz.ac.auckland.alm.ILayoutSpecArea;
import nz.ac.auckland.alm.LayoutSpec;
import nz.ac.auckland.alm.algebra.Fragment;
import nz.ac.auckland.alm.algebra.trafo.FragmentAlternatives;

import java.util.ArrayList;
import java.util.List;

public class AlternativeInfo {
    private FragmentAlternatives.Result result;
    private Area.Size minSize;
    private Area.Size prefSize;
    private double prefSizeDiff;

    public AlternativeInfo(FragmentAlternatives.Result result) {
        this.result = result;

        getSizes(result.fragment);
    }

    public AlternativeInfo(FragmentAlternatives.Result result, LayoutRenderer renderer) {
        this.result = result;

        getSizes2(result.fragment, renderer);
    }

    public FragmentAlternatives.Result getResult() {
        return result;
    }

    public Fragment getFragment() {
        return result.fragment;
    }

    public float getQuality() {
        return result.trafoHistory.getTotalQuality();
    }

    public Area.Size getMinSize() {
        return minSize;
    }

    public Area.Size getPrefSize() {
        return prefSize;
    }

    public double getPrefSizeDiff() {
        return prefSizeDiff;
    }

    public double getPrefRatio() {
        return prefSize.getWidth() / prefSize.getHeight();
    }

    private void getAtoms(Fragment fragment, List<IArea> areas) {
        for (IArea area : (Iterable<IArea>)fragment.getItems()) {
            if (area instanceof Fragment)
                getAtoms((Fragment)area, areas);
            else if (!areas.contains(area))
                areas.add(area);
        }
    }

    private void getSizes(Fragment fragment) {
        fragment.applySpecsToChild();

        final float hSpacing = 10;
        final float vSpacing = 20;
        List<IArea> areas = new ArrayList<IArea>();
        getAtoms(fragment, areas);
        LayoutSpec layoutSpec = new LayoutSpec();
        layoutSpec.setHorizontalSpacing(hSpacing);
        layoutSpec.setVerticalSpacing(vSpacing);
        fragment.setLeft(layoutSpec.getLeft());
        fragment.setTop(layoutSpec.getTop());
        fragment.setRight(layoutSpec.getRight());
        fragment.setBottom(layoutSpec.getBottom());

        for (IArea area : areas) {
            if (area instanceof ILayoutSpecArea)
                layoutSpec.addArea((ILayoutSpecArea)area);
        }

        minSize = layoutSpec.getMinSize();
        prefSize = layoutSpec.getPreferredSize();

        if (areas.size() > 0) {
            if (areas.get(0).getCookie() != null) {
                NlComponent component = (NlComponent)areas.get(0).getCookie();
                NlComponent root = component.getRoot();
                // portrait h <-> w
                if (minSize.getWidth() >= root.h || minSize.getHeight() >= root.w) {
                    prefSizeDiff = Double.MAX_VALUE;
                    layoutSpec.release();
                    return;
                }
                layoutSpec.setRight(root.h);
                layoutSpec.setBottom(root.w);
                layoutSpec.solve();
            }
        }

        // calculate prefSizeDiff
        prefSizeDiff = 0;
        double emptySpace = layoutSpec.getRight().getValue() * layoutSpec.getBottom().getValue();
        for (IArea area : areas) {
            if (!(area instanceof Area))
                continue;
            double width = area.getRight().getValue() - area.getLeft().getValue();
            double height = area.getBottom().getValue() - area.getTop().getValue();
            Area.Size areaPrefSize = ((Area)area).getPreferredSize();
            prefSizeDiff += Math.pow(width * height - areaPrefSize.getWidth() * areaPrefSize.getHeight(), 2);
            emptySpace -= width * height;
        }
        prefSizeDiff += Math.pow(emptySpace, 2);
        prefSizeDiff = Math.sqrt(prefSizeDiff);

        // clean up
        layoutSpec.release();
        // todo remove?
        // reset variables
        for (IArea area : areas) {
            area.setLeft(null);
            area.setTop(null);
            area.setRight(null);
            area.setBottom(null);
        }
    }

    private double calculatePrefDiff(NlComponent component) {
        double result = 0;
        double emptySpace = component.w * component.h;
        for (NlComponent child : component.getChildren()) {
            if (child.getTag().getName().equals("LinearLayout")) {
                result += calculatePrefDiff(child);
                continue;
            }
            Area.Size prefSize = NlComponentParser.getPreferredSize(child);
            double diff = Math.pow(child.w * child.h - prefSize.getWidth() * prefSize.getHeight(), 2);
            result += diff;
            emptySpace -= child.w * child.h;
        }
        return result + Math.pow(emptySpace, 2);
    }

    private void getSizes2(Fragment fragment, LayoutRenderer renderer) {
        XmlFile file = renderer.createLandFile(fragment);

        DesignSurface surface = new DesignSurface(renderer.getProject());
        NlEditor nlEditor = new NlEditor(renderer.getFacet(), file.getVirtualFile(), renderer.getProject());
        NlModel model = NlModel.create(surface, nlEditor, renderer.getFacet(), file);
        surface.setModel(model);
        model.renderImmediately();

        minSize = new Area.Size();
        prefSize = new Area.Size();

        // calculate prefSizeDiff
        prefSizeDiff = Math.sqrt(calculatePrefDiff(model.getComponents().get(0)));
    }
}

