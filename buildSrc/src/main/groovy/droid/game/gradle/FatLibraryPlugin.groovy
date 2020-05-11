package droid.game.gradle

import com.android.build.gradle.api.LibraryVariant
import droid.game.gradle.fataar.VariantProcessor
import droid.game.gradle.fataar.Utils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency

/**
 * plugin entry
 *
 * Created by Vigi on 2017/1/14.
 * Modified by kezong on 2018/12/18
 */
class FatLibraryPlugin implements Plugin<Project> {

    public static final String ARTIFACT_TYPE_AAR = 'aar'

    public static final String ARTIFACT_TYPE_JAR = 'jar'

    private Project project

    private Configuration embedConf

    private Set<ResolvedArtifact> artifacts

    private Set<ResolvedDependency> unResolveArtifact

    @Override
    void apply(Project project) {
        this.project = project
        Utils.setProject(project)
        checkAndroidPlugin()
        createConfiguration()
        project.afterEvaluate {
            resolveArtifacts()
            dealUnResolveArtifacts()
            project.android.libraryVariants.all { variant ->
                processVariant(variant)
            }
        }

    }

    private void checkAndroidPlugin() {
        if (!project.plugins.hasPlugin('com.android.library')) {
            throw new ProjectConfigurationException('fat-aar-plugin must be applied in project that' +
                    ' has android library plugin!', null)
        }
    }

    private void createConfiguration() {
        embedConf = project.configurations.create('embed')
        embedConf.visible = false
        embedConf.transitive = false

        project.gradle.addListener(new DependencyResolutionListener() {
            @Override
            void beforeResolve(ResolvableDependencies resolvableDependencies) {
                embedConf.dependencies.each { dependency ->
                    project.dependencies.add('compileOnly', dependency)
                }
                project.gradle.removeListener(this)
            }

            @Override
            void afterResolve(ResolvableDependencies resolvableDependencies) {
            }
        })
    }

    private void resolveArtifacts() {
        def set = new HashSet<>()
        embedConf.resolvedConfiguration.resolvedArtifacts.each { artifact ->
            // jar file wouldn't be here
            if (ARTIFACT_TYPE_AAR == artifact.type || ARTIFACT_TYPE_JAR == artifact.type) {
                Utils.logAnytime('[embed detected][' + artifact.type + ']' + artifact.moduleVersion.id)
            } else {
                throw new ProjectConfigurationException('Only support embed aar and jar dependencies!', null)
            }
            set.add(artifact)
        }
        artifacts = Collections.unmodifiableSet(set)
    }

    private void processVariant(LibraryVariant variant) {
        def processor = new VariantProcessor(project, variant)
        processor.addArtifacts(artifacts)
        processor.addUnResolveArtifact(unResolveArtifact)
        processor.processVariant()
    }

    private void dealUnResolveArtifacts() {
        def dependencies = Collections.unmodifiableSet(embedConf.resolvedConfiguration.firstLevelModuleDependencies)
        def dependencySet = new HashSet()
        dependencies.each { dependency ->
            boolean match = false
            artifacts.each { artifact ->
                if (dependency.moduleName == artifact.name) {
                    match = true
                }
            }
            if (!match) {
                Utils.logAnytime('[unResolve dependency detected][' + dependency.name + ']')
                dependencySet.add(dependency)
            }
        }
        unResolveArtifact = Collections.unmodifiableSet(dependencySet)
    }
}