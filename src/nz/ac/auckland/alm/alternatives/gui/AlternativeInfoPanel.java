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
import nz.ac.auckland.alm.algebra.trafo.IAlternativeClassifier;
import nz.ac.auckland.alm.alternatives.AlternativeAction;
import nz.ac.auckland.alm.alternatives.AlternativeInfo;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class AlternativeInfoPanel {
  private interface IColumn {
    String columnName();
    Object getRow(AlternativeInfo info);
  }

  static public JPanel create(Fragment main, final AlternativeController alternativeController,
                              final AlternativeAction.Classifier classifier) {
    final List<AlternativeInfo> alternativeInfos = alternativeController.getAlternatives();

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(new Label("Original Layout: " + main.toString() + ", " + alternativeInfos.size() + "Alternatives:"));

    final List<IColumn> myColumns = new ArrayList<IColumn>();
    myColumns.add(new IColumn() {
      @Override
      public String columnName() {
        return "Fragment";
      }

      @Override
      public Object getRow(AlternativeInfo info) {
        return info.getFragment();
      }
    });
    myColumns.add(new IColumn() {
      @Override
      public String columnName() {
        return "Objective Value";
      }

      @Override
      public Object getRow(AlternativeInfo info) {
        return classifier.objectiveValue((AlternativeAction.Classification)info.getResult().classification);
      }
    });
    myColumns.add(new IColumn() {
      @Override
      public String columnName() {
        return "Obj Pref Size";
      }

      @Override
      public Object getRow(AlternativeInfo info) {
        return classifier.getPrefSizeDiffTerm((AlternativeAction.Classification)info.getResult().classification);
      }
    });
    myColumns.add(new IColumn() {
      @Override
      public String columnName() {
        return "Obj Ratio";
      }

      @Override
      public Object getRow(AlternativeInfo info) {
        return classifier.getRatioTerm((AlternativeAction.Classification)info.getResult().classification);
      }
    });
    myColumns.add(new IColumn() {
      @Override
      public String columnName() {
        return "Obj NTrafo";
      }

      @Override
      public Object getRow(AlternativeInfo info) {
        return classifier.getNTrafoTerm((AlternativeAction.Classification)info.getResult().classification);
      }
    });
    myColumns.add(new IColumn() {
      @Override
      public String columnName() {
        return "Obj Symmetry";
      }

      @Override
      public Object getRow(AlternativeInfo info) {
        return classifier.getSymmetryTerm((AlternativeAction.Classification)info.getResult().classification);
      }
    });
    myColumns.add(new IColumn() {
      @Override
      public String columnName() {
        return "Obj Level";
      }

      @Override
      public Object getRow(AlternativeInfo info) {
        return classifier.getLevelTerm((AlternativeAction.Classification)info.getResult().classification);
      }
    });
    /*
    myColumns.add(new IColumn() {
      @Override
      public String columnName() {
        return "N Trafos";
      }

      @Override
      public Object getRow(AlternativeInfo info) {
        return info.getResult().trafoHistory.getNTrafos();
      }
    });
    myColumns.add(new IColumn() {
      @Override
      public String columnName() {
        return "Min Size";
      }

      @Override
      public Object getRow(AlternativeInfo info) {
        return info.getMinSize().getWidth() + ", " + info.getMinSize().getHeight();
      }
    });
    myColumns.add(new IColumn() {
      @Override
      public String columnName() {
        return "Preferred Size";
      }

      @Override
      public Object getRow(AlternativeInfo info) {
        return info.getPrefSize().getWidth() + ", " + info.getPrefSize().getHeight();
      }
    });*/

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
        return myColumns.get(column).getRow(info);
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
      }
    });

    panel.add(new JBScrollPane(table));
    return panel;
  }
}
