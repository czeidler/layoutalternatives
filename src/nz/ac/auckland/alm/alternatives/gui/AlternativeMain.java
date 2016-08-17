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

import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.UIUtil;
import nz.ac.auckland.alm.algebra.Fragment;
import nz.ac.auckland.alm.algebra.trafo.Classifier;
import nz.ac.auckland.alm.alternatives.AlternativeInfo;
import nz.ac.auckland.alm.alternatives.LayoutRenderer;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;


public class AlternativeMain {
  static public void showAlternatives(Project project, XmlFile rootXmlFile, Fragment main, AlternativeController alternativeController,
                                      LayoutRenderer layoutRenderer, Classifier classifier) {
    JDialog dialog = new JDialog();
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    dialog.setTitle("Layout Alternatives");


    JSplitPane infoPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                                          AlternativeInfoPanel.create(main, alternativeController, classifier),
                                          layoutRenderer.createView(null, rootXmlFile, false));
    infoPanel.setDividerLocation(400);

    JPanel alternativeView = getAlternativeView(project, rootXmlFile, alternativeController, layoutRenderer);

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

  static private void print(final LayoutRenderer layoutRenderer, JPanel panel, String outName) {
    BufferedImage image = UIUtil.createImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    panel.printAll(g);
    g.dispose();
    try {
      ImageIO.write(image, "png", new File(outName));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  static private void print(final LayoutRenderer layoutRenderer, Fragment fragment, String outName) {
    DesignSurface panel = layoutRenderer.getDesignSurface(null, fragment, true, true);
    panel.setSize(1000, 600);
    panel.setVisible(true);
    panel.revalidate();
    panel.repaint();
    panel.setDoubleBuffered(false);
    print(layoutRenderer, panel, outName);
    panel.dispose();
  }

  static private void print(final LayoutRenderer layoutRenderer, List<AlternativeInfo> infoList) {
    print(layoutRenderer, infoList.get(0).getFragment(), "test.png");
  }

  static private void loadPreview(AlternativeInfo alternativeInfo, JPanel previewPanel, LayoutRenderer layoutRenderer,
                                  boolean refreshResources) {
    DesignSurface surface = null;
    if (previewPanel.getComponents().length > 0) {
      surface = (DesignSurface)previewPanel.getComponent(0);
      previewPanel.remove(0);
    }

    previewPanel.add(layoutRenderer.getDesignSurface(surface, alternativeInfo.getFragment(), false, refreshResources));
    previewPanel.revalidate();
    previewPanel.repaint();
    Container parent = previewPanel.getParent();
    if (parent != null) {
      parent.invalidate();
      parent.repaint();
    }
  }

  static private JPanel getAlternativeView(final Project project, final XmlFile rootXmlFile,
                                           final AlternativeController alternativeController, final LayoutRenderer layoutRenderer) {
    final JTextField label = new JTextField();
    label.setMaximumSize(new Dimension(label.getMaximumSize().width, label.getPreferredSize().height));
    label.setEditable(false);
    final JPanel previewPanel = new JPanel(new BorderLayout());

    final JButton printImagesButton = new JButton("Print");
    printImagesButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        SwingUtilities.invokeLater(new Runnable() {
                                     @Override
                                     public void run() {
                                       print(layoutRenderer, previewPanel, project.getName() + "_" + rootXmlFile.getName() + "_" +
                                                                           alternativeController.getSelectedAlternative() + ".png");
                                     }
                                   });
      }
    });
    final JButton refreshButton = new JButton("Refresh");
    refreshButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        layoutRenderer.getFacet().refreshResources();
        int selected = alternativeController.getSelectedAlternative();
        if (selected < 0)
          return;
        loadPreview(alternativeController.getAlternatives().get(selected), previewPanel, layoutRenderer, true);
      }
    });


    // we keep the hard ref in the main panel
    AlternativeController.IListener alternativeViewListener = new AlternativeController.IListener() {
      @Override
      public void onAlternativesChanged() {

      }

      @Override
      public void onAlternativeSelected(int i) {
        AlternativeInfo alternativeInfo = alternativeController.getAlternatives().get(i);
        loadPreview(alternativeInfo, previewPanel, layoutRenderer, false);
        label.setText("Alternative: " + alternativeInfo.getFragment());
      }
    };
    alternativeController.addListener(alternativeViewListener);

    AlternativeViewPanel mainPanel = new AlternativeViewPanel(alternativeViewListener);
    mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
    mainPanel.add(label);
    JPanel buttonBar = new JPanel();
    buttonBar.setLayout(new HorizontalLayout(10));
    buttonBar.add(printImagesButton);
    buttonBar.add(refreshButton);
    buttonBar.setMaximumSize(new Dimension(buttonBar.getMaximumSize().width, buttonBar.getPreferredSize().height));

    mainPanel.add(buttonBar);
    mainPanel.add(previewPanel);
    return mainPanel;
  }
}
