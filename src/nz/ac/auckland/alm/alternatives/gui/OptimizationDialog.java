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

import nz.ac.auckland.alm.algebra.trafo.Classification;
import nz.ac.auckland.alm.algebra.trafo.Classifier;
import nz.ac.auckland.alm.algebra.trafo.ObjectiveTerm;
import nz.ac.auckland.alm.alternatives.AlternativeInfo;
import nz.ac.auckland.linsolve.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;


public class OptimizationDialog {
  static private String doOptimize(AlternativeController controller, Classifier classifier, int index) {
    LinearSpec linearSpec = new LinearSpec();

    List<ObjectiveTerm> terms = classifier.getObjectiveTerms();
    List<Variable> variables = new ArrayList<Variable>();
    for (ObjectiveTerm term : terms) {
      Variable variable = new Variable(term.getName());
      variables.add(variable);
      linearSpec.addConstraint(new Constraint(1, variable, OperatorType.GE, 0));
    }

    // add target
    /*Summand[] summands = new Summand[variables.size()];
    for (int i = 0; i < summands.length; i++) {
      Variable variable = variables.get(i);
      summands[i] = new Summand(1, variable);
    }
    Constraint constraint = new Constraint(summands, OperatorType.EQ, 10);
    linearSpec.addConstraint(constraint);
*/
    List<AlternativeInfo> alternativeInfoList = controller.getAlternatives();
    AlternativeInfo toOptimize = alternativeInfoList.get(index);
    for (int i = 0; i < alternativeInfoList.size(); i++) {
      if (i == index)
        continue;
      AlternativeInfo current = alternativeInfoList.get(i);

      Summand[] summands = new Summand[terms.size()];
      for (int a = 0; a < terms.size(); a++) {
        ObjectiveTerm term = terms.get(a);
        summands[a] = new Summand(term.value((Classification)current.getResult().classification)
                                  - term.value((Classification)toOptimize.getResult().classification), variables.get(a));
      }
      Constraint constraint = new Constraint(summands, OperatorType.EQ, 10, 0.5);
      linearSpec.addConstraint(constraint);
    }
    linearSpec.solve();

    String result = "";
    float sum = 0;
    for (Variable variable : variables) {
      result += variable + ", ";
      sum += variable.getValue();
    }
    result += "\n";
    for (Variable variable : variables)
      result += variable.getValue() / sum + ", ";

    return result;
  }

  static public JDialog optimize(AlternativeController controller, Classifier classifier, int index) {
    JDialog dialog = new JDialog();

    final JTextField resultText = new JTextField();
    resultText.setEditable(false);
    resultText.setText("Result: " + doOptimize(controller, classifier, index));
    dialog.add(resultText);

    dialog.pack();
    dialog.setLocationRelativeTo(null);
    dialog.setVisible(true);
    return dialog;
  }
}
