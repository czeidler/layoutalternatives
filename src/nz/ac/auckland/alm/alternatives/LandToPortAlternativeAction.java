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

import nz.ac.auckland.alm.algebra.trafo.*;

import java.util.List;


public class LandToPortAlternativeAction extends OrientationTrafoAction {
  @Override
  protected IPermutationSelector<Classification> getSelector(FragmentAlternatives fragmentAlternatives) {
    SwapTrafo swapTrafo = new SwapTrafo();
    InverseColumnTrafo inverseColumnTrafo = new InverseColumnTrafo();
    RowFlowTrafo rowFlowTrafo = new RowFlowTrafo();
    fragmentAlternatives.addTrafo(swapTrafo);
    fragmentAlternatives.addTrafo(inverseColumnTrafo);
    fragmentAlternatives.addTrafo(rowFlowTrafo);

    List<ITransformation> trafos = fragmentAlternatives.getTrafos();

    IPermutationSelector<Classification> selector
      = new ChainPermutationSelector<Classification>(
      new ApplyToAllPermutationSelector<Classification>(trafos, swapTrafo),
      new ApplyToAllPermutationSelector<Classification>(trafos, inverseColumnTrafo),
      new ApplyToAllPermutationSelector<Classification>(trafos, rowFlowTrafo),
      new RandomPermutationSelector<Classification>(trafos)
    );

    return selector;
  }

  @Override
  protected String getOutputDir() {
    return "layout-port";
  }
}
