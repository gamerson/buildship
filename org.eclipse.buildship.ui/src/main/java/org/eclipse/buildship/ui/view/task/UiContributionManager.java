/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package org.eclipse.buildship.ui.view.task;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.eclipse.buildship.ui.UiPluginConstants;
import org.eclipse.buildship.ui.util.nodeselection.ActionEnablingSelectionChangedListener;
import org.eclipse.buildship.ui.util.nodeselection.ActionShowingContextMenuListener;
import org.eclipse.buildship.ui.util.nodeselection.SelectionSpecificAction;
import org.eclipse.buildship.ui.util.selection.ContextActivatingViewPartListener;
import org.eclipse.buildship.ui.view.CollapseTreeNodesAction;
import org.eclipse.buildship.ui.view.ExpandTreeNodesAction;
import org.eclipse.buildship.ui.view.ShowFilterAction;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.action.*;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Menu;

/**
 * Adds UI contributions to the {@link TaskView}.
 */
public final class UiContributionManager {

    private static final String TOOLBAR_MISC_GROUP = "toolbarMiscGroup";
    private static final String TOOLBAR_TREE_GROUP = "toolbarTreeGroup";
    private static final String MENU_SORTING_GROUP = "toolbarSortingGroup";
    private static final String MENU_FILTERING_GROUP = "menuFilteringGroup";
    private static final String MENU_MISC_GROUP = "menuMiscGroup";

    private final TaskView taskView;
    private final ImmutableList<SelectionSpecificAction> toolBarActions;
    private final ImmutableList<SelectionSpecificAction> contextMenuActions;
    private final ImmutableList<SelectionSpecificAction> contextMenuActionsPrecededBySeparator;
    private final ImmutableList<SelectionSpecificAction> contextMenuActionsSucceededBySeparator;
    private final ActionEnablingSelectionChangedListener toolBarActionsSelectionChangedListener;
    private final TreeViewerSelectionChangeListener treeViewerSelectionChangeListener;
    private final TreeViewerDoubleClickListener treeViewerDoubleClickListener;
    private final ContextActivatingViewPartListener contextActivatingViewPartListener;
    private final WorkbenchSelectionListener workbenchSelectionListener;
    private final TaskViewUpdatingProjectChangeListener taskViewUpdatingProjectChangeListener;

    public UiContributionManager(TaskView taskView) {
        this.taskView = Preconditions.checkNotNull(taskView);

        // create actions
        RunTasksAction runTasksAction = new RunTasksAction(UiPluginConstants.RUN_TASKS_COMMAND_ID);
        RunDefaultTasksAction runDefaultTasksAction = new RunDefaultTasksAction(UiPluginConstants.RUN_DEFAULT_TASKS_COMMAND_ID);
        CreateRunConfigurationAction createRunConfigurationAction = new CreateRunConfigurationAction(UiPluginConstants.OPEN_RUN_CONFIGURATION_COMMAND_ID);
        OpenRunConfigurationAction openRunConfigurationAction = new OpenRunConfigurationAction(UiPluginConstants.OPEN_RUN_CONFIGURATION_COMMAND_ID);
        OpenBuildScriptAction openBuildScriptAction = new OpenBuildScriptAction(UiPluginConstants.OPEN_BUILD_SCRIPT_COMMAND_ID);
        ExpandTreeNodesAction expandNodesAction = new ExpandTreeNodesAction(this.taskView.getTreeViewer());
        CollapseTreeNodesAction collapseNodesAction = new CollapseTreeNodesAction(this.taskView.getTreeViewer());

        // add selection-sensitive tool bar actions
        this.toolBarActions = ImmutableList.of();

        // add selection-sensitive context menu actions
        this.contextMenuActions = ImmutableList.<SelectionSpecificAction>of(runTasksAction, runDefaultTasksAction,
                createRunConfigurationAction, openRunConfigurationAction,
                openBuildScriptAction, expandNodesAction, collapseNodesAction);
        this.contextMenuActionsPrecededBySeparator = ImmutableList.<SelectionSpecificAction>of(openBuildScriptAction, expandNodesAction);
        this.contextMenuActionsSucceededBySeparator = ImmutableList.of();

        // create listeners
        this.toolBarActionsSelectionChangedListener = new ActionEnablingSelectionChangedListener(taskView, this.toolBarActions);
        this.treeViewerSelectionChangeListener = new TreeViewerSelectionChangeListener(taskView);
        this.treeViewerDoubleClickListener = new TreeViewerDoubleClickListener(UiPluginConstants.RUN_TASKS_COMMAND_ID, taskView.getTreeViewer());
        this.contextActivatingViewPartListener = new ContextActivatingViewPartListener(UiPluginConstants.TASKVIEW_CONTEXT_ID, taskView);
        this.workbenchSelectionListener = new WorkbenchSelectionListener(taskView);
        this.taskViewUpdatingProjectChangeListener = new TaskViewUpdatingProjectChangeListener(taskView);
    }

    /**
     * Wires all UI contributions into the task view.
     */
    public void wire() {
        populateToolBar();
        populateMenu();
        registerContextMenu();
        registerListeners();
    }

    private void populateToolBar() {
        IToolBarManager manager = this.taskView.getViewSite().getActionBars().getToolBarManager();
        manager.add(new GroupMarker(TOOLBAR_TREE_GROUP));
        manager.appendToGroup(TOOLBAR_TREE_GROUP, new ExpandTreeNodesAction(this.taskView.getTreeViewer()));
        manager.appendToGroup(TOOLBAR_TREE_GROUP, new CollapseTreeNodesAction(this.taskView.getTreeViewer()));
        manager.appendToGroup(TOOLBAR_TREE_GROUP, new ShowFilterAction(this.taskView.getFilteredTree()));
        manager.appendToGroup(TOOLBAR_TREE_GROUP, new Separator());
        manager.add(new GroupMarker(TOOLBAR_MISC_GROUP));
        manager.appendToGroup(TOOLBAR_MISC_GROUP, new RefreshViewAction(UiPluginConstants.REFRESH_TASKVIEW_COMMAND_ID));
        manager.appendToGroup(TOOLBAR_MISC_GROUP, new ToggleLinkToSelectionAction(this.taskView));
    }

    private void populateMenu() {
        IMenuManager manager = this.taskView.getViewSite().getActionBars().getMenuManager();
        manager.add(new Separator(MENU_FILTERING_GROUP));
        manager.appendToGroup(MENU_FILTERING_GROUP, new FilterTaskSelectorsAction(this.taskView));
        manager.appendToGroup(MENU_FILTERING_GROUP, new FilterProjectTasksAction(this.taskView));
        manager.appendToGroup(MENU_FILTERING_GROUP, new FilterPrivateTasksAction(this.taskView));
        manager.add(new Separator(MENU_SORTING_GROUP));
        manager.appendToGroup(MENU_SORTING_GROUP, new SortTasksByTypeAction(this.taskView));
        manager.appendToGroup(MENU_SORTING_GROUP, new SortTasksByVisibilityAction(this.taskView));
        manager.add(new Separator(MENU_MISC_GROUP));
        manager.appendToGroup(MENU_MISC_GROUP, new ToggleShowTreeHeaderAction(this.taskView.getTreeViewer(), this.taskView.getState()));
    }

    private void registerContextMenu() {
        TreeViewer treeViewer = this.taskView.getTreeViewer();
        MenuManager menuManager = new MenuManager();
        menuManager.setRemoveAllWhenShown(true);
        menuManager.addMenuListener(new ActionShowingContextMenuListener(this.taskView, this.contextMenuActions, this.contextMenuActionsPrecededBySeparator, this.contextMenuActionsSucceededBySeparator));
        Menu contextMenu = menuManager.createContextMenu(treeViewer.getTree());
        treeViewer.getTree().setMenu(contextMenu);
        this.taskView.getViewSite().registerContextMenu(menuManager, treeViewer);
    }

    private void registerListeners() {
        this.taskView.getTreeViewer().addSelectionChangedListener(this.toolBarActionsSelectionChangedListener);
        this.taskView.getTreeViewer().addSelectionChangedListener(this.treeViewerSelectionChangeListener);
        this.taskView.getTreeViewer().addDoubleClickListener(this.treeViewerDoubleClickListener);
        this.taskView.getSite().getPage().addPartListener(this.contextActivatingViewPartListener);
        this.taskView.getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(this.workbenchSelectionListener);
        ResourcesPlugin.getWorkspace().addResourceChangeListener(this.taskViewUpdatingProjectChangeListener);
    }

    public void dispose() {
        this.taskView.getTreeViewer().removeSelectionChangedListener(this.toolBarActionsSelectionChangedListener);
        this.taskView.getTreeViewer().removeSelectionChangedListener(this.treeViewerSelectionChangeListener);
        this.taskView.getTreeViewer().removeDoubleClickListener(this.treeViewerDoubleClickListener);
        this.taskView.getSite().getPage().removePartListener(this.contextActivatingViewPartListener);
        this.taskView.getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(this.workbenchSelectionListener);
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(this.taskViewUpdatingProjectChangeListener);
    }

}
