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
import nz.ac.auckland.alm.Area;
import nz.ac.auckland.alm.IArea;
import nz.ac.auckland.alm.ILayoutSpecArea;
import nz.ac.auckland.alm.LayoutSpec;
import nz.ac.auckland.alm.algebra.Fragment;
import nz.ac.auckland.alm.algebra.IDirection;
import nz.ac.auckland.alm.algebra.trafo.FragmentAlternatives;
import nz.ac.auckland.linsolve.Variable;

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

    public FragmentAlternatives.Result getResult() {
        return result;
    }

    public Fragment getFragment() {
        return result.fragment;
    }

    public float getQuality() {
        return result.quality;
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

    private void setTabs(Fragment fragment) {
        IDirection direction = fragment.getDirection();
        if (fragment.size() == 1 && fragment.getItemAt(0) instanceof Fragment) {
            setTabs((Fragment)fragment.getItemAt(0));
            return;
        }
        for (int i = 0; i < fragment.size() - 1; i++) {
            IArea area1 = fragment.getItemAt(i);
            IArea area2 = fragment.getItemAt(i + 1);
            if (area1 instanceof Fragment)
                setTabs((Fragment)area1);
            if (area2 instanceof Fragment)
                setTabs((Fragment)area2);
            Variable tab = direction.createTab();
            direction.setTab(area1, tab);
            direction.setOppositeTab(area2, tab);
        }
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
        setTabs(fragment);

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
                layoutSpec.setRight(root.h);
                layoutSpec.setBottom(root.w);
                layoutSpec.solve();
            }
        }

        // calculate prefSizeDiff
        prefSizeDiff = 0;
        for (IArea area : areas) {
            if (!(area instanceof Area))
                continue;
            double width = area.getRight().getValue() - area.getLeft().getValue();
            double height = area.getBottom().getValue() - area.getTop().getValue();
            Area.Size areaPrefSize = ((Area)area).getPreferredSize();
            prefSizeDiff += Math.pow(width - areaPrefSize.getWidth(), 2) + Math.pow(height - areaPrefSize.getHeight(), 2);
        }
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
}

