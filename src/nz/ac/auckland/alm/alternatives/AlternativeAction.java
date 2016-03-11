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
import nz.ac.auckland.alm.Area;
import nz.ac.auckland.alm.IArea;
import nz.ac.auckland.alm.LayoutSpec;
import nz.ac.auckland.alm.algebra.Fragment;
import nz.ac.auckland.alm.algebra.FragmentUtils;
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

    private int getEquivalent(List<FragmentAlternatives.Result> results, Fragment fragment) {
        for (int i = 0; i < results.size(); i++) {
            Fragment result = results.get(i).fragment;
            if (result.isEquivalent(fragment))
                return i;
        }
        return -1;
    }

    static public class Classification {
        public TrafoHistory trafoHistory;
        public Area.Size minSize;
        public Area.Size prefSize;
        public double childrenPrefDiff2Width;
        public double childrenPrefDiff2Height;
        public float orientationWeight;
    }

    static public class Classifier implements IAlternativeClassifier<Classification> {
        private float targetWidth;
        private float targetHeight;

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
                classification.childrenPrefDiff2Width += Math.pow(width - areaPrefSize.getWidth(), 2);
                classification.childrenPrefDiff2Height += Math.pow(height - areaPrefSize.getHeight(), 2);
            }
            classification.childrenPrefDiff2Width /= areas.size();
            classification.childrenPrefDiff2Height /= areas.size();

            float summedFragmentWeight = SymmetryAnalyzer.summedFragmentWeights(fragment);
            float summedFragmentWeightSameOrientation = SymmetryAnalyzer.summedFragmentWeightSameOrientation(fragment);
            classification.orientationWeight = 1 - summedFragmentWeightSameOrientation / summedFragmentWeight;
            return classification;
        }

        @Override
        public List<ITransformation> selectTransformations(Fragment fragment, Classification classification) {
            List<ITransformation> trafos = new ArrayList<ITransformation>();
            return trafos;
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

            return (3 * getPrefSizeDiffTerm(classification)
                    + getRatioTerm(classification)
                    + 2 * getNTrafoTerm(classification)
                    + 4 * getSymmetryTerm(classification)) / 4;
        }

        public double getPrefSizeDiffTerm(Classification classification) {
            return (classification.childrenPrefDiff2Width + classification.childrenPrefDiff2Height)
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
            return classification.orientationWeight;
        }
    }

    private Classifier classifier = new Classifier();

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

        FragmentAlternatives fragmentAlternatives = new FragmentAlternatives(classifier, new FilteredGroupDetector(comparator));
        SwapTrafo swapTrafo = new SwapTrafo();
        fragmentAlternatives.addTrafo(swapTrafo);
        fragmentAlternatives.addTrafo(new ColumnFlowTrafo());
        fragmentAlternatives.addTrafo(new InverseRowFlowTrafo());

        List<ITransformation> trafos = fragmentAlternatives.getTrafos();
        List<FragmentAlternatives.Result> alternatives = new ArrayList<FragmentAlternatives.Result>();

        IPermutationSelector<Classification> selector
          = new ChainPermutationSelector<Classification>(
          new ApplyToAllPermutationSelector<Classification>(trafos, swapTrafo),
          new RandomPermutationSelector<Classification>(trafos));

        List<FragmentAlternatives.Result> results = fragmentAlternatives.calculateAlternatives(mainFragment, selector, 50, 1000 * 60);
        for (FragmentAlternatives.Result result : results) {
            if (getEquivalent(alternatives, result.fragment) < 0)
                alternatives.add(result);
        }

        List<AlternativeInfo> alternativeInfos = new ArrayList<AlternativeInfo>();
        for (FragmentAlternatives.Result alternative : alternatives)
            alternativeInfos.add(new AlternativeInfo(alternative));

        AlternativeController alternativeController = new  AlternativeController(alternativeInfos, classifier);
        alternativeController.sortByObjectiveValue();
        showAlternatives(xmlFile, mainFragment, alternativeController, layoutRenderer);
    }

    private void showAlternatives(XmlFile rootXmlFile, Fragment main, AlternativeController alternativeController,
                                  LayoutRenderer layoutRenderer) {
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
        mainPane.setDividerLocation(400);

        dialog.add(mainPane);

        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    private AlternativeController.IListener alternativeViewListener;
    
    private JPanel getAlternativeView(final AlternativeController alternativeController, final LayoutRenderer layoutRenderer) {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        final Label label = new Label();
        mainPanel.add(label);
        final JPanel previewPanel = new JPanel(new BorderLayout());
        mainPanel.add(previewPanel);

        // keep a hard ref:
        alternativeViewListener = new AlternativeController.IListener() {
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
        return mainPanel;
    }
}
