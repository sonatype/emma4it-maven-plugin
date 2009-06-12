package org.sonatype.maven.plugin.emma4it;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.doxia.site.renderer.SiteRenderer;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;

import com.vladium.emma.IAppConstants;
import com.vladium.emma.report.ReportProcessor;
import com.vladium.util.XProperties;

/**
 * @author marvin
 * @goal emma4it-report
 * @requiresDependencyResolution test
 */
public class StandalonetReportMojo
    extends AbstractMavenReport
{

    /**
     * @parameter expression="${project}"
     */
    private MavenProject project;

    /** @component */
    private ArtifactFactory artifactFactory;

    /** @component */
    private ArtifactResolver resolver;

    /** @parameter expression="${localRepository}" */
    private ArtifactRepository localRepository;

    /** @parameter expression="${project.remoteArtifactRepositories}" */
    @SuppressWarnings( "unchecked" )
    private List remoteRepositories;

    /**
     * @component
     */
    private SiteRenderer siteRenderer;

    /**
     * @parameter default-value="${project.reporting.outputDirectory}/emma"
     */
    private File outputDirectory;

    /**
     * selects report type(s) to be generated: txt|html|xml
     *
     * @parameter
     */
    private String[] formats = new String[] { "html", "xml" };

    /**
     * Location of instrumentation files
     *
     * @parameter
     */
    private File[] instrumentations;

    /**
     * Report metadata files
     *
     * @parameter
     */
    private File[] metadatas;

    /**
     * @parameter
     */
    private ArtifactItem[] artifactItems;

    /**
     * @parameter
     */
    private File[] sourceFolders;

    /**
     * @component role="org.codehaus.plexus.archiver.manager.ArchiverManager"
     */
    private ArchiverManager archiverManager;

    @Override
    protected void executeReport( Locale locale )
        throws MavenReportException
    {
        validate();

        String[] dataPath = getDataPath();
        String[] sourcePath = getSourcePath();

        File emmaDir = new File( project.getBuild().getDirectory(), "/emma" );

        ReportProcessor reporter = ReportProcessor.create();
        reporter.setAppName( IAppConstants.APP_NAME );
        reporter.setDataPath( dataPath );
        reporter.setSourcePath( sourcePath );
        reporter.setReportTypes( formats );
        XProperties properties = new XProperties();
        properties.setProperty( "report.html.out.file", new File( outputDirectory, "index.html" ).getAbsolutePath() );
        properties.setProperty( "report.xml.out.file", new File( emmaDir, "coverage.xml" ).getAbsolutePath() );
        properties.setProperty( "report.txt.out.file", new File( emmaDir, "coverage.txt" ).getAbsolutePath() );
        reporter.setPropertyOverrides( properties );

        reporter.run();
    }

    private String[] getSourcePath()
        throws MavenReportException
    {
        List<String> sources = new ArrayList<String>();
        if ( artifactItems != null && artifactItems.length != 0 )
        {
            List<Artifact> artifacts = resolveArtifacts();
            for ( Artifact artifact : artifacts )
            {
                File outputDir = new File( project.getBuild().getDirectory() + "/emma", artifact.getArtifactId() );
                outputDir.mkdirs();

                try
                {
                    UnArchiver zipUnArchiver = archiverManager.getUnArchiver( artifact.getFile() );
                    zipUnArchiver.setSourceFile( artifact.getFile() );
                    zipUnArchiver.setDestDirectory( outputDir );
                    zipUnArchiver.extract();
                }
                catch ( ArchiverException e )
                {
                    throw new MavenReportException( "Unable to extract " + artifact.toString() + " sources.", e );
                }
                catch ( NoSuchArchiverException e )
                {
                    throw new MavenReportException( "Unable to extract " + artifact.toString() + " sources.", e );
                }
                sources.add( outputDir.getAbsolutePath() );
            }
        }

        if ( sourceFolders != null && sourceFolders.length != 0 )
        {
            for ( File sourceFolder : sourceFolders )
            {
                String path = sourceFolder.getAbsolutePath();
                if ( sourceFolder.exists() && sourceFolder.isDirectory() )
                {
                    sources.add( path );
                }
                else
                {
                    getLog().warn( "Source folder " + path + " not found!" );
                }
            }
        }

        return sources.toArray( new String[0] );
    }

    private List<Artifact> resolveArtifacts()
        throws MavenReportException
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
                // Not found, no problem
                getLog().warn( "Artifact " + artifact.toString() + " source no available at maven repository" );
            }
            artifacts.add( artifact );
        }
        return artifacts;
    }

    private String[] getDataPath()
    {
        List<String> dataPath = new ArrayList<String>();
        for ( File instrumentation : this.instrumentations )
        {
            dataPath.add( instrumentation.getAbsolutePath() );
        }
        for ( File metadata : this.metadatas )
        {
            dataPath.add( metadata.getAbsolutePath() );
        }

        return dataPath.toArray( new String[0] );
    }

    private void validate()
        throws MavenReportException
    {
        if ( instrumentations == null )
        {
            instrumentations = new File[] { new File( project.getBuild().getDirectory() + "/emma/coverage.em" ) };
        }
        for ( File instrumentation : instrumentations )
        {
            if ( !instrumentation.exists() )
            {
                throw new MavenReportException( "Intrumentation file " + instrumentation.getAbsolutePath()
                    + " not found." );
            }
        }

        if ( metadatas == null )
        {
            metadatas = new File[] { new File( project.getBuild().getDirectory() + "/emma/coverage.ec" ) };
        }
        for ( File metadata : metadatas )
        {
            if ( !metadata.exists() )
            {
                throw new MavenReportException( "Metadata file " + metadata.getAbsolutePath() + " not found." );
            }
        }

        if ( formats == null || formats.length == 0 )
        {
            throw new MavenReportException( "Format must be specify" );
        }
        for ( String format : formats )
        {
            if ( !"xml".equals( format ) && !"html".equals( format ) && !"txt".equals( format ) )
            {
                throw new MavenReportException( "Invalid format type: " + format );
            }
        }
    }

    @Override
    protected String getOutputDirectory()
    {
        return this.outputDirectory.getAbsolutePath();
    }

    @Override
    protected MavenProject getProject()
    {
        return this.project;
    }

    @Override
    protected SiteRenderer getSiteRenderer()
    {
        return this.siteRenderer;
    }

    @Override
    public boolean isExternalReport()
    {
        return true;
    }

    public String getDescription( Locale locale )
    {
        return "Emma Test Coverage Report";
    }

    public String getName( Locale locale )
    {
        return "Emma Report";
    }

    public String getOutputName()
    {
        return "emma/index";
    }

}
