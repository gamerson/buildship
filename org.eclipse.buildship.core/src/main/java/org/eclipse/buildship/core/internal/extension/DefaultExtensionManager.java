/*
 * Copyright (c) 2018 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.internal.extension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.invocation.InvocationCustomizer;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;

import com.google.common.annotations.VisibleForTesting;

public class DefaultExtensionManager implements ExtensionManager {

    @Override
    public List<InvocationCustomizer> loadCustomizers() {
        Collection<IConfigurationElement> elements = loadElements("invocationcustomizers");
        List<InvocationCustomizer> result = new ArrayList<>(elements.size());
        for (IConfigurationElement element : elements) {
            try {
                result.add(InvocationCustomizer.class.cast(element.createExecutableExtension("class")));
            } catch (Exception e) {
                CorePlugin.logger().warn("Cannot load invocationcustomizers extension", e);
            }
        }
        return result;
    }

    @Override
    public List<ProjectConfiguratorContribution> loadConfigurators() {
        Collection<IConfigurationElement> elements = loadElements("projectconfigurators");
        List<ProjectConfiguratorContribution> result = new ArrayList<>(elements.size());
        for (IConfigurationElement element : elements) {
            ProjectConfiguratorContribution contribution = ProjectConfiguratorContribution.from(element);
            result.add(contribution);
        }
        return result;
    }

    @VisibleForTesting
    Collection<IConfigurationElement> loadElements(String extensionPointName) {
        return Arrays.asList(Platform.getExtensionRegistry().getConfigurationElementsFor(CorePlugin.PLUGIN_ID, extensionPointName));
    }

    @Override
    public List<BuildExecutionParticipantContribution> loadBuildExecutionParticipants() {
        Collection<IConfigurationElement> elements = loadElements("executionparticipants");
        List<BuildExecutionParticipantContribution> result = new ArrayList<>(elements.size());
        for (IConfigurationElement element : elements) {
            BuildExecutionParticipantContribution contribution = BuildExecutionParticipantContribution.from(element);
            result.add(contribution);
        }
        return result;
    }
}
