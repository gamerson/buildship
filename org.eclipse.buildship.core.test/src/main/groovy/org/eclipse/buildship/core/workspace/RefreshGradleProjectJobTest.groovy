package org.eclipse.buildship.core.workspace

import org.eclipse.buildship.core.CorePlugin
import org.eclipse.buildship.core.configuration.GradleProjectNature
import org.eclipse.buildship.core.test.fixtures.ProjectImportSpecification
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IProjectDescription
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.JavaCore

class RefreshGradleProjectJobTest extends ProjectImportSpecification {

    def setup() {
        executeProjectImportAndWait(createSampleProject())
    }

    def "Updates the dependency list" () {
        setup:
        file('sample', 'moduleA', 'build.gradle').text = """apply plugin: 'java'
           dependencies { testCompile 'junit:junit:4.12' }
        """

        when:
        new RefreshGradleProjectsJob().schedule()
        waitForGradleJobsToFinish()

        then:
        JavaCore.create(findProject('moduleA')).resolvedClasspath.find{ it.path.toPortableString().endsWith('junit-4.12.jar') }
    }

    def "Gradle nature and Gradle settings file are discarded when the project is excluded from a Gradle build"() {
        setup:
        file('sample', 'settings.gradle').text = """
           include 'moduleA'
        """

        when:
        new RefreshGradleProjectsJob().schedule()
        waitForGradleJobsToFinish()

        then:
        IProject project = findProject('moduleB')
        project != null
        !GradleProjectNature.INSTANCE.isPresentOn(project)
        project.getFolder('.settings').exists()
        !project.getFolder('.settings').getFile('gradle.prefs').exists()
    }

    def "A new Gradle module is imported into the workspace"() {
        setup:
        file('sample', 'settings.gradle').text = """
           include 'moduleA'
           include 'moduleB'
           include 'moduleC'
        """
        file('sample', 'moduleC', 'build.gradle') << "apply plugin: 'java'"
        folder('sample', 'moduleC', 'src', 'main', 'java')

        when:
        new RefreshGradleProjectsJob().schedule()
        waitForGradleJobsToFinish()

        then:
        IProject project = findProject('moduleC')
        project != null
        GradleProjectNature.INSTANCE.isPresentOn(project)
    }

    def "Project is transformed to a Gradle project when included in a Gradle build"() {
        setup:
        file('sample', 'settings.gradle').text = """
           include 'moduleA'
           include 'moduleB'
           include 'moduleC'
        """
        folder('sample', 'moduleC', 'src', 'main', 'java')
        file('sample', 'moduleC', '.project') <<
        '''<?xml version="1.0" encoding="UTF-8"?>
            <projectDescription>
              <name>moduleC</name>
              <comment>original</comment>
              <projects></projects>
              <buildSpec></buildSpec>
              <natures></natures>
            </projectDescription>
        '''
        IProjectDescription description = CorePlugin.workspaceOperations().findProjectInFolder(folder('sample', 'moduleC'), new NullProgressMonitor()).get()
        IProject project = workspace.root.getProject(description.getName());
        project.create(description, null);
        project.open(IResource.BACKGROUND_REFRESH, null);
        waitForGradleJobsToFinish()

        when:
        new RefreshGradleProjectsJob().schedule()
        waitForGradleJobsToFinish()

        then:
        GradleProjectNature.INSTANCE.isPresentOn(project)
    }

    private def createSampleProject() {
        file('sample', 'build.gradle') <<
        '''allprojects {
               repositories { mavenCentral() }
               apply plugin: 'java'
           }
        '''
        file('sample', 'settings.gradle') <<
        """
           include 'moduleA'
           include 'moduleB'
        """
        file('sample', 'moduleA', 'build.gradle') << "apply plugin: 'java'"
        folder('sample', 'moduleA', 'src', 'main', 'java')
        file('sample', 'moduleB', 'build.gradle') << "apply plugin: 'java'"
        folder('sample', 'moduleB', 'src', 'main', 'java')
        folder('sample')
    }

}
