/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.spi.connector.layout;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/**
 * The layout for a remote repository whose artifacts/metadata can be addressed via URIs. Implementations must be
 * stateless.
 */
public interface RepositoryLayout
{

    /**
     * A descriptor for a checksum file. This descriptor simply associates the location of a checksum file with the
     * underlying algorithm used to calculate/verify it. Checksum algorithms are denoted by names as used with
     * {@link java.security.MessageDigest#getInstance(String)}, e.g. {@code "SHA-1"} or {@code "MD5"}.
     */
    static final class Checksum
    {

        private final String algorithm;

        private final URI location;

        /**
         * Creates a new checksum file descriptor with the specified algorithm and location. The method
         * {@link #forLocation(URI, String)} is usually more convenient though.
         * 
         * @param algorithm The algorithm used to calculate the checksum, must not be {@code null}.
         * @param location The relative URI to the checksum file within a repository, must not be {@code null}.
         */
        public Checksum( String algorithm, URI location )
        {
            verify( algorithm, location );
            this.algorithm = algorithm;
            this.location = location;
        }

        /**
         * Creates a checksum file descriptor for the specified artifact/metadata location and algorithm. The location
         * of the checksum file itself is derived from the supplied resource URI by appending the file extension
         * corresponding to the algorithm. The file extension in turn is derived from the algorithm name by stripping
         * out any hyphen ('-') characters and lower-casing the name, e.g. "SHA-1" is mapped to ".sha1".
         * 
         * @param location The relative URI to the artifact/metadata whose checksum file is being obtained, must not be
         *            {@code null} and must not have a query or fragment part.
         * @param algorithm The algorithm used to calculate the checksum, must not be {@code null}.
         * @return The checksum file descriptor, never {@code null}.
         */
        public static Checksum forLocation( URI location, String algorithm )
        {
            verify( algorithm, location );
            if ( location.getRawQuery() != null )
            {
                throw new IllegalArgumentException( "resource location must not have query parameters: " + location );
            }
            if ( location.getRawFragment() != null )
            {
                throw new IllegalArgumentException( "resource location must not have a fragment: " + location );
            }
            String extension = '.' + algorithm.replace( "-", "" ).toLowerCase( Locale.ENGLISH );
            return new Checksum( algorithm, URI.create( location.toString() + extension ) );
        }

        private static void verify( String algorithm, URI location )
        {
            if ( algorithm == null || algorithm.length() <= 0 )
            {
                throw new IllegalArgumentException( "checksum algorithm has not been specified" );
            }
            if ( location == null )
            {
                throw new IllegalArgumentException( "checksum location has not been specified" );
            }
        }

        /**
         * Gets the name of the algorithm that is used to calculate the checksum.
         * 
         * @return The algorithm name, never {@code null}.
         * @see java.security.MessageDigest#getInstance(String)
         */
        public String getAlgorithm()
        {
            return algorithm;
        }

        /**
         * Gets the location of the checksum file with a remote repository. The URI is relative to the root directory of
         * the repository.
         * 
         * @return The relative URI to the checksum file, never {@code null}.
         */
        public URI getLocation()
        {
            return location;
        }

        @Override
        public String toString()
        {
            return location + " (" + algorithm + ")";
        }

    }

    /**
     * Gets the location within a remote repository where the specified artifact resides. The URI is relative to the
     * root directory of the repository.
     * 
     * @param artifact The artifact to get the URI for, must not be {@code null}.
     * @param upload {@code false} if the artifact is being downloaded, {@code true} if the artifact is being uploaded.
     * @return The relative URI to the artifact, never {@code null}.
     */
    URI getLocation( Artifact artifact, boolean upload );

    /**
     * Gets the location within a remote repository where the specified metadata resides. The URI is relative to the
     * root directory of the repository.
     * 
     * @param metadata The metadata to get the URI for, must not be {@code null}.
     * @param upload {@code false} if the metadata is being downloaded, {@code true} if the metadata is being uploaded.
     * @return The relative URI to the metadata, never {@code null}.
     */
    URI getLocation( Metadata metadata, boolean upload );

    /**
     * Gets the checksums files that a remote repository keeps to help detect data corruption during transfers of the
     * specified artifact.
     * 
     * @param artifact The artifact to get the checksum files for, must not be {@code null}.
     * @param upload {@code false} if the checksums are being downloaded/verified, {@code true} if the checksums are
     *            being uploaded/created.
     * @param location The relative URI to the artifact within the repository as previously obtained from
     *            {@link #getLocation(Artifact, boolean)}, must not be {@code null}.
     * @return The checksum files for the given artifact, possibly empty but never {@code null}.
     */
    List<Checksum> getChecksums( Artifact artifact, boolean upload, URI location );

    /**
     * Gets the checksums files that a remote repository keeps to help detect data corruption during transfers of the
     * specified metadata.
     * 
     * @param metadata The metadata to get the checksum files for, must not be {@code null}.
     * @param upload {@code false} if the checksums are being downloaded/verified, {@code true} if the checksums are
     *            being uploaded/created.
     * @param location The relative URI to the metadata within the repository as previously obtained from
     *            {@link #getLocation(Metadata, boolean)}, must not be {@code null}.
     * @return The checksum files for the given metadata, possibly empty but never {@code null}.
     */
    List<Checksum> getChecksums( Metadata metadata, boolean upload, URI location );

}
