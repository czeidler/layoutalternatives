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

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.uibuilder.editor.NlEditor;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import nz.ac.auckland.alm.IArea;
import nz.ac.auckland.alm.algebra.Fragment;
import nz.ac.auckland.alm.algebra.trafo.*;
import nz.ac.auckland.alm.alternatives.gui.AlternativeController;
import nz.ac.auckland.alm.alternatives.gui.AlternativeInfoPanel;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;


public class AlternativeAction extends AnAction {
    private Comparator<IArea> comparator = new Comparator<IArea>() {
        private String fragmentId(Fragment fragment) {
            String id = "";
            if (fragment.isHorizontalDirection())
                id += "h";
            else
                id += "v";
            for (IArea item : (Iterable<IArea>)fragment.getItems()) {
                if (item instanceof Fragment)
                    id += fragmentId((Fragment)item);
                else
                    id += areaId(item);
            }
            return id;
        }

        private String areaId(IArea area) {
            if (area.getCookie() == null) {
                if (area.getId() == null)
                    return "area";
                else
                    return area.getId();
            }
            NlComponent component = (NlComponent)area.getCookie();
            return component.getTagName();
        }

        @Override
        public int compare(IArea area0, IArea area1) {
            String area0Id;
            String area1Id;
            if (area0 instanceof Fragment)
                area0Id = fragmentId((Fragment)area0);
            else
                area0Id = areaId(area0);

            if (area1 instanceof Fragment)
                area1Id = fragmentId((Fragment)area1);
            else
                area1Id = areaId(area1);

            if (area0Id.equals(area1Id))
                return 0;
            return -1;
        }
    };

    private int getEquivalent(List<ITransformation.Result> results, Fragment fragment) {
        for (int i = 0; i < results.size(); i++) {
            Fragment result = results.get(i).fragment;
            if (result.isEquivalent(fragment))
                return i;
        }
        return -1;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null)
            return;
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null)
            return;
        final Document document = editor.getDocument();
        if (document == null)
            return;
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
        if (virtualFile == null)
            return;

        PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, virtualFile);
        if (psiFile == null || !(psiFile instanceof XmlFile) || !LayoutDomFileDescription.isLayoutFile((XmlFile)psiFile))
            return;
        AndroidFacet facet = AndroidFacet.getInstance(psiFile);

        final XmlFile xmlFile = (XmlFile)psiFile;
        LayoutRenderer layoutRenderer = new LayoutRenderer(project, xmlFile);

        // main layout
        DesignSurface surface = new DesignSurface(project);
        NlEditor nlEditor = new NlEditor(facet, virtualFile, project);
        NlModel model = NlModel.create(surface, nlEditor, facet, xmlFile);
        surface.setModel(model);
        model.renderImmediately();

        if (model.getComponents().size() != 1)
            return;
        NlComponent root = model.getComponents().get(0);

        final IArea item = NlComponentParser.parse(root);
        if (!(item instanceof Fragment))
            return;
        Fragment mainFragment = (Fragment)item;

        List<Fragment> groups = GroupDetector.detect(mainFragment, comparator);
        groups.add(mainFragment);
        FragmentAlternatives fragmentAlternatives = new FragmentAlternatives();
        fragmentAlternatives.addTransformation(new SwapTrafo());
        fragmentAlternatives.addTransformation(new ColumnOneToTwoTrafo());
        List<ITransformation.Result> alternatives = new ArrayList<ITransformation.Result>();
        for (Fragment group : groups) {
            List<ITransformation.Result> results = fragmentAlternatives.calculateAlternatives(group);
            for (ITransformation.Result result : results) {
                if (getEquivalent(alternatives, result.fragment) < 0)
                    alternatives.add(result);
            }
        }

        List<AlternativeInfo> alternativeInfos = new ArrayList<AlternativeInfo>();
        for (ITransformation.Result alternative : alternatives)
            alternativeInfos.add(new AlternativeInfo(alternative.fragment, alternative.quality));

        AlternativeController alternativeController = new  AlternativeController(alternativeInfos);
        alternativeController.sortByRatio();
        showAlternatives(xmlFile, mainFragment, alternativeController, layoutRenderer);
    }

    private void showAlternatives(XmlFile rootXmlFile, Fragment main, AlternativeController alternativeController,
                                  LayoutRenderer layoutRenderer) {
        JDialog dialog = new JDialog();
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setTitle("Layout Alternatives");
        dialog.setLayout(new BoxLayout(dialog.getContentPane(), BoxLayout.X_AXIS));

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.add(AlternativeInfoPanel.create(main, alternativeController));
        infoPanel.add(layoutRenderer.createView(rootXmlFile, false));

        JPanel alternativeView = getAlternativeView(alternativeController, layoutRenderer);

        dialog.add(infoPanel);
        dialog.add(alternativeView);

        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    private AlternativeController.IListener alternativeViewListener;
    
    private JPanel getAlternativeView(final AlternativeController alternativeController, final LayoutRenderer layoutRenderer) {
        final JPanel panel = new JPanel();
        // keep a hard ref:
        alternativeViewListener = new AlternativeController.IListener() {
            @Override
            public void onAlternativesChanged() {
                
            }

            @Override
            public void onAlternativeSelected(int i) {
                AlternativeInfo alternativeInfo = alternativeController.getAlternatives().get(i);
                if (panel.getComponents().length > 0)
                    panel.remove(0);
                panel.add(layoutRenderer.getDesignSurface(alternativeInfo.getFragment()));
                panel.revalidate();
                panel.repaint();
                Container parent = panel.getParent();
                if (parent != null) {
                    parent.invalidate();
                    parent.repaint();
                }
            }
        };
        alternativeController.addListener(alternativeViewListener);
        return panel;
    }
}
