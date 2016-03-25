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

  public Classifier() {
    objectiveTerms.add(new ObjectiveTerm("Min Size", 0.2f) {
      @Override
      public double value(Classification classification) {
        return getMinSizeTerm(classification);
      }
    });
    objectiveTerms.add(new ObjectiveTerm("Pref Size", 5.f) {
      @Override
      public double value(Classification classification) {
        return getPrefSizeDiffTerm(classification);
      }
    });
    objectiveTerms.add(new ObjectiveTerm("Pref Ratio", 0.2f) {
      @Override
      public double value(Classification classification) {
        return getRatioTerm(classification);
      }
    });
    objectiveTerms.add(new ObjectiveTerm("N Trafos", 0.2f) {
      @Override
      public double value(Classification classification) {
        return getNTrafoTerm(classification);
      }
    });
    /*objectiveTerms.add(new ObjectiveTerm("Sym", 0.1f) {
      @Override
      public double value(Classification classification) {
        return getSymmetryTerm(classification);
      }
    });
    objectiveTerms.add(new ObjectiveTerm("Level Sym", 0.5f) {
      @Override
      public double value(Classification classification) {
        return getLevelSymmetryTerm(classification);
      }
    });
    */
    objectiveTerms.add(new ObjectiveTerm("Sym2", 0.7f) {
      @Override
      public double value(Classification classification) {
        return classification.symmetryTerm2;
      }
    });
    objectiveTerms.add(new ObjectiveTerm("Level", 0.2f) {
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

    int fragmentCount = SymmetryAnalyzer.countFragments(fragment);
    float symmetryCount = SymmetryAnalyzer.symmetryCountSameChildrenSize(fragment);
    classification.symmetryTerm = 1.f - symmetryCount / fragmentCount;

    int numberOfElementsInLevels = SymmetryAnalyzer.numberOfElementsInLevels(fragment);
    float levelSymmetryCount = SymmetryAnalyzer.levelSymmetry(fragment);
    classification.levelSymmetryTerm = 1f - levelSymmetryCount / numberOfElementsInLevels;

    classification.symmetryTerm2 = 1.f - SymmetryAnalyzer.symmetryClassifier(fragment);
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
    return (double)classification.trafoHistory.getNTrafos() / 5;
  }

  public double getSymmetryTerm(Classification classification) {
    return classification.symmetryTerm;
  }

  public double getLevelSymmetryTerm(Classification classification) {
    return classification.levelSymmetryTerm;
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
    return level / 5;
  }
}
