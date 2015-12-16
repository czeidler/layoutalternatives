import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.uibuilder.editor.NlEditor;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlProlog;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.components.panels.HorizontalLayout;
import nz.ac.auckland.alm.trafo.*;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.FileOutputStream;
import java.util.Arrays;


public class ActionTest extends AnAction {
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
            return;/home/czei002/studio-master-dev/tools/layoutalternatives/src/ActionTest.java

        PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, virtualFile);
        if (psiFile == null || !(psiFile instanceof XmlFile) || !LayoutDomFileDescription.isLayoutFile((XmlFile)psiFile))
            return;
        AndroidFacet facet = AndroidFacet.getInstance(psiFile);
        final XmlFile xmlFile = (XmlFile)psiFile;

        final Item item = ParsePsiLayout.parse(xmlFile);
        if (item instanceof Group)
            Trafo.swapOrientation((Group)item, true);

        PsiDirectory resourceDir = xmlFile.getParent().getParentDirectory();
        PsiDirectory landDir = resourceDir.findSubdirectory("layout-land");
        if (landDir == null)
            landDir = resourceDir.createSubdirectory("layout-land");
        PsiFile copyLand = landDir.findFile(xmlFile.getName());
        if (copyLand == null)
            copyLand = landDir.createFile(xmlFile.getName());

        final XmlFile copyXmlFile = (XmlFile)copyLand;
        WriteCommandAction<Void> action = new WriteCommandAction<Void>(project, copyLand) {
            @Override
            protected void run(@NotNull Result<Void> result) throws Throwable {
                // fix prolog
                XmlDocument copyDocument = copyXmlFile.getDocument();
                if (copyDocument.getProlog() != null)
                    copyDocument.getProlog().delete();
                copyDocument.add(xmlFile.getDocument().getProlog().copy());

                PsiLayoutWriter.write(item, copyXmlFile, project);
            }
        };
        action.execute();

        JDialog dialog = new JDialog();
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setTitle("Layout Alternatives");

        DesignSurface surface = createView(project, virtualFile, facet, xmlFile);
        DesignSurface surface2 = createView(project, virtualFile, facet, (XmlFile)copyLand);

        dialog.setLayout(new HorizontalLayout(10));
        dialog.add(surface);
        dialog.add(surface2);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    private DesignSurface createView(Project project, VirtualFile virtualFile, AndroidFacet facet, XmlFile xmlFile) {
        DesignSurface surface = new DesignSurface(project);
        NlEditor nlEditor = new NlEditor(facet, virtualFile, project);
        NlModel model = NlModel.create(surface, nlEditor, facet, xmlFile);
        surface.setModel(model);
        model.requestRenderAsap();

        return surface;
    }

}
