import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.uibuilder.editor.NlEditor;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import nz.ac.auckland.alm.IArea;
import nz.ac.auckland.alm.algebra.Fragment;
import nz.ac.auckland.alm.algebra.trafo.GroupDetector;
import nz.ac.auckland.alm.trafo.*;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;


public class ActionTest extends AnAction {
    private Comparator<IArea> comparator = new Comparator<IArea>() {
        private String fragmentId(Fragment fragment) {
            String id = "";
            if (fragment.isHorizontalDirection())
                id += "h";
            else
                id += "v";
            List<IArea> items = fragment.getItems();
            for (IArea item : items) {
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

        // main layout
        DesignSurface surface = new DesignSurface(project);
        NlEditor nlEditor = new NlEditor(facet, virtualFile, project);
        NlModel model = NlModel.create(surface, nlEditor, facet, xmlFile);
        surface.setModel(model);
        model.renderImmediately();

        if (model.getComponents().size() != 1)
            return;
        NlComponent root = model.getComponents().get(0);

        final IArea item = ParsePsiLayout.parse(root);
        if (item instanceof Fragment)
            Trafo.swapOrientation((Fragment)item, true);

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

        // column test
        final IArea item2 = ParsePsiLayout.parse(root);
        if (item2 instanceof Fragment)
            Trafo.makeColumn((Fragment)item2);

        PsiFile columnFile = copyLand = landDir.findFile("column.xml");
        if (columnFile == null)
            columnFile = copyLand = landDir.createFile("column.xml");

        final XmlFile columnXmlFile = (XmlFile)columnFile;
        action = new WriteCommandAction<Void>(project, copyLand) {
            @Override
            protected void run(@NotNull Result<Void> result) throws Throwable {
                // fix prolog
                XmlDocument copyDocument = columnXmlFile.getDocument();
                if (copyDocument.getProlog() != null)
                    copyDocument.getProlog().delete();
                copyDocument.add(xmlFile.getDocument().getProlog().copy());

                PsiLayoutWriter.write(item2, columnXmlFile, project);
            }
        };
        action.execute();

        JDialog dialog = new JDialog();
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setTitle("Layout Alternatives");

        DesignSurface surface2 = createView(project, virtualFile, facet, copyXmlFile);
        DesignSurface surface3 = createView(project, virtualFile, facet, columnXmlFile);

        dialog.setLayout(new GridBagLayout());
        GridBagConstraints constraint = new GridBagConstraints();
        constraint.fill = GridBagConstraints.BOTH;
        constraint.anchor = GridBagConstraints.WEST;
        constraint.gridx = 0;
        constraint.gridy = 0;
        constraint.weightx = 1;
        constraint.weighty = 1;
        constraint.gridheight = 2;

        dialog.add(surface, constraint);

        constraint = new GridBagConstraints();
        constraint.fill = GridBagConstraints.BOTH;
        constraint.anchor = GridBagConstraints.NORTHEAST;
        constraint.gridx = 1;
        constraint.gridy = 0;
        constraint.weightx = 1;
        constraint.weighty = 1;
        dialog.add(surface2, constraint);

        constraint = new GridBagConstraints();
        constraint.fill = GridBagConstraints.BOTH;
        constraint.anchor = GridBagConstraints.SOUTHEAST;
        constraint.gridx = 1;
        constraint.gridy = 1;
        constraint.weightx = 1;
        constraint.weighty = 1;
        dialog.add(surface3, constraint);

        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    private DesignSurface createView(Project project, VirtualFile virtualFile, AndroidFacet facet, XmlFile xmlFile) {
        DesignSurface surface = new DesignSurface(project);
        NlEditor nlEditor = new NlEditor(facet, virtualFile, project);
        NlModel model = NlModel.create(surface, nlEditor, facet, xmlFile);
        surface.setModel(model);
        //model.requestRenderAsap();
        model.renderImmediately();
        return surface;
    }

}
