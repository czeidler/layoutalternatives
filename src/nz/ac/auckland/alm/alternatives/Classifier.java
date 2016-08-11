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
import nz.ac.auckland.alm.LayoutSpec;
import nz.ac.auckland.alm.algebra.Fragment;
import nz.ac.auckland.alm.algebra.FragmentUtils;
import nz.ac.auckland.alm.algebra.trafo.FragmentRef;
import nz.ac.auckland.alm.algebra.trafo.IAlternativeClassifier;
import nz.ac.auckland.alm.algebra.trafo.SymmetryAnalyzer;
import nz.ac.auckland.alm.algebra.trafo.TrafoHistory;

import java.util.ArrayList;
import java.util.List;


public class Classifier implements IAlternativeClassifier<Classification> {
  private float targetWidth;
  private float targetHeight;

  final private List<ObjectiveTerm> objectiveTerms = new ArrayList<ObjectiveTerm>();
  // manual values: 0.1, 5, 0.2, 0.02, 0.5, 0.1
  // auto values: 0.1660961969756879, 0.6032269451239768, 0.011315961345100378, 0.022983098304970133, 0.14937804418813272, 0.04699981222005293
  public Classifier() {
    objectiveTerms.add(new ObjectiveTerm("Min Size", 0.1660961969756879f) {
      @Override
      public double value(Classification classification) {
        return getMinSizeTerm(classification);
      }
    });
    objectiveTerms.add(new ObjectiveTerm("Pref Size", 0.6032269451239768f) {
      @Override
      public double value(Classification classification) {
        return getPrefSizeDiffTerm(classification);
      }
    });
    objectiveTerms.add(new ObjectiveTerm("Pref Ratio", 0.011315961345100378f) {
      @Override
      public double value(Classification classification) {
        return getRatioTerm(classification);
      }
    });
    objectiveTerms.add(new ObjectiveTerm("N Trafos", 0.022983098304970133f) {
      @Override
      public double value(Classification classification) {
        return getNTrafoTerm(classification);
      }
    });
    objectiveTerms.add(new ObjectiveTerm("Sym", 0.14937804418813272f) {
      @Override
      public double value(Classification classification) {
        return classification.symmetryTerm;
      }
    });
    objectiveTerms.add(new ObjectiveTerm("Level", 0.04699981222005293f) {
      @Override
      public double value(Classification classification) {
        return getLevelTerm(classification);
      }
    });
  }

  public List<ObjectiveTerm> getObjectiveTerms() {
    return objectiveTerms;
  }

  @Override
  public Classification classify(Fragment fragment, TrafoHistory history) {
    Classification classification = new Classification();
    classification.trafoHistory = history;

    LayoutSpec layoutSpec = FragmentUtils.toLayoutSpec(fragment);

    if (targetWidth == 0f) {
      NlComponent component = (NlComponent)layoutSpec.getAreas().get(0).getCookie();
      NlComponent root = component.getRoot();
      targetWidth = root.h;
      targetHeight = root.w;
    }

    classification.minSize = layoutSpec.getMinSize();
    if (isInvalid(classification)) {
      layoutSpec.release();
      return classification;
    }
    classification.prefSize = layoutSpec.getPreferredSize();

    if (layoutSpec.getAreas().size() > 0) {
      if (layoutSpec.getAreas().get(0).getCookie() != null) {
        layoutSpec.setRight(targetWidth);
        layoutSpec.setBottom(targetHeight);
        layoutSpec.solve();
      }
    }
    layoutSpec.release();

    List<Area> areas = FragmentUtils.getAreas(fragment);
    for (Area area : areas) {
      double width = area.getRight().getValue() - area.getLeft().getValue();
      double height = area.getBottom().getValue() - area.getTop().getValue();
      Area.Size areaPrefSize = area.getPreferredSize();
      // pref size == 0 means unset
      if (areaPrefSize.getWidth() > 0)
        classification.childrenPrefDiff2Width += Math.pow(width - areaPrefSize.getWidth(), 2);
      if (areaPrefSize.getHeight() > 0)
        classification.childrenPrefDiff2Height += Math.pow(height - areaPrefSize.getHeight(), 2);
    }
    classification.childrenPrefDiff2Width /= areas.size();
    classification.childrenPrefDiff2Height /= areas.size();

    classification.symmetryTerm = 1.f - SymmetryAnalyzer.symmetryClassifier(fragment);
    return classification;
  }

  private boolean isInvalid(Classification classification) {
    if (classification.minSize.getWidth() > targetWidth || classification.minSize.getHeight() > targetHeight)
      return true;
    return false;
  }

  @Override
  public double objectiveValue(Classification classification) {
    if (isInvalid(classification))
      return IAlternativeClassifier.INVALID_OBJECTIVE;

    double value = 0;
    for (ObjectiveTerm term : objectiveTerms)
      value += term.getWeight() * term.value(classification);
    return value;
  }

  public double getPrefSizeDiffTerm(Classification classification) {
    return (classification.childrenPrefDiff2Width + classification.childrenPrefDiff2Height)
           / (Math.pow(targetWidth, 2) + Math.pow(targetHeight, 2));
  }

  public double getMinSizeTerm(Classification classification) {
    return (Math.pow(classification.minSize.getWidth(), 2)
            + Math.pow(classification.minSize.getHeight(), 2))
           / (Math.pow(targetWidth, 2) + Math.pow(targetHeight, 2));
  }

  public double getRatioTerm(Classification classification) {
    double ratio = classification.prefSize.getWidth() / classification.prefSize.getHeight();
    double targetRatio = targetWidth / targetHeight;
    // assume a height of 1 and compare the resulting width, i.e. the ratios
    double ratioValue = Math.abs(ratio  - targetRatio) / targetRatio;
    if (ratioValue > 1d)
      return 1d;
    return ratioValue;
  }

  public double getNTrafoTerm(Classification classification) {
    int nTrafos = classification.trafoHistory.getNTrafos();
    if (nTrafos > 5)
      return 5;
    return nTrafos;
  }

  public double getSymmetryTerm(Classification classification) {
    return classification.symmetryTerm;
  }

  public double getLevelTerm(Classification classification) {
    List<TrafoHistory.Entry> entries = classification.trafoHistory.getEntries();
    if (entries.size() == 0)
      return 0d;
    TrafoHistory.Entry lastEntry = entries.get(entries.size() - 1);
    double level = 0;
    for (FragmentRef ref : lastEntry.fragmentRefs) {
      if (ref == null)
        continue;
      level = ref.getNLevels();
      break;
    }
    if (level > 5)
      return 5;
    return level;
  }
}
