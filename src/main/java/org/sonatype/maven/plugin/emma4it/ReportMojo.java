package org.sonatype.maven.plugin.emma4it;

/*
 * PUT YOUR LICENSE HEADER HERE.
 */

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;

import com.vladium.emma.IAppConstants;
import com.vladium.emma.report.ReportProcessor;
import com.vladium.util.XProperties;

/**
 * Generates human-readable reports from the previously collected coverage data. <strong>Note:</strong> Unlike the
 * related goal <code>emma4it</code>, this goal is not meant to participate in the site lifecycle but in the normal
 * build lifecycle after the tests have been run.
 *
 * @goal report
 * @phase test
 * @since 1.2
 * @author Benjamin Bentmann
 * @version $Id$
 */
public class ReportMojo
    extends AbstractMojo
{

    /**
     * @component
     */
    private ArtifactFactory artifactFactory;

    /**
     * @component
     */
    private ArtifactResolver resolver;

    /**
     * @parameter default-value="${localRepository}"
     * @readonly
     */
    private ArtifactRepository localRepository;

    /**
     * @parameter default-value="${project.remoteArtifactRepositories}"
     * @readonly
     */
    private List<? extends ArtifactRepository> remoteRepositories;

    /**
     * The project's base directory.
     *
     * @parameter default-value="${basedir}"
     * @readonly
     */
    private File baseDirectory;

    /**
     * The project's build directory.
     *
     * @parameter default-value="${project.build.directory}"
     * @readonly
     */
    private File buildDirectory;

    /**
     * The location of the generated report files.
     *
     * @parameter default-value="${project.reporting.outputDirectory}/emma"
     */
    private File reportDirectory;

    /**
     * The location to expand source attachments to.
     *
     * @parameter default-value="${project.build.directory}/emma"
     */
    private File sourcesDirectory;

    /**
     * The (case-sensitive) names of the reports to be generated. Supported reports are <code>txt</code>,
     * <code>xml</code> and <code>html</code>. Defaults to <code>txt</code>, <code>xml</code> and <code>html</code>.
     *
     * @parameter
     */
    private String[] formats;

    /**
     * The offline metadata and runtime coverage files to generate the reports from. Defaults to all <code>*.ec</code>,
     * <code>*.em</code> and <code>*.es</code> files within the project base directory and within any subdirectory of
     * the build directory.
     *
     * @parameter
     */
    private FileSet[] dataSets;

    /**
     * An optional collection of source directories to be used for the HTML report.
     *
     * @parameter
     */
    private FileSet[] sourceSets;

    /**
     * An optional collection of artifacts whose source attachments should be resolved for the HTML report.
     *
     * @parameter
     */
    private ArtifactItem[] artifactItems;

    /**
     * @component role="org.codehaus.plexus.archiver.manager.ArchiverManager"
     */
    private ArchiverManager archiverManager;

    /**
     * Executes this mojo.
     *
     * @throws MojoExecutionException If the reports could not be generated.
     */
    public void execute()
        throws MojoExecutionException
    {
        String[] formats = getFormats();
        String[] dataPath = collectDataPath();
        String[] sourcePath = collectSourcePath();

        XProperties properties = new XProperties();
        properties.setProperty( "report.html.out.file", new File( reportDirectory, "index.html" ).getAbsolutePath() );
        properties.setProperty( "report.xml.out.file", new File( reportDirectory, "coverage.xml" ).getAbsolutePath() );
        properties.setProperty( "report.txt.out.file", new File( reportDirectory, "coverage.txt" ).getAbsolutePath() );
        properties.setProperty( "report.sort", getSortOrder() );
        properties.setProperty( "report.out.encoding", "UTF-8" );
        properties.setProperty( "report.xml.out.encoding", "UTF-8" );
        properties.setProperty( "report.html.out.encoding", "UTF-8" );

        ReportProcessor reporter = ReportProcessor.create();
        reporter.setAppName( IAppConstants.APP_NAME );
        reporter.setDataPath( dataPath );
        reporter.setSourcePath( sourcePath );
        try
        {
            reporter.setReportTypes( formats );
        }
        catch ( RuntimeException e )
        {
            throw new MojoExecutionException( "Unsupported report format", e );
        }
        reporter.setPropertyOverrides( properties );

        reporter.run();
    }

    private String[] getFormats()
    {
        if ( formats == null || formats.length <= 0 )
        {
            return new String[] { "txt", "xml", "html" };
        }
        return formats;
    }

    private String getSortOrder()
    {
        return "+name,+block,+method,+class";
    }

    private String[] collectDataPath()
        throws MojoExecutionException
    {
        getLog().debug( "Collecting data files" );

        FileSet[] dataSets = this.dataSets;

        if ( dataSets == null || dataSets.length <= 0 )
        {
            dataSets = new FileSet[2];
            dataSets[0] = new FileSet( baseDirectory, new String[] { "*.ec", "*.em", "*.es" }, null );
            dataSets[1] = new FileSet( buildDirectory, new String[] { "**/*.ec", "**/*.em", "**/*.es" }, null );
        }

        List<String> dataPath = new ArrayList<String>();

        for ( FileSet fileSet : dataSets )
        {
            if ( fileSet.getDirectory() == null )
            {
                throw new MojoExecutionException( "Missing base directory for data set " + fileSet );
            }
            else if ( !fileSet.getDirectory().isDirectory() )
            {
                getLog().warn( "Ignored non-existing data set directory " + fileSet.getDirectory() );
            }
            else
            {
                dataPath.addAll( fileSet.scan( true, false ) );
            }
        }

        if ( getLog().isDebugEnabled() )
        {
            for ( String path : dataPath )
            {
                getLog().debug( "  " + path );
            }
        }

        return dataPath.toArray( new String[dataPath.size()] );
    }

    private String[] collectSourcePath()
        throws MojoExecutionException
    {
        getLog().debug( "Collecting source directories" );

        List<String> sourcePath = new ArrayList<String>();

        if ( artifactItems != null && artifactItems.length > 0 )
        {
            List<Artifact> artifacts = resolveArtifacts();
            for ( Artifact artifact : artifacts )
            {
                File outputDir = new File( sourcesDirectory, artifact.getArtifactId() );
                try
                {
                    outputDir.mkdirs();

                    UnArchiver zipUnArchiver = archiverManager.getUnArchiver( artifact.getFile() );
                    zipUnArchiver.setSourceFile( artifact.getFile() );
                    zipUnArchiver.setDestDirectory( outputDir );
                    zipUnArchiver.extract();
                    sourcePath.add( outputDir.getAbsolutePath() );
                }
                catch ( ArchiverException e )
                {
                    getLog().warn( "Unable to extract " + artifact.toString() + " sources.", e );
                }
                catch ( NoSuchArchiverException e )
                {
                    getLog().warn( "Unable to extract " + artifact.toString() + " sources.", e );
                }
            }
        }

        if ( sourceSets != null && sourceSets.length > 0 )
        {
            for ( FileSet fileSet : sourceSets )
            {
                if ( fileSet.getDirectory() == null )
                {
                    throw new MojoExecutionException( "Missing base directory for source set " + fileSet );
                }
                else if ( !fileSet.getDirectory().isDirectory() )
                {
                    getLog().warn( "Ignored non-existing source set directory " + fileSet.getDirectory() );
                }
                else
                {
                    sourcePath.addAll( fileSet.scan( false, true ) );
                }
            }
        }

        if ( getLog().isDebugEnabled() )
        {
            for ( String path : sourcePath )
            {
                getLog().debug( "  " + path );
            }
        }

        return sourcePath.toArray( new String[sourcePath.size()] );
    }

    private List<Artifact> resolveArtifacts()
    {
        List<Artifact> artifacts = new ArrayList<Artifact>();
        for ( ArtifactItem artifactItem : artifactItems )
        {
            Artifact artifact =
                artifactFactory.createArtifactWithClassifier( artifactItem.getGroupId(), artifactItem.getArtifactId(),
                                                              artifactItem.getVersion(), artifactItem.getType(),
                                                              "sources" );
            try
            {
                resolver.resolve( artifact, remoteRepositories, localRepository );
            }
            catch ( AbstractArtifactResolutionException e )
            {
                getLog().warn( "Artifact " + artifact.toString() + " source not available at Maven repository" );
            }
            artifacts.add( artifact );
        }
        return artifacts;
    }

}
