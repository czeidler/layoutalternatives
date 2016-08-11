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

import com.android.tools.idea.uibuilder.editor.NlEditor;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import nz.ac.auckland.alm.algebra.Fragment;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;


public class LayoutRenderer {
    final Project project;
    final AndroidFacet facet;
    final XmlFile xmlFile;

    LayoutRenderer(Project project, XmlFile mainFile) {
        this.project = project;
        this.xmlFile = mainFile;
        this.facet = AndroidFacet.getInstance(mainFile);
    }

    public Project getProject() {
        return project;
    }

    public AndroidFacet getFacet() {
        return facet;
    }

    public DesignSurface getDesignSurface(DesignSurface reuse, final Fragment fragment, boolean renderImmediately,
                                          boolean refreshResources) {
        XmlFile copyXmlFile = createLandFile(fragment);
        if (refreshResources)
            facet.refreshResources();
        return createView(reuse, copyXmlFile, renderImmediately);
    }

    public XmlFile createLandFile(final Fragment fragment) {
        PsiDirectory resourceDir = xmlFile.getParent().getParentDirectory();
        PsiDirectory landDir = resourceDir.findSubdirectory("layout-land");
        if (landDir == null)
            landDir = resourceDir.createSubdirectory("layout-land");
        String fileName = xmlFile.getName();
        PsiFile copyLand = landDir.findFile(fileName);
        if (copyLand == null)
            copyLand = landDir.createFile(fileName);

        facet.getConfigurationManager().getConfiguration(copyLand.getVirtualFile()).setTheme(
          facet.getConfigurationManager().getConfiguration(xmlFile.getVirtualFile()).getTheme()
        );

        final XmlFile copyXmlFile = (XmlFile)copyLand;
        WriteCommandAction<Void> action = new WriteCommandAction<Void>(project, copyLand) {
            @Override
            protected void run(@NotNull Result<Void> result) throws Throwable {
                // fix prolog
                XmlDocument copyDocument = copyXmlFile.getDocument();
                if (copyDocument.getProlog() != null)
                    copyDocument.getProlog().delete();
                copyDocument.add(xmlFile.getDocument().getProlog().copy());

                ALMLayoutWriter.write(xmlFile.getRootTag(), fragment, copyXmlFile, project, facet);
                //PsiLayoutWriter.write(fragment, copyXmlFile, project);
            }
        };
        action.execute();

        return copyXmlFile;
    }

    public DesignSurface createView(DesignSurface reuse, XmlFile xmlFile, boolean renderImmediately) {
        DesignSurface surface = reuse;
        if (surface == null)
            surface = new DesignSurface(project);
        NlEditor nlEditor = new NlEditor(facet, xmlFile.getVirtualFile(), project);
        NlModel model = NlModel.create(surface, nlEditor, facet, xmlFile);
        surface.setModel(model);
        if (renderImmediately)
            model.renderImmediately();
        else
            model.requestRenderAsap();
        nlEditor.dispose();
        model.dispose();
        return surface;
    }
}
