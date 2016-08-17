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
import com.android.tools.idea.uibuilder.editor.NlEditorPanel;
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
import nz.ac.auckland.alm.alternatives.gui.AlternativeMain;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;

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
        NlEditor nlEditor = new NlEditor(facet, virtualFile, project);
        NlEditorPanel nlEditorPanel = new NlEditorPanel(nlEditor, facet, virtualFile);
        DesignSurface surface = new DesignSurface(project, nlEditorPanel);
        NlModel model = NlModel.create(surface, nlEditor, facet, xmlFile);
        surface.setModel(model);
        model.render();
        nlEditor.dispose();

        if (model.getComponents().size() != 1)
            return;
        NlComponent root = model.getComponents().get(0);

        final IArea item = NlComponentParser.parse(root);
        if (!(item instanceof Fragment))
            return;
        Fragment mainFragment = (Fragment)item;

        Classifier classifier = new Classifier(root.h, root.w);
        FragmentAlternatives fragmentAlternatives = new FragmentAlternatives(classifier, new FilteredGroupDetector(comparator));
        SwapTrafo swapTrafo = new SwapTrafo();
        ColumnTrafo columnTrafo = new ColumnTrafo();
        InverseRowFlowTrafo inverseRowFlowTrafo = new InverseRowFlowTrafo();
        fragmentAlternatives.addTrafo(swapTrafo);
        fragmentAlternatives.addTrafo(columnTrafo);
        fragmentAlternatives.addTrafo(inverseRowFlowTrafo);

        List<ITransformation> trafos = fragmentAlternatives.getTrafos();
        List<FragmentAlternatives.Result> alternatives = new ArrayList<FragmentAlternatives.Result>();

        IPermutationSelector<Classification> selector
          = new ChainPermutationSelector<Classification>(
          new ApplyToAllPermutationSelector<Classification>(trafos, swapTrafo),
          new ApplyToAllPermutationSelector<Classification>(trafos, columnTrafo),
          new ApplyToAllPermutationSelector<Classification>(trafos, inverseRowFlowTrafo),
          new RandomPermutationSelector<Classification>(trafos)
        );

        List<FragmentAlternatives.Result> results = fragmentAlternatives.calculateAlternatives(mainFragment, selector, 400, 5 * 1000 * 60);
        for (FragmentAlternatives.Result result : results) {
            if (getEquivalent(alternatives, result.fragment) < 0)
                alternatives.add(result);
        }

        List<AlternativeInfo> alternativeInfos = new ArrayList<AlternativeInfo>();
        for (FragmentAlternatives.Result alternative : alternatives)
            alternativeInfos.add(new AlternativeInfo(alternative));

        AlternativeController alternativeController = new  AlternativeController(psiFile.getName(), alternativeInfos, classifier);
        alternativeController.sortByObjectiveValue();
        AlternativeMain.showAlternatives(project, xmlFile, mainFragment, alternativeController, layoutRenderer, classifier);
    }
}
