package org.eclipse.aether.spi.connector.layout;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.net.URI;
import java.util.List;

import static java.util.Objects.requireNonNull;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.signature.SignatureAlgorithmFactory;

/**
 * The layout for a remote repository whose artifacts/metadata can be addressed via URIs.
 * <p>
 * <strong>Note:</strong> Implementations must be stateless.
 */
public interface RepositoryLayout
{

    /**
     * A descriptor for a checksum location. This descriptor simply associates the location of a checksum file with the
     * underlying checksum algorithm used to calculate/verify it.
     */
    final class ChecksumLocation
    {
        private final URI location;

        private final ChecksumAlgorithmFactory checksumAlgorithmFactory;

        /**
         * Creates a new checksum file descriptor with the specified algorithm and location. The method
         * {@link #forLocation(URI, ChecksumAlgorithmFactory)} is usually more convenient though.
         *
         * @param location                 The relative URI to the checksum file within a repository, must not be {@code
         *                                 null}.
         * @param checksumAlgorithmFactory The checksum type used to calculate the checksum, must not be {@code null}.
         */
        public ChecksumLocation( URI location, ChecksumAlgorithmFactory checksumAlgorithmFactory )
        {
            verify( location, checksumAlgorithmFactory );
            this.location = location;
            this.checksumAlgorithmFactory = checksumAlgorithmFactory;
        }

        /**
         * Creates a checksum descriptor for the specified artifact/metadata location and algorithm. The location
         * of the checksum file itself is derived from the supplied resource URI by appending the file extension
         * specified by the algorithm factory. See {@link ChecksumAlgorithmFactory#getFileExtension()}.
         *
         * @param location                 The relative URI to the artifact/metadata whose checksum file is being
         *                                 obtained, must not be
         *                                 {@code null} and must not have a query or fragment part.
         * @param checksumAlgorithmFactory The algorithm used to calculate the checksum, must not be {@code null}.
         * @return The checksum file descriptor, never {@code null}.
         */
        public static ChecksumLocation forLocation( URI location, ChecksumAlgorithmFactory checksumAlgorithmFactory )
        {
            verify( location, checksumAlgorithmFactory );
            if ( location.getRawQuery() != null )
            {
                throw new IllegalArgumentException( "resource location must not have query parameters: " + location );
            }
            if ( location.getRawFragment() != null )
            {
                throw new IllegalArgumentException( "resource location must not have a fragment: " + location );
            }
            return new ChecksumLocation( URI.create( location + "." + checksumAlgorithmFactory.getFileExtension() ),
                    checksumAlgorithmFactory );
        }

        private static void verify( URI location, ChecksumAlgorithmFactory checksumAlgorithmFactory )
        {
            requireNonNull( location, "checksum location cannot be null" );
            if ( location.isAbsolute() )
            {
                throw new IllegalArgumentException( "checksum location must be relative" );
            }
            requireNonNull( checksumAlgorithmFactory, "checksum algorithm factory cannot be null" );
        }

        /**
         * Gets the {@link ChecksumAlgorithmFactory} that is used to calculate the checksum.
         *
         * @return The checksum factory, never {@code null}.
         */
        public ChecksumAlgorithmFactory getChecksumAlgorithmFactory()
        {
            return checksumAlgorithmFactory;
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
            return location + " (" + checksumAlgorithmFactory.getName() + ")";
        }
    }

    /**
     * Returns immutable list of {@link ChecksumAlgorithmFactory} this instance of layout uses, never {@code null}.
     * The order represents the order how checksums are validated (hence retrieved).
     *
     * @since 1.8.0
     */
    List<ChecksumAlgorithmFactory> getChecksumAlgorithmFactories();

    /**
     * Returns immutable list of {@link SignatureAlgorithmFactory} this instance of layout uses, never {@code null}.
     *
     * @since 1.8.0
     */
    List<SignatureAlgorithmFactory> getSignatureAlgorithmFactories();

    /**
     * Gets the location within a remote repository where the specified artifact resides. The URI is relative to the
     * root directory of the repository.
     *
     * @param artifact The artifact to get the URI for, must not be {@code null}.
     * @param upload   {@code false} if the artifact is being downloaded, {@code true} if the artifact is being
     *                 uploaded.
     * @return The relative URI to the artifact, never {@code null}.
     */
    URI getLocation( Artifact artifact, boolean upload );

    /**
     * Gets the location within a remote repository where the specified metadata resides. The URI is relative to the
     * root directory of the repository.
     *
     * @param metadata The metadata to get the URI for, must not be {@code null}.
     * @param upload   {@code false} if the metadata is being downloaded, {@code true} if the metadata is being
     *                 uploaded.
     * @return The relative URI to the metadata, never {@code null}.
     */
    URI getLocation( Metadata metadata, boolean upload );

    /**
     * Gets the checksums files that a remote repository keeps to help detect data corruption during transfers of the
     * specified artifact.
     *
     * @param artifact The artifact to get the checksum files for, must not be {@code null}.
     * @param upload   {@code false} if the checksums are being downloaded/verified, {@code true} if the checksums are
     *                 being uploaded/created.
     * @param location The relative URI to the artifact within the repository as previously obtained from
     *                 {@link #getLocation(Artifact, boolean)}, must not be {@code null}.
     * @return The checksum files for the given artifact, possibly empty but never {@code null}. If empty, that means
     * that this layout does not provide checksums for given artifact.
     */
    List<ChecksumLocation> getChecksumLocations( Artifact artifact, boolean upload, URI location );

    /**
     * Gets the checksums files that a remote repository keeps to help detect data corruption during transfers of the
     * specified metadata.
     *
     * @param metadata The metadata to get the checksum files for, must not be {@code null}.
     * @param upload   {@code false} if the checksums are being downloaded/verified, {@code true} if the checksums are
     *                 being uploaded/created.
     * @param location The relative URI to the metadata within the repository as previously obtained from
     *                 {@link #getLocation(Metadata, boolean)}, must not be {@code null}.
     * @return The checksum files for the given metadata, possibly empty but never {@code null}. If empty, that means
     * that this layout does not provide checksums for given artifact.
     */
    List<ChecksumLocation> getChecksumLocations( Metadata metadata, boolean upload, URI location );

}
