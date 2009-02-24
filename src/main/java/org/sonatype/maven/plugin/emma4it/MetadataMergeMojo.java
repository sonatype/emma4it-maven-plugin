package org.sonatype.maven.plugin.emma4it;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import com.vladium.emma.IAppConstants;
import com.vladium.emma.merge.MergeProcessor;
import com.vladium.util.XProperties;

/**
 * @goal merge
 * @author marvin
 */
public class MetadataMergeMojo
    extends AbstractMojo
{

    /**
     * Location of the file.
     *
     * @parameter expression="${project}"
     * @required
     */
    private MavenProject project;

    /**
     * @parameter default-value="${project.build.testOutputDirectory}"
     */
    private File searchPath;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( !searchPath.isDirectory() )
        {
            throw new MojoExecutionException( "SearchPath " + searchPath + " not found." );
        }

        merge( "coverage.ec" );
        merge( "coverage.em" );
    }

    private void merge( String metadataFile )
    {
        getLog().info( "Merging " + metadataFile );
        String[] paths = getMetadataPaths( metadataFile );
        if ( paths.length == 0 )
        {
            getLog().error( "coverage.ec metadata not found." );
            return;
        }

        String output = project.getBuild().getDirectory() + "/emma/" + metadataFile;

        MergeProcessor processor = MergeProcessor.create();
        processor.setAppName( IAppConstants.APP_NAME ); // for log prefixing

        processor.setDataPath( paths );
        processor.setSessionOutFile( output );
        processor.setPropertyOverrides( new XProperties() );

        processor.run();
    }

    @SuppressWarnings( "unchecked" )
    private String[] getMetadataPaths( String metadataFile )
    {
        Collection<File> metadatas =
            FileUtils.listFiles( searchPath, new NameFileFilter( metadataFile ), TrueFileFilter.INSTANCE );
        List<String> paths = new ArrayList<String>();
        for ( File file : metadatas )
        {
            paths.add( file.getAbsolutePath() );
        }

        return paths.toArray( new String[0] );
    }

}
