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

import com.intellij.psi.xml.XmlFile;
import nz.ac.auckland.alm.algebra.Fragment;
import nz.ac.auckland.alm.alternatives.AlternativeInfo;
import nz.ac.auckland.alm.alternatives.Classifier;
import nz.ac.auckland.alm.alternatives.LayoutRenderer;

import javax.swing.*;
import java.awt.*;

/**
 * Created by lec on 25/03/16.
 */
public class AlternativeMain {
  static public void showAlternatives(XmlFile rootXmlFile, Fragment main, AlternativeController alternativeController,
                                      LayoutRenderer layoutRenderer, Classifier classifier) {
    JDialog dialog = new JDialog();
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    dialog.setTitle("Layout Alternatives");


    JSplitPane infoPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                                          AlternativeInfoPanel.create(main, alternativeController, classifier),
                                          layoutRenderer.createView(rootXmlFile, false));
    infoPanel.setDividerLocation(400);

    JPanel alternativeView = getAlternativeView(alternativeController, layoutRenderer);

    JSplitPane mainPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                         infoPanel, alternativeView);
    mainPane.setDividerLocation(700);

    dialog.add(mainPane);

    dialog.pack();
    dialog.setLocationRelativeTo(null);
    dialog.setVisible(true);
  }


  static private class AlternativeViewPanel extends JPanel {
    // we have to keep a hard ref
    final private AlternativeController.IListener alternativeViewListener;

    public AlternativeViewPanel(AlternativeController.IListener alternativeViewListener) {
      super();
      this.alternativeViewListener = alternativeViewListener;
    }
  }

  static private JPanel getAlternativeView(final AlternativeController alternativeController, final LayoutRenderer layoutRenderer) {
    final JTextField label = new JTextField();
    label.setMaximumSize(new Dimension(label.getMaximumSize().width, label.getPreferredSize().height));
    label.setEditable(false);
    final JPanel previewPanel = new JPanel(new BorderLayout());

    // we keep the hard ref in the main panel
    AlternativeController.IListener alternativeViewListener = new AlternativeController.IListener() {
      @Override
      public void onAlternativesChanged() {

      }

      @Override
      public void onAlternativeSelected(int i) {
        AlternativeInfo alternativeInfo = alternativeController.getAlternatives().get(i);
        if (previewPanel.getComponents().length > 0)
          previewPanel.remove(0);
        label.setText("Alternative: " + alternativeInfo.getFragment());
        previewPanel.add(layoutRenderer.getDesignSurface(alternativeInfo.getFragment()));
        previewPanel.revalidate();
        previewPanel.repaint();
        Container parent = previewPanel.getParent();
        if (parent != null) {
          parent.invalidate();
          parent.repaint();
        }
      }
    };
    alternativeController.addListener(alternativeViewListener);

    AlternativeViewPanel mainPanel = new AlternativeViewPanel(alternativeViewListener);
    mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
    mainPanel.add(label);
    mainPanel.add(previewPanel);
    return mainPanel;
  }
}
