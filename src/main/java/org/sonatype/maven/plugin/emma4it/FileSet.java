package org.sonatype.maven.plugin.emma4it;

/*
 * PUT YOUR LICENSE HEADER HERE. 
 */

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.codehaus.plexus.util.DirectoryScanner;

/**
 * A bean for the mojo configuration to hold the specification of an Ant-like fileset. This class is very similar to the
 * equally named one from maven-model but supports Maven's path translation.
 * 
 * @author Benjamin Bentmann
 * @version $Id$
 */
public class FileSet
{

    /**
     * The base directory of the fileset, should not be {@code null}.
     */
    private File directory;

    /**
     * The Ant-like inclusion patterns for the fileset, maybe {@code null} to include all files.
     */
    private String[] includes;

    /**
     * The Ant-like exclusion patterns for the fileset, may be {@code null} to exclude no files.
     */
    private String[] excludes;

    /**
     * Creates a new data set.
     */
    public FileSet()
    {
        // enables no-arg constructor
    }

    /**
     * Creates a new data set.
     * 
     * @param directory The base directory of the fileset, should not be {@code null}.
     * @param includes The Ant-like inclusion patterns for the fileset, maybe {@code null} to include all files.
     * @param excludes The Ant-like exclusion patterns for the fileset, may be {@code null} to exclude no files.
     */
    public FileSet( File directory, String[] includes, String[] excludes )
    {
        this.directory = directory;
        this.includes = includes;
        this.excludes = excludes;
    }

    /**
     * Gets the base directory of the fileset.
     * 
     * @return The base directory of the fileset or {@code null} if unset.
     */
    public File getDirectory()
    {
        return directory;
    }

    /**
     * Gets the Ant-like inclusion patterns for the fileset.
     * 
     * @return The Ant-like inclusion patterns for the fileset or {@code null} to include all files.
     */
    public String[] getIncludes()
    {
        return includes;
    }

    /**
     * Gets the Ant-like exclusion patterns for the fileset, may be {@code null} to exclude no files.
     * 
     * @return The Ant-like exclusion patterns for the fileset or {@code null} to exclude no files.
     */
    public String[] getExcludes()
    {
        return excludes;
    }

    /**
     * Collects the absolute paths to the files matched by this file set.
     * 
     * @param files A flag whether to include normal files in the result collection.
     * @param dirs A flag whether to include directories in the result collection.
     * @return The set of matched files or an empty set if the base directory does not exist, never {@code null}.
     * @throws IllegalStateException If the base directory has not been set.
     */
    public Collection<String> scan( boolean files, boolean dirs )
    {
        if ( getDirectory() == null )
        {
            throw new IllegalStateException( "The base directory has not been set" );
        }

        Collection<String> paths = new ArrayList<String>();

        if ( getDirectory().isDirectory() )
        {
            DirectoryScanner scanner = new DirectoryScanner();

            scanner.setBasedir( getDirectory() );

            scanner.setIncludes( getIncludes() );
            scanner.setExcludes( getExcludes() );
            scanner.addDefaultExcludes();

            scanner.scan();

            if ( files )
            {
                for ( String path : scanner.getIncludedFiles() )
                {
                    paths.add( new File( scanner.getBasedir(), path ).getAbsolutePath() );
                }
            }

            if ( dirs )
            {
                for ( String path : scanner.getIncludedDirectories() )
                {
                    paths.add( new File( scanner.getBasedir(), path ).getAbsolutePath() );
                }
            }
        }

        return paths;
    }

    @Override
    public String toString()
    {
        StringBuilder buffer = new StringBuilder( 256 );
        buffer.append( "FileSet[" );
        buffer.append( "directory=" ).append( directory );
        buffer.append( ", includes=" ).append( ( includes == null ) ? includes : Arrays.asList( includes ) );
        buffer.append( ", excludes=" ).append( ( excludes == null ) ? excludes : Arrays.asList( excludes ) );
        buffer.append( "]" );
        return buffer.toString();
    }

}
