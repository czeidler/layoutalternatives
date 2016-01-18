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
import com.intellij.ui.components.JBList;
import nz.ac.auckland.alm.Area;
import nz.ac.auckland.alm.IArea;
import nz.ac.auckland.alm.ILayoutSpecArea;
import nz.ac.auckland.alm.LayoutSpec;
import nz.ac.auckland.alm.algebra.Fragment;
import nz.ac.auckland.alm.algebra.IDirection;
import nz.ac.auckland.alm.algebra.trafo.*;
import nz.ac.auckland.alm.trafo.*;
import nz.ac.auckland.linsolve.Variable;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
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

    private int getEquivalent(List<Fragment> results, Fragment fragment) {
        for (int i = 0; i < results.size(); i++) {
            Fragment result = results.get(i);
            if (result.isEquivalent(fragment))
                return i;
        }
        return -1;
    }

    static class AlternativeController extends WeakListenable<AlternativeController.IListener> {
        public interface IListener {
            void onAlternativesChanged();
            void onAlternativeSelected(int i);
        }

        final private List<AlternativeInfo> alternatives;
        private int selectedAlternative = -1;

        public AlternativeController(List<AlternativeInfo> alternatives) {
            this.alternatives = alternatives;
        }

        public void sortByRatio() {
            Collections.sort(alternatives, new Comparator<AlternativeInfo>() {
                @Override
                public int compare(AlternativeInfo a0, AlternativeInfo a1) {
                    double targetRatio = 16d/9;
                    if (Math.abs(a0.getPrefRatio() - targetRatio) < Math.abs(a1.getPrefRatio() - targetRatio))
                        return -1;
                    return 1;
                }
            });
            notifiyAlternativesChanged();
        }

        public void selectAlternative(int i) {
            if (i == this.selectedAlternative)
                return;
            this.selectedAlternative = i;
            notifyAlternativeSelected(i);
        }

        public int getSelectedAlternative() {
            return selectedAlternative;
        }

        public List<AlternativeInfo> getAlternatives() {
            return alternatives;
        }

        private void notifiyAlternativesChanged() {
            for (IListener listener : getListeners())
                listener.onAlternativesChanged();
        }

        private void notifyAlternativeSelected(int i) {
            for (IListener listener : getListeners())
                listener.onAlternativeSelected(i);
        }
    }

    class AlternativeInfo {
        private Fragment myFragment;
        private Area.Size prefSize;

        public AlternativeInfo(Fragment fragment) {
            this.myFragment = fragment;

            getSizes(fragment);
        }

        public double getPrefRatio() {
            return prefSize.getWidth() / prefSize.getHeight();
        }

        @Override
        public String toString() {
            return myFragment.toString() + ", pref(" + prefSize.getWidth() + "," + prefSize.getHeight() + "), ratio: " + getPrefRatio();
        }

        private void setTabstops(Fragment fragment) {
            IDirection direction = fragment.getDirection();
            for (int i = 0; i < fragment.getItems().size() - 1; i++) {
                IArea area1 = (IArea)fragment.getItems().get(i);
                IArea area2 = (IArea)fragment.getItems().get(i + 1);
                if (area1 instanceof Fragment)
                    setTabstops((Fragment)area1);
                if (area2 instanceof Fragment)
                    setTabstops((Fragment)area2);
                Variable tab = direction.createTab();
                direction.setTab(area1, tab);
                direction.setOppositeTab(area2, tab);
            }
        }

        private void getAtoms(Fragment fragment, List<IArea> areas) {
            for (IArea area : (List<IArea>)fragment.getItems()) {
                if (area instanceof Fragment)
                    getAtoms((Fragment)area, areas);
                else if (!areas.contains(area))
                    areas.add(area);
            }
        }

        private void getSizes(Fragment fragment) {
            setTabstops(fragment);

            List<IArea> areas = new ArrayList<IArea>();
            getAtoms(fragment, areas);
            LayoutSpec layoutSpec = new LayoutSpec();
            fragment.setLeft(layoutSpec.getLeft());
            fragment.setTop(layoutSpec.getTop());
            fragment.setRight(layoutSpec.getRight());
            fragment.setBottom(layoutSpec.getBottom());

            for (IArea area : areas) {
                if (area instanceof ILayoutSpecArea)
                    layoutSpec.addArea((ILayoutSpecArea)area);
            }

            prefSize = layoutSpec.getPreferredSize();
            layoutSpec.release();

            // reset variables
            for (IArea area : areas) {
                area.setLeft(null);
                area.setTop(null);
                area.setRight(null);
                area.setBottom(null);
            }
        }

        public Fragment getFragment() {
            return myFragment;
        }
    }

    static class LayoutRenderer {
        final Project project;
        final AndroidFacet facet;
        final XmlFile xmlFile;

        LayoutRenderer(Project project, XmlFile mainFile) {
            this.project = project;
            this.xmlFile = mainFile;
            this.facet = AndroidFacet.getInstance(mainFile);
        }

        public DesignSurface getDesignSurface(final Fragment fragment) {
            PsiDirectory resourceDir = xmlFile.getParent().getParentDirectory();
            PsiDirectory landDir = resourceDir.findSubdirectory("layout-land");
            if (landDir == null)
                landDir = resourceDir.createSubdirectory("layout-land");
            String fileName = xmlFile.getName();
            PsiFile copyLand = landDir.findFile(fileName);
            if (copyLand == null)
                copyLand = landDir.createFile(fileName);

            final XmlFile copyXmlFile = (XmlFile)copyLand;
            WriteCommandAction<Void> action = new WriteCommandAction<Void>(project, copyLand) {
                @Override
                protected void run(@NotNull Result<Void> result) throws Throwable {
                    // fix prolog
                    XmlDocument copyDocument = copyXmlFile.getDocument();
                    if (copyDocument.getProlog() != null)
                        copyDocument.getProlog().delete();
                    copyDocument.add(xmlFile.getDocument().getProlog().copy());

                    PsiLayoutWriter.write(fragment, copyXmlFile, project);
                }
            };
            action.execute();

            return createView(copyXmlFile, false);
        }

        public DesignSurface createView(XmlFile xmlFile, boolean renderImmediately) {
            DesignSurface surface = new DesignSurface(project);
            NlEditor nlEditor = new NlEditor(facet, xmlFile.getVirtualFile(), project);
            NlModel model = NlModel.create(surface, nlEditor, facet, xmlFile);
            surface.setModel(model);
            if (renderImmediately)
                model.renderImmediately();
            else
                model.requestRenderAsap();
            return surface;
        }
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

        final IArea item = ParsePsiLayout.parse(root);
        if (!(item instanceof Fragment))
            return;
        Fragment mainFragment = (Fragment)item;

        List<Fragment> groups = GroupDetector.detect(mainFragment, comparator);
        groups.add(mainFragment);
        FragmentAlternatives fragmentAlternatives = new FragmentAlternatives();
        fragmentAlternatives.addTransformation(new SwapTrafo());
        fragmentAlternatives.addTransformation(new ColumnOneToTwoTrafo());
        List<Fragment> alternatives = new ArrayList<Fragment>();
        for (Fragment group : groups) {
            List<ITransformation.Result> results = fragmentAlternatives.calculateAlternatives(group);
            for (ITransformation.Result result : results) {
                if (getEquivalent(alternatives, result.fragment) < 0)
                    alternatives.add(result.fragment);
            }
        }

        List<AlternativeInfo> alternativeInfos = new ArrayList<AlternativeInfo>();
        for (Fragment alternative : alternatives)
            alternativeInfos.add(new AlternativeInfo(alternative));

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
        infoPanel.add(getFragmentsPanel(main, alternativeController));
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

    private JPanel getFragmentsPanel(Fragment main, final AlternativeController alternativeController) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new Label("Original Layout: " + main.toString()));
        final JBList list = new JBList(alternativeController.getAlternatives().toArray());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                if (!listSelectionEvent.getValueIsAdjusting())
                    alternativeController.selectAlternative(list.getSelectedIndex());
            }
        });
        panel.add(list);
        return panel;
    }

}
