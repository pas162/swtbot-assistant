package com.renesas.swtbot.assistant.views;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.core.resources.ResourcesPlugin;

/**
 * Dialog for selecting a Java package from workspace projects.
 * Uses Eclipse JDT's native package browsing approach.
 */
public class PackageSelectionDialog extends Dialog {

    private TreeViewer treeViewer;
    private IPackageFragment selectedPackage;
    private String selectedProjectName;
    private String selectedPackageName;

    public PackageSelectionDialog(Shell parentShell) {
        super(parentShell);
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Select Package");
        shell.setSize(500, 400);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite composite = (Composite) super.createDialogArea(parent);
        composite.setLayout(new GridLayout(1, false));
        composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label label = new Label(composite, SWT.NONE);
        label.setText("Select a source folder and package:");
        label.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        treeViewer = new TreeViewer(composite, SWT.BORDER | SWT.SINGLE);
        treeViewer.getTree().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // Set up content provider for Java elements
        treeViewer.setContentProvider(new JavaPackageContentProvider());

        // Set up label provider with Java icons
        ILabelProvider labelProvider = new JavaElementLabelProvider(
                JavaElementLabelProvider.SHOW_DEFAULT |
                JavaElementLabelProvider.SHOW_QUALIFIED |
                JavaElementLabelProvider.SHOW_ROOT);
        treeViewer.setLabelProvider(labelProvider);

        // Filter to show only packages and source roots
        treeViewer.addFilter(new ViewerFilter() {
            @Override
            public boolean select(Viewer viewer, Object parentElement, Object element) {
                if (element instanceof IJavaProject) {
                    return true;
                }
                if (element instanceof IPackageFragmentRoot) {
                    try {
                        IPackageFragmentRoot root = (IPackageFragmentRoot) element;
                        return root.getKind() == IPackageFragmentRoot.K_SOURCE;
                    } catch (JavaModelException e) {
                        return false;
                    }
                }
                if (element instanceof IPackageFragment) {
                    return true;
                }
                return false;
            }
        });

        // Set input to all Java projects in workspace
        treeViewer.setInput(JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()));

        // Expand first level
        treeViewer.expandToLevel(2);

        // Add selection listener
        treeViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                updateButtons();
            }
        });

        // Add double-click listener for package selection
        treeViewer.addDoubleClickListener(new IDoubleClickListener() {
            @Override
            public void doubleClick(DoubleClickEvent event) {
                IStructuredSelection selection = (IStructuredSelection) event.getSelection();
                Object selected = selection.getFirstElement();
                if (selected instanceof IPackageFragment) {
                    buttonPressed(IDialogConstants.OK_ID);
                } else {
                    // Expand/collapse on double click
                    if (treeViewer.getExpandedState(selected)) {
                        treeViewer.collapseToLevel(selected, TreeViewer.ALL_LEVELS);
                    } else {
                        treeViewer.expandToLevel(selected, 1);
                    }
                }
            }
        });

        return composite;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);
        updateButtons();
    }

    private void updateButtons() {
        Button okButton = getButton(IDialogConstants.OK_ID);
        if (okButton != null) {
            IStructuredSelection selection = (IStructuredSelection) treeViewer.getSelection();
            Object selected = selection.getFirstElement();
            okButton.setEnabled(selected instanceof IPackageFragment);
        }
    }

    @Override
    protected void okPressed() {
        IStructuredSelection selection = (IStructuredSelection) treeViewer.getSelection();
        Object selected = selection.getFirstElement();
        if (selected instanceof IPackageFragment) {
            selectedPackage = (IPackageFragment) selected;
            selectedPackageName = selectedPackage.getElementName();
            selectedProjectName = selectedPackage.getJavaProject().getElementName();
        }
        super.okPressed();
    }

    public String getSelectedPackage() {
        return selectedPackageName;
    }

    public String getSelectedProject() {
        return selectedProjectName;
    }

    /**
     * Content provider for Java package tree
     */
    private class JavaPackageContentProvider implements ITreeContentProvider {

        @Override
        public Object[] getElements(Object inputElement) {
            if (inputElement instanceof org.eclipse.jdt.core.IJavaModel) {
                try {
                    return ((org.eclipse.jdt.core.IJavaModel) inputElement).getJavaProjects();
                } catch (JavaModelException e) {
                    return new Object[0];
                }
            }
            return new Object[0];
        }

        @Override
        public Object[] getChildren(Object parentElement) {
            try {
                if (parentElement instanceof IJavaProject) {
                    IJavaProject project = (IJavaProject) parentElement;
                    return project.getPackageFragmentRoots();
                }
                if (parentElement instanceof IPackageFragmentRoot) {
                    IPackageFragmentRoot root = (IPackageFragmentRoot) parentElement;
                    return root.getChildren();
                }
                if (parentElement instanceof IPackageFragment) {
                    // Return subpackages by scanning root's children
                    IPackageFragment fragment = (IPackageFragment) parentElement;
                    IPackageFragmentRoot root = (IPackageFragmentRoot) fragment.getParent();
                    String prefix = fragment.getElementName() + ".";
                    java.util.List<IPackageFragment> children = new java.util.ArrayList<>();
                    
                    // Get all package fragments from root
                    for (org.eclipse.jdt.core.IJavaElement element : root.getChildren()) {
                        if (element instanceof IPackageFragment) {
                            IPackageFragment pkg = (IPackageFragment) element;
                            String name = pkg.getElementName();
                            if (name.startsWith(prefix) && !name.equals(fragment.getElementName())) {
                                // Check if it's a direct child
                                String suffix = name.substring(prefix.length());
                                if (!suffix.contains(".")) {
                                    children.add(pkg);
                                }
                            }
                        }
                    }
                    return children.toArray();
                }
            } catch (JavaModelException e) {
                // Ignore
            }
            return new Object[0];
        }

        @Override
        public Object getParent(Object element) {
            if (element instanceof org.eclipse.jdt.core.IJavaElement) {
                return ((org.eclipse.jdt.core.IJavaElement) element).getParent();
            }
            return null;
        }

        @Override
        public boolean hasChildren(Object element) {
            return getChildren(element).length > 0;
        }

        @Override
        public void dispose() {
        }

        @Override
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        }
    }
}
