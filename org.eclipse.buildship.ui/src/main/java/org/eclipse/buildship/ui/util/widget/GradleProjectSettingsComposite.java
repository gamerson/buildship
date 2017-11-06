/*
 * Copyright (c) 2017 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.ui.util.widget;

import com.gradleware.tooling.toolingmodel.util.Maybe;
import com.gradleware.tooling.toolingmodel.util.Pair;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.i18n.CoreMessages;

/**
 * Widget containing the following Gradle configuration elements.
 * <ul>
 * <li>Gradle distribution</li>
 * <li>Gradle user home</li>
 * <li>Build scans enablement</li>
 * <li>Offline mode enablement</li>
 * </ul>
 *
 * @author Donat Csikos
 */
public final class GradleProjectSettingsComposite extends Composite {

    private final String overrideCheckboxLabel;
    private final String configureParentPrefsLinkLabel;

    private Button overrideSettingsCheckbox;
    private Link parentPreferenceLink;
    private GradleDistributionGroup gradleDistributionGroup;
    private GradleUserHomeGroup gradleUserHomeGroup;
    private Button offlineModeCheckbox;
    private Button buildScansCheckbox;
    private Button autoBuildCheckbox;

    private GradleProjectSettingsComposite(Composite parent, boolean hasOverrideCheckbox, String overrideCheckboxLabel, String configureParentPrefsLinkLabel, boolean hasAutoRefreshCheckbox) {
        super(parent, SWT.NONE);

        this.overrideCheckboxLabel = overrideCheckboxLabel;
        this.configureParentPrefsLinkLabel = configureParentPrefsLinkLabel;

        GridLayoutFactory.swtDefaults().numColumns(2).margins(0, 0).applyTo(this);

        if (hasOverrideCheckbox) {
            createOverrideWorkspaceCheckbox(this);
            configureParentPreferenceLink(this);
            createHorizontalLine(this);
        }
        createGradleDistributionGroup(this);
        createGradleUserHomeGroup(this);
        createOfflineModeCheckbox(this);
        createBuildScansCheckbox(this);
        if (hasAutoRefreshCheckbox) {
            createAutoBuildCheckbox(this);
        }

        addListeners();

        Dialog.applyDialogFont(this);
    }

    private void createOverrideWorkspaceCheckbox(Composite parent) {
        this.overrideSettingsCheckbox = new Button(parent, SWT.CHECK);
        this.overrideSettingsCheckbox.setText(this.overrideCheckboxLabel);
        GridDataFactory.swtDefaults().applyTo(parent);
    }

    private void configureParentPreferenceLink(Composite parent) {
        this.parentPreferenceLink = new Link(parent, SWT.NONE);
        this.parentPreferenceLink.setFont(parent.getFont());
        this.parentPreferenceLink.setText("<A>" + this.configureParentPrefsLinkLabel + "...</A>");
        GridDataFactory.swtDefaults().align(SWT.END, SWT.CENTER).grab(false, false).applyTo(this.parentPreferenceLink);
    }

    private void createHorizontalLine(Composite parent) {
        Label line = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
        GridDataFactory.fillDefaults().align(SWT.FILL, SWT.CENTER).span(2, 1).applyTo(line);
    }

    private void createGradleDistributionGroup(Composite parent) {
        this.gradleDistributionGroup = new GradleDistributionGroup(CorePlugin.publishedGradleVersions(), parent);
        GridDataFactory.swtDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).span(2, 1).applyTo(this.gradleDistributionGroup);
    }

    private void createGradleUserHomeGroup(Composite parent) {
        this.gradleUserHomeGroup = new GradleUserHomeGroup(parent);
        GridDataFactory.swtDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).span(2, 1).applyTo(this.gradleUserHomeGroup);
    }

    private void createOfflineModeCheckbox(Composite parent) {
        this.offlineModeCheckbox = new Button(parent, SWT.CHECK);
        this.offlineModeCheckbox.setText("Offline Mode");
        GridDataFactory.swtDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).span(2, 1).applyTo(this.offlineModeCheckbox);
    }

    private void createBuildScansCheckbox(Composite parent) {
        this.buildScansCheckbox = new Button(parent, SWT.CHECK);
        this.buildScansCheckbox.setText("Build Scans");
        GridDataFactory.swtDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).span(2, 1).applyTo(this.buildScansCheckbox);
        HoverText.createAndAttach(this.buildScansCheckbox, CoreMessages.Preference_Label_BuildScansHover);
    }

    private void createAutoBuildCheckbox(Composite parent) {
        this.autoBuildCheckbox = new Button(parent, SWT.CHECK);
        this.autoBuildCheckbox.setText("Automatic Refresh");
        GridDataFactory.swtDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).span(2, 1).applyTo(this.autoBuildCheckbox);
        HoverText.createAndAttach(this.autoBuildCheckbox, "Automatically synchronize project when build script changes");
    }

    private void addListeners() {
        if (this.overrideSettingsCheckbox != null) {
            this.overrideSettingsCheckbox.addSelectionListener(new SelectionListener() {

                @Override
                public void widgetSelected(SelectionEvent e) {
                    updateEnablement();
                }

                @Override
                public void widgetDefaultSelected(SelectionEvent e) {
                    updateEnablement();
                }
            });
        }
    }

    public void updateEnablement() {
        if (this.overrideSettingsCheckbox != null) {
            boolean enabled = this.overrideSettingsCheckbox.getSelection();
            this.gradleDistributionGroup.setEnabled(enabled);
            this.gradleUserHomeGroup.setEnabled(enabled);
            this.offlineModeCheckbox.setEnabled(enabled);
            this.buildScansCheckbox.setEnabled(enabled);
            if (this.autoBuildCheckbox != null) {
                this.autoBuildCheckbox.setEnabled(enabled);
            }
        }
    }

    @Override
    protected void checkSubclass() {
        // Disable the check that prevents subclassing of SWT components
    }

    public Button getOverrideBuildSettingsCheckbox() {
        return this.overrideSettingsCheckbox;
    }

    public Link getParentPreferenceLink() {
        return this.parentPreferenceLink;
    }

    public GradleDistributionGroup getGradleDistributionGroup() {
        return this.gradleDistributionGroup;
    }

    public GradleUserHomeGroup getGradleUserHomeGroup() {
        return this.gradleUserHomeGroup;
    }

    public Button getOfflineModeCheckbox() {
        return this.offlineModeCheckbox;
    }

    public Button getBuildScansCheckbox() {
        return this.buildScansCheckbox;
    }

    // TODO (donat) refactor factories and class hierarchy such that the autobuild checkbox is not available for the run configurations
    public Button getAutoBuildCheckbox() {
        return this.autoBuildCheckbox;
    }
    public static final GradleProjectSettingsCompositeBuilder builder(Composite parent) {
        return new GradleProjectSettingsCompositeBuilder(parent);
    }

    /**
     * Builder for building a {@link GradleProjectSettingsComposite}.
     *
     * @author Christopher Bryan Boyd
     *
     */
    public static class GradleProjectSettingsCompositeBuilder {
        private Maybe<Pair<String, String>> overrideCheckbox = Maybe.absent();
        private boolean autoRefreshCheckbox = false;
        private Composite parent;
        private GradleProjectSettingsCompositeBuilder(Composite parent) {
            this.parent = parent;
        }
        public GradleProjectSettingsCompositeBuilder withOverrideCheckbox(String overrideCheckboxLabel, String configureParentPrefsLinkLabel) {
            this.overrideCheckbox = Maybe.of(new Pair<>(overrideCheckboxLabel, configureParentPrefsLinkLabel));
            return this;
        }
        public GradleProjectSettingsCompositeBuilder withAutoRefreshCheckbox() {
            this.autoRefreshCheckbox = true;
            return this;
        }
        public GradleProjectSettingsComposite build() {
            if (this.overrideCheckbox.isPresent()) {
                return new GradleProjectSettingsComposite(this.parent, true, this.overrideCheckbox.get().getFirst(), this.overrideCheckbox.get().getSecond(), this.autoRefreshCheckbox);
            } else {
                return new GradleProjectSettingsComposite(this.parent, false, null, null, this.autoRefreshCheckbox);
            }
        }
    }

}
