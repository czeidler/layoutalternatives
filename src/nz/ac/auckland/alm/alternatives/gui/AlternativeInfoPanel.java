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

import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import nz.ac.auckland.alm.algebra.Fragment;
import nz.ac.auckland.alm.algebra.trafo.Classification;
import nz.ac.auckland.alm.algebra.trafo.Classifier;
import nz.ac.auckland.alm.algebra.trafo.ObjectiveTerm;
import nz.ac.auckland.alm.alternatives.*;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class AlternativeInfoPanel {
  private interface IColumn {
    String columnName();
    Object getRow(AlternativeInfo info, int row);
  }

  static private void export(AlternativeController controller, Classifier classifier) throws IOException {
    FileWriter fileWriter = new FileWriter(controller.getFileName() + ".data");
    BufferedWriter writer = new BufferedWriter(fileWriter);

    List<AlternativeInfo> alternativeInfos = controller.getAlternatives();
    List<ObjectiveTerm> objectiveTerms = classifier.getObjectiveTerms();
    for (AlternativeInfo alternativeInfo : alternativeInfos) {
      for (int i = 0; i < objectiveTerms.size(); i++) {
        double value = objectiveTerms.get(i).value((Classification)alternativeInfo.getResult().classification);
        writer.write("" + value);
        if (i < objectiveTerms.size() - 1)
          writer.write(",");
      }
      writer.write("\n");
    }

    writer.close();
  }

  static public JPanel create(Fragment main, final AlternativeController alternativeController,
                              final Classifier classifier) {
    final List<AlternativeInfo> alternativeInfos = alternativeController.getAlternatives();

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(new Label("Original Layout: " + main.toString() + ", " + alternativeInfos.size() + "Alternatives:"));

    final List<IColumn> myColumns = new ArrayList<IColumn>();
    myColumns.add(new IColumn() {
      @Override
      public String columnName() {
        return "i";
      }

      @Override
      public Object getRow(AlternativeInfo info, int row) {
        return row;
      }
    });
    myColumns.add(new IColumn() {
      @Override
      public String columnName() {
        return "Fragment";
      }

      @Override
      public Object getRow(AlternativeInfo info, int row) {
        return info.getFragment();
      }
    });
    myColumns.add(new IColumn() {
      @Override
      public String columnName() {
        return "Objective Value";
      }

      @Override
      public Object getRow(AlternativeInfo info, int row) {
        return classifier.objectiveValue((Classification)info.getResult().classification);
      }
    });
    for (final ObjectiveTerm term : classifier.getObjectiveTerms()) {
      myColumns.add(new IColumn() {
        @Override
        public String columnName() {
          return term.getName() + "(" + term.getWeight() + ")";
        }

        @Override
        public Object getRow(AlternativeInfo info, int row) {
          return term.getWeight() * term.value((Classification)info.getResult().classification);
        }
      });
    }

    AbstractTableModel tableModel = new AbstractTableModel() {
      @Override
      public int getRowCount() {
        return alternativeInfos.size();
      }

      @Override
      public int getColumnCount() {
        return myColumns.size();
      }

      @Override
      public Object getValueAt(int row, int column) {
        AlternativeInfo info = alternativeInfos.get(row);
        return myColumns.get(column).getRow(info, row);
      }

      @Override
      public String getColumnName(int column) {
        return myColumns.get(column).columnName();
      }
    };

    final JTable table = new JBTable(tableModel);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent listSelectionEvent) {
        alternativeController.selectAlternative(table.getSelectedRow());

        AlternativeInfo info = alternativeInfos.get(table.getSelectedRow());
        double sum = 0;
        for (final ObjectiveTerm term : classifier.getObjectiveTerms()) {
          double value = term.value((Classification)info.getResult().classification);
          System.out.print(value + ", ");
          sum += value;
        }
        System.out.print(sum);
        System.out.println();
      }
    });

    // right click menu
    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent event) {
        if (!SwingUtilities.isRightMouseButton(event) || !(event.getComponent() instanceof JTable))
          return;

        final int row = table.rowAtPoint(event.getPoint());
        if (row < 0 || row >= table.getRowCount())
          return;

        JPopupMenu popup = new JPopupMenu();
        JMenuItem item = new JMenuItem("Optimize Row");
        popup.add(item);
        item.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent actionEvent) {
            OptimizationDialog.optimize(alternativeController, classifier, row);
          }
        });
        popup.show(event.getComponent(), event.getX(), event.getY());
      }
    });

    panel.add(new JBScrollPane(table));

    // export button
    JButton exportButton = new JButton("Export");
    exportButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        try {
          export(alternativeController, classifier);
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    });
    panel.add(exportButton);

    return panel;
  }
}
