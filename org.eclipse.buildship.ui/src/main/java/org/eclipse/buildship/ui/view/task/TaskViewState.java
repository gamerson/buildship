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

import org.osgi.service.prefs.BackingStoreException;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;

import org.eclipse.buildship.core.util.preference.EclipsePreferencesUtils;
import org.eclipse.buildship.ui.UiPlugin;
import org.eclipse.buildship.ui.view.TreeViewerState;

/**
 * Represents the (persistable) configuration state of the {@link TaskView}. Backed by the Eclipse
 * Preferences API.
 */
public final class TaskViewState implements TreeViewerState {

    private static final String PREF_PROJECT_TASKS_VISIBLE = "tasksView.projectTasksVisible";
    private static final String PREF_TASK_SELECTORS_VISIBLE = "tasksView.taskSelectorsVisible";
    private static final String PREF_PRIVATE_TASKS_VISIBLE = "tasksView.privateTasksVisible";
    private static final String PREF_SORT_BY_TYPE = "tasksView.sortByType";
    private static final String PREF_SORT_BY_VISIBILITY = "tasksView.sortByVisibility";
    private static final String PREF_LINK_TO_SELECTION = "tasksView.linkToSelection";
    private static final String PREF_SHOW_TREE_HEADER = "tasksView.showTreeHeader";
    private static final String PREF_HEADER_NAME_COLUMN_WIDTH = "tasksView.headerNameColumnWidth";
    private static final String PREF_HEADER_DESCRIPTION_COLUMN_WIDTH = "tasksView.headerDescriptionColumnWidth";

    private boolean projectTasksVisible;
    private boolean taskSelectorsVisible;
    private boolean privateTasksVisible;
    private boolean sortByType;
    private boolean sortByVisibility;
    private boolean linkToSelection;
    private boolean showTreeHeader;
    private int headerNameColumnWidth;
    private int headerDescriptionColumnWidth;

    public void load() {
        IEclipsePreferences prefs = EclipsePreferencesUtils.getInstanceScope().getNode(UiPlugin.PLUGIN_ID);
        this.projectTasksVisible = prefs.getBoolean(PREF_PROJECT_TASKS_VISIBLE, false);
        this.taskSelectorsVisible = prefs.getBoolean(PREF_TASK_SELECTORS_VISIBLE, true);
        this.privateTasksVisible = prefs.getBoolean(PREF_PRIVATE_TASKS_VISIBLE, false);
        this.sortByType = prefs.getBoolean(PREF_SORT_BY_TYPE, true);
        this.sortByVisibility = prefs.getBoolean(PREF_SORT_BY_VISIBILITY, true);
        this.linkToSelection = prefs.getBoolean(PREF_LINK_TO_SELECTION, false);
        this.showTreeHeader = prefs.getBoolean(PREF_SHOW_TREE_HEADER, false);
        this.headerNameColumnWidth = prefs.getInt(PREF_HEADER_NAME_COLUMN_WIDTH, 200);
        this.headerDescriptionColumnWidth = prefs.getInt(PREF_HEADER_DESCRIPTION_COLUMN_WIDTH, 400);
    }

    public void save() {
        IEclipsePreferences prefs = EclipsePreferencesUtils.getInstanceScope().getNode(UiPlugin.PLUGIN_ID);
        prefs.putBoolean(PREF_PROJECT_TASKS_VISIBLE, this.projectTasksVisible);
        prefs.putBoolean(PREF_TASK_SELECTORS_VISIBLE, this.taskSelectorsVisible);
        prefs.putBoolean(PREF_PRIVATE_TASKS_VISIBLE, this.privateTasksVisible);
        prefs.putBoolean(PREF_SORT_BY_TYPE, this.sortByType);
        prefs.putBoolean(PREF_SORT_BY_VISIBILITY, this.sortByVisibility);
        prefs.putBoolean(PREF_LINK_TO_SELECTION, this.linkToSelection);
        prefs.putBoolean(PREF_SHOW_TREE_HEADER, this.showTreeHeader);
        prefs.putInt(PREF_HEADER_NAME_COLUMN_WIDTH, this.headerNameColumnWidth);
        prefs.putInt(PREF_HEADER_DESCRIPTION_COLUMN_WIDTH, this.headerDescriptionColumnWidth);

        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            UiPlugin.logger().error("Unable to store task view preferences.", e);
        }
    }

    public boolean isProjectTasksVisible() {
        return this.projectTasksVisible;
    }

    public void setProjectTasksVisible(boolean projectTasksVisible) {
        this.projectTasksVisible = projectTasksVisible;
    }

    public boolean isTaskSelectorsVisible() {
        return this.taskSelectorsVisible;
    }

    public void setTaskSelectorsVisible(boolean taskSelectorsVisible) {
        this.taskSelectorsVisible = taskSelectorsVisible;
    }

    public boolean isPrivateTasksVisible() {
        return this.privateTasksVisible;
    }

    public void setPrivateTasksVisible(boolean privateTasksVisible) {
        this.privateTasksVisible = privateTasksVisible;
    }

    public boolean isSortByType() {
        return this.sortByType;
    }

    public void setSortByType(boolean sortByType) {
        this.sortByType = sortByType;
    }

    public boolean isSortByVisibility() {
        return this.sortByVisibility;
    }

    public void setSortByVisibility(boolean sortByVisibility) {
        this.sortByVisibility = sortByVisibility;
    }

    public boolean isLinkToSelection() {
        return this.linkToSelection;
    }

    public void setLinkToSelection(boolean linkToSelection) {
        this.linkToSelection = linkToSelection;
    }

    @Override
    public boolean isShowTreeHeader() {
        return this.showTreeHeader;
    }

    @Override
    public void setShowTreeHeader(boolean showTreeHeader) {
        this.showTreeHeader = showTreeHeader;
    }

    public int getHeaderNameColumnWidth() {
        return this.headerNameColumnWidth;
    }

    public void setHeaderNameColumnWidth(int headerNameColumnWidth) {
        this.headerNameColumnWidth = headerNameColumnWidth;
    }

    public int getHeaderDescriptionColumnWidth() {
        return this.headerDescriptionColumnWidth;
    }

    public void setHeaderDescriptionColumnWidth(int headerDescriptionColumnWidth) {
        this.headerDescriptionColumnWidth = headerDescriptionColumnWidth;
    }

    public void dispose() {
        save();
    }

}
