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
package nz.ac.auckland.alm.alternatives.gui;

import nz.ac.auckland.alm.algebra.trafo.IAlternativeClassifier;
import nz.ac.auckland.alm.alternatives.AlternativeInfo;
import nz.ac.auckland.alm.misc.WeakListenable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class AlternativeController extends WeakListenable<AlternativeController.IListener> {
  public interface IListener {
    void onAlternativesChanged();
    void onAlternativeSelected(int i);
  }

  final private List<AlternativeInfo> alternatives;
  final private IAlternativeClassifier classifier;
  private int selectedAlternative = -1;

  public AlternativeController(List<AlternativeInfo> alternatives, IAlternativeClassifier classifier) {
    this.alternatives = alternatives;
    this.classifier = classifier;
  }

  public void sortByObjectiveValue() {
    Collections.sort(alternatives, new Comparator<AlternativeInfo>() {
      @Override
      public int compare(AlternativeInfo a0, AlternativeInfo a1) {
        Double objectiveValue0 = classifier.objectiveValue(a0.getResult().classification);
        Double objectiveValue1 = classifier.objectiveValue(a1.getResult().classification);
        return objectiveValue0.compareTo(objectiveValue1);
      }
    });
    notifyAlternativesChanged();
  }

  public void selectAlternative(int i) {
    if (i == this.selectedAlternative)
      return;
    this.selectedAlternative = i;
    notifyAlternativeSelected(i);
  }

  public int getSelectedAlternative() {
    return selectedAlternative;
  }

  public List<AlternativeInfo> getAlternatives() {
    return alternatives;
  }

  private void notifyAlternativesChanged() {
    for (IListener listener : getListeners())
      listener.onAlternativesChanged();
  }

  private void notifyAlternativeSelected(int i) {
    for (IListener listener : getListeners())
      listener.onAlternativeSelected(i);
  }
}
