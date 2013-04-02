package org.bsc.maven.plugin.processor;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 *
 * @author bsorrentino
 *
 * @threadSafe
 */
public abstract class AbstractAnnotationProcessorMojo extends AbstractMojo
{
    /**
     * @parameter expression = "${project}"
     * @readonly
     * @required
     */
    //@MojoParameter(expression = "${project}", readonly = true, required = true)
    protected MavenProject project;

    /**
     * @parameter expression="${plugin.artifacts}"
     * @readonly
     */
    //@MojoParameter(expression="${plugin.artifacts}", readonly = true )
    private java.util.List<Artifact> pluginArtifacts;

    /**
     * Specify the directory where to place generated source files (same behaviour of -s option)
     * @parameter
     *
     */
    //@MojoParameter(required = false, description = "Specify the directory where to place generated source files (same behaviour of -s option)")
    private File outputDirectory;

    /**
     * Annotation Processor FQN (Full Qualified Name) - when processors are not specified, the default discovery mechanism will be used
     * @parameter
     *
     */
    //@MojoParameter(required = false, description = "Annotation Processor FQN (Full Qualified Name) - when processors are not specified, the default discovery mechanism will be used")
    private String[] processors;

    /**
     * Additional compiler arguments
     * @parameter
     *
     */
    //@MojoParameter(required = false, description = "Additional compiler arguments")
    private String compilerArguments;

    /**
     * Additional processor options (see javax.annotation.processing.ProcessingEnvironment#getOptions()
     * @parameter alias="options"
     *
     */
    private java.util.Map<String,Object> optionMap;

    /**
     * Controls whether or not the output directory is added to compilation
     */
    //@MojoParameter(required = false, description = "Controls whether or not the output directory is added to compilation")
    private Boolean addOutputDirectoryToCompilationSources;

    /**
     * Indicates whether the build will continue even if there are compilation errors; defaults to true.
     * @parameter default-value="true"  expression = "${annotation.failOnError}"
     * @required
     */
    //@MojoParameter(required = true, defaultValue = "true", expression = "${annotation.failOnError}", description = "Indicates whether the build will continue even if there are compilation errors; defaults to true.")
    private Boolean failOnError = true;

    /**
     * Indicates whether the compiler output should be visible, defaults to true.
     *
     * @parameter expression = "${annotation.outputDiagnostics}" default-value="true"
     * @required
     */
    //@MojoParameter(required = true, defaultValue = "true", expression = "${annotation.outputDiagnostics}", description = "Indicates whether the compiler output should be visible, defaults to true.")
    private boolean outputDiagnostics = true;

    /**
     * System properties set before processor invocation.
     * @parameter
     *
     */
    //@MojoParameter(required = false, description = "System properties set before processor invocation.")
    private java.util.Map<String,String> systemProperties;

    /**
     * includes pattern
     * @parameter
     */
    //@MojoParameter( description="includes pattern")
    private String[] includes;

    /**
     * excludes pattern
     * @parameter
     */
    //@MojoParameter( description="excludes pattern")
    private String[] excludes;

    private static final Lock syncExecutionLock = new ReentrantLock();
    private File[] additionalSourceDirectories;
    private static boolean appendSourceArtifacts = false;

    private static String sourceClassifier = "sources";

    private List<File> sourceArtifacts = new ArrayList();

    public File[] getAdditionalSourceDirectories()
    {
        if (this.additionalSourceDirectories == null) {
            this.additionalSourceDirectories = new File[0];
        }
        return this.additionalSourceDirectories;
    }

    protected abstract Set<File> getSourceDirectories();

    protected abstract File getOutputClassDirectory();

    protected abstract void addCompileSourceRoot(MavenProject paramMavenProject, String paramString);

    public abstract File getDefaultOutputDirectory();

    private String buildProcessor()
    {
        if ((this.processors == null) || (this.processors.length == 0))
        {
            return null;
        }

        StringBuilder result = new StringBuilder();

        int i = 0;

        for (i = 0; i < this.processors.length - 1; i++)
        {
            result.append(this.processors[i]).append(',');
        }

        result.append(this.processors[i]);

        return result.toString();
    }

    protected abstract Set<String> getClasspathElements(Set<String> paramSet);

    private String buildCompileClasspath()
    {
        Set<String> pathElements = new LinkedHashSet<String>();

        if (this.pluginArtifacts != null)
        {
            for (Artifact a : this.pluginArtifacts)
            {
                if (("compile".equalsIgnoreCase(a.getScope())) || ("runtime".equalsIgnoreCase(a.getScope())))
                {
                    File f = a.getFile();

                    if (f != null) pathElements.add(a.getFile().getAbsolutePath());
                }
            }

        }

        getClasspathElements(pathElements);

        StringBuilder result = new StringBuilder();

        for (String elem : pathElements) {
            result.append(elem).append(File.pathSeparator);
        }
        return result.toString();
    }

    public void execute()
            throws MojoExecutionException
    {
        if ("pom".equalsIgnoreCase(this.project.getPackaging()))
        {
            return;
        }

        syncExecutionLock.lock();
        try
        {
            executeWithExceptionsHandled();
        }
        catch (Exception e1)
        {
            super.getLog().error("error on execute: " + e1.getMessage());
            if (this.failOnError.booleanValue())
            {
                throw new MojoExecutionException("Error executing", e1);
            }
        }
        finally {
            syncExecutionLock.unlock();
        }
    }

    private void executeWithExceptionsHandled()
            throws Exception
    {
        if (this.outputDirectory == null)
        {
            this.outputDirectory = getDefaultOutputDirectory();
        }

        ensureOutputDirectoryExists();
        addOutputToSourcesIfNeeded();

        String includesString = (this.includes == null) || (this.includes.length == 0) ? "**/*.java" : StringUtils.join(this.includes, ",");
        String excludesString = (this.excludes == null) || (this.excludes.length == 0) ? null : StringUtils.join(this.excludes, ",");

        Set<File> sourceDirs = getSourceDirectories();
        if (sourceDirs == null) throw new IllegalStateException("getSourceDirectories is null!");

        if ((this.additionalSourceDirectories != null) && (this.additionalSourceDirectories.length > 0)) {
            sourceDirs.addAll(Arrays.asList((File[])this.additionalSourceDirectories));
        }

        List<File> files = new ArrayList();

        for (File sourceDir : sourceDirs)
        {
            getLog().debug(String.format("processing source directory [%s]", new Object[] { sourceDir.getPath() }));

            if (sourceDir == null) {
                getLog().warn("source directory is null! Processor task will be skipped!");
            }
            else if (!sourceDir.exists()) {
                getLog().warn(String.format("source directory [%s] doesn't exist! Processor task will be skipped!", new Object[] { sourceDir.getPath() }));
            }
            else if (!sourceDir.isDirectory()) {
                getLog().warn(String.format("source directory [%s] is invalid! Processor task will be skipped!", new Object[] { sourceDir.getPath() }));
            }
            else
            {
                files.addAll(FileUtils.getFiles(sourceDir, includesString, excludesString));
            }
        }

        if (appendSourceArtifacts) {
            processSourceArtifacts();
        }
        Iterable<? extends JavaFileObject> compilationUnits1 = null;

        String compileClassPath = buildCompileClasspath();

        String processor = buildProcessor();

        List<String> options = new ArrayList(10);

        options.add("-cp");
        options.add(compileClassPath);
        options.add("-proc:only");

        addCompilerArguments(options);

        if (processor != null)
        {
            options.add("-processor");
            options.add(processor);
        }
        else
        {
            getLog().info("No processors specified. Using default discovery mechanism.");
        }
        options.add("-d");
        options.add(getOutputClassDirectory().getPath());

        options.add("-s");
        options.add(this.outputDirectory.getPath());

        for (String option : options)
        {
            getLog().info("javac option: " + option);
        }

        DiagnosticListener dl = null;
        if (this.outputDiagnostics)
        {
            dl = new DiagnosticListener()
            {

                public void report(Diagnostic diagnostic) {

                }

                /*public void report(Diagnostic<? extends JavaFileObject> diagnostic)
                {
                    AbstractAnnotationProcessorMojo.this.getLog().info("diagnostic " + diagnostic);
                } */

            };
        }
        else
        {
            dl = new DiagnosticListener()
            {
                public void report(Diagnostic diagnostic) {

                }
            };
        }

        if (this.systemProperties != null)
        {
            Set<Map.Entry<String, String>> pSet = this.systemProperties.entrySet();

            for (Map.Entry<String, String> e : pSet)
            {
                getLog().info(String.format("set system property : [%s] = [%s]", new Object[] { e.getKey(), e.getValue() }));
                System.setProperty((String)e.getKey(), (String)e.getValue());
            }

        }

        try
        {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

            if (compiler == null) {
                getLog().error("JVM is not suitable for processing annotation! ToolProvider.getSystemJavaCompiler() is null.");
                return;
            }

            List zipSources = new ArrayList();
            for (File f : this.sourceArtifacts)
            {
                ZipFile zipFile = new ZipFile(f);
                Enumeration entries = zipFile.entries();
                int i = 0;

                while (entries.hasMoreElements())
                {
                    ZipEntry entry = (ZipEntry)entries.nextElement();

                    if (entry.getName().endsWith(".java"))
                    {
                        i++;
                        zipSources.add(ZipFileObject.create(zipFile, entry));
                    }
                }

                System.out.println("** Discovered " + i + " java sources in " + f.getAbsolutePath());
            }

            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

            if ((files != null) && (!files.isEmpty())) {
                compilationUnits1 = fileManager.getJavaFileObjectsFromFiles(files);
            }
            else if (zipSources.isEmpty())
            {
                getLog().warn("no source file(s) detected! Processor task will be skipped");
                return;
            }

            List allSources = new ArrayList();
            allSources.addAll(zipSources);

            if (compilationUnits1 != null)
            {
                for (JavaFileObject fileObject : compilationUnits1) {
                    allSources.add(fileObject);
                }
            }
            JavaCompiler.CompilationTask task1 = compiler.getTask(new PrintWriter(System.out), fileManager, dl, options, null, allSources);

            if (!task1.call().booleanValue())
            {
                throw new Exception("error compiling zip sources");
            }
        }
        finally
        {
        }
    }

    private void processSourceArtifacts()
    {
        for (Artifact dep : this.project.getDependencyArtifacts())
        {
            if ((dep.hasClassifier()) && (dep.getClassifier().equals(sourceClassifier)))
            {
                getLog().debug("Append source artifact to classpath: " + dep.getGroupId() + ":" + dep.getArtifactId());
                this.sourceArtifacts.add(dep.getFile());
            }
        }
    }

    private List<File> scanSourceDirectorySources(File sourceDir) throws IOException {
        if (sourceDir == null) {
            getLog().warn("source directory cannot be read (null returned)! Processor task will be skipped");
            return null;
        }
        if (!sourceDir.exists()) {
            getLog().warn("source directory doesn't exist! Processor task will be skipped");
            return null;
        }
        if (!sourceDir.isDirectory()) {
            getLog().warn("source directory is invalid! Processor task will be skipped");
            return null;
        }

        String includesString = (this.includes == null) || (this.includes.length == 0) ? "**/*.java" : StringUtils.join(this.includes, ",");
        String excludesString = (this.excludes == null) || (this.excludes.length == 0) ? null : StringUtils.join(this.excludes, ",");

        List files = FileUtils.getFiles(sourceDir, includesString, excludesString);
        return files;
    }

    private void addCompilerArguments(List<String> options)
    {
        if (!StringUtils.isEmpty(this.compilerArguments))
        {
            for (String arg : this.compilerArguments.split(" "))
            {
                if (!StringUtils.isEmpty(arg))
                {
                    arg = arg.trim();
                    getLog().info("Adding compiler arg: " + arg);
                    options.add(arg);
                }
            }
        }
        if ((this.optionMap != null) && (!this.optionMap.isEmpty()))
            for (Map.Entry e : this.optionMap.entrySet())
            {
                if ((!StringUtils.isEmpty((String)e.getKey())) && (e.getValue() != null)) {
                    String opt = String.format("-A%s=%s", new Object[] { ((String)e.getKey()).trim(), e.getValue().toString().trim() });
                    options.add(opt);
                    getLog().info("Adding compiler arg: " + opt);
                }
            }
    }

    private void addOutputToSourcesIfNeeded()
    {
        Boolean add = this.addOutputDirectoryToCompilationSources;
        if ((add == null) || (add.booleanValue()))
        {
            getLog().info("Source directory: " + this.outputDirectory + " added");
            addCompileSourceRoot(this.project, this.outputDirectory.getAbsolutePath());
        }
    }

    private void ensureOutputDirectoryExists()
    {
        File f = this.outputDirectory;
        if (!f.exists())
        {
            f.mkdirs();
        }
        if (!getOutputClassDirectory().exists())
            getOutputClassDirectory().mkdirs();
    }
}