/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.UnsolvableVersionConflictException;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.junit.Test;

public class RepositoryExceptionTest {

    private void assertSerializable(RepositoryException e) {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new ByteArrayOutputStream());
            oos.writeObject(e);
            oos.close();
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    private RequestTrace newTrace() {
        return new RequestTrace("test");
    }

    private Artifact newArtifact() {
        return new DefaultArtifact("gid", "aid", "ext", "1");
    }

    private Metadata newMetadata() {
        return new DefaultMetadata("maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
    }

    private RemoteRepository newRepo() {
        Proxy proxy = new Proxy(Proxy.TYPE_HTTP, "localhost", 8080, null);
        return new RemoteRepository.Builder("id", "test", "http://localhost")
                .setProxy(proxy)
                .build();
    }

    @Test
    public void testArtifactDescriptorException_Serializable() {
        ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();
        request.setArtifact(newArtifact()).addRepository(newRepo()).setTrace(newTrace());
        ArtifactDescriptorResult result = new ArtifactDescriptorResult(request);
        assertSerializable(new ArtifactDescriptorException(result));
    }

    @Test
    public void testArtifactResolutionException_Serializable() {
        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(newArtifact()).addRepository(newRepo()).setTrace(newTrace());
        ArtifactResult result = new ArtifactResult(request);
        assertSerializable(new ArtifactResolutionException(Arrays.asList(result)));
    }

    @Test
    public void testArtifactTransferException_Serializable() {
        assertSerializable(new ArtifactTransferException(newArtifact(), newRepo(), "error"));
    }

    @Test
    public void testArtifactNotFoundException_Serializable() {
        assertSerializable(new ArtifactNotFoundException(newArtifact(), newRepo(), "error"));
    }

    @Test
    public void testDependencyCollectionException_Serializable() {
        CollectRequest request = new CollectRequest();
        request.addDependency(new Dependency(newArtifact(), "compile"));
        request.addRepository(newRepo());
        request.setTrace(newTrace());
        CollectResult result = new CollectResult(request);
        assertSerializable(new DependencyCollectionException(result));
    }

    @Test
    public void testDependencyResolutionException_Serializable() {
        CollectRequest request = new CollectRequest();
        request.addDependency(new Dependency(newArtifact(), "compile"));
        request.addRepository(newRepo());
        request.setTrace(newTrace());
        DependencyRequest req = new DependencyRequest();
        req.setTrace(newTrace());
        req.setCollectRequest(request);
        DependencyResult result = new DependencyResult(req);
        assertSerializable(new DependencyResolutionException(result, null));
    }

    @Test
    public void testMetadataTransferException_Serializable() {
        assertSerializable(new MetadataTransferException(newMetadata(), newRepo(), "error"));
    }

    @Test
    public void testMetadataNotFoundException_Serializable() {
        assertSerializable(new MetadataNotFoundException(newMetadata(), newRepo(), "error"));
    }

    @Test
    public void testNoLocalRepositoryManagerException_Serializable() {
        assertSerializable(new NoLocalRepositoryManagerException(new LocalRepository("/tmp")));
    }

    @Test
    public void testNoRepositoryConnectorException_Serializable() {
        assertSerializable(new NoRepositoryConnectorException(newRepo()));
    }

    @Test
    public void testNoRepositoryLayoutException_Serializable() {
        assertSerializable(new NoRepositoryLayoutException(newRepo()));
    }

    @Test
    public void testNoTransporterException_Serializable() {
        assertSerializable(new NoTransporterException(newRepo()));
    }

    @Test
    public void testRepositoryOfflineException_Serializable() {
        assertSerializable(new RepositoryOfflineException(newRepo()));
    }

    @Test
    public void testUnsolvableVersionConflictException_Serializable() {
        DependencyNode node = new DefaultDependencyNode(new Dependency(newArtifact(), "test"));
        assertSerializable(new UnsolvableVersionConflictException(Collections.singleton(Arrays.asList(node))));
    }

    @Test
    public void testVersionResolutionException_Serializable() {
        VersionRequest request = new VersionRequest();
        request.setArtifact(newArtifact()).addRepository(newRepo()).setTrace(newTrace());
        VersionResult result = new VersionResult(request);
        assertSerializable(new VersionResolutionException(result));
    }

    @Test
    public void testVersionRangeResolutionException_Serializable() {
        VersionRangeRequest request = new VersionRangeRequest();
        request.setArtifact(newArtifact()).addRepository(newRepo()).setTrace(newTrace());
        VersionRangeResult result = new VersionRangeResult(request);
        assertSerializable(new VersionRangeResolutionException(result));
    }
}
