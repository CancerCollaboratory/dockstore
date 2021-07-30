package io.dockstore.webservice.languages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.dockstore.common.DockerImageReference;
import io.dockstore.common.Registry;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.FileFormat;
import io.dockstore.webservice.core.ParsedInformation;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.languages.LanguageHandlerInterface.DockerSpecifier;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Tests public methods in the CWLHandler file
 * @author gluu
 * @since 1.5.0
 */
public class CWLHandlerTest {
    /**
     * Tests if the input and output file formats can be extracted from a CWL descriptor file
     * @throws Exception
     */
    @Test
    public void getInputFileFormats() throws Exception {
        CWLHandler cwlHandler = new CWLHandler();
        String filePath = ResourceHelpers.resourceFilePath("metadata_example4.cwl");
        Set<FileFormat> inputs = cwlHandler.getFileFormats(FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), "inputs");
        Assert.assertTrue(inputs.stream().anyMatch(input -> input.getValue().equals("http://edamontology.org/format_2572")));
        Set<FileFormat> outputs = cwlHandler.getFileFormats(FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), "outputs");
        Assert.assertTrue(outputs.stream().anyMatch(input -> input.getValue().equals("http://edamontology.org/format_1964")));
    }

    @Test
    public void testDeterminingImageRegistry() {
        CWLHandler cwlHandler = new CWLHandler();
        Assert.assertEquals("Should be Docker Hub", Registry.DOCKER_HUB, cwlHandler.determineImageRegistry("python:2.7").get());
        Assert.assertEquals("Should be Docker Hub", Registry.DOCKER_HUB, cwlHandler.determineImageRegistry("debian:jessie").get());
        Assert.assertEquals("Should be Docker Hub", Registry.DOCKER_HUB, cwlHandler.determineImageRegistry("knowengdev/data_cleanup_pipeline:07_11_2017").get());
        Assert.assertTrue("Should be empty for no version being included", cwlHandler.determineImageRegistry("knowengdev/data_cleanup_pipeline").isEmpty());
        Assert.assertTrue("Should be empty for no version being included", cwlHandler.determineImageRegistry("python:").isEmpty());
        Assert.assertEquals("Should be Amazon", Registry.AMAZON_ECR, cwlHandler.determineImageRegistry("137112412989.dkr.ecr.us-east-1.amazonaws.com/amazonlinux:latest").get());
        Assert.assertEquals("Should be Amazon", Registry.AMAZON_ECR, cwlHandler.determineImageRegistry("public.ecr.aws/ubuntu/ubuntu:latest").get());
        Assert.assertTrue("Should be empty, Google not supported yet", cwlHandler.determineImageRegistry("gcr.io/project-id/image:tag").isEmpty());
        Assert.assertEquals("Should be Quay", Registry.QUAY_IO, cwlHandler.determineImageRegistry("quay.io/ucsc_cgl/verifybamid:1.30.0").get());
    }

    @Test
    public void testDeterminingImageSpecifier() {
        Assert.assertEquals(
                LanguageHandlerInterface.DockerSpecifier.NO_TAG,
                LanguageHandlerInterface.determineImageSpecifier("quay.io/ucsc_cgl/verifybamid", DockerImageReference.LITERAL)
        );
        Assert.assertEquals(
                LanguageHandlerInterface.DockerSpecifier.LATEST,
                LanguageHandlerInterface.determineImageSpecifier("quay.io/ucsc_cgl/verifybamid:latest", DockerImageReference.LITERAL)
        );
        Assert.assertEquals(
                LanguageHandlerInterface.DockerSpecifier.TAG,
                LanguageHandlerInterface.determineImageSpecifier("quay.io/ucsc_cgl/verifybamid:1.30.0", DockerImageReference.LITERAL)
        );
        Assert.assertEquals(
                LanguageHandlerInterface.DockerSpecifier.DIGEST,
                LanguageHandlerInterface.determineImageSpecifier(
                        "quay.io/ucsc_cgl/verifybamid@sha256:05442f015018fb6a315b666f80d205e9ac066b3c53a217d5f791d22831ae98ac",
                        DockerImageReference.LITERAL
                )
        );
        Assert.assertEquals(
                LanguageHandlerInterface.DockerSpecifier.PARAMETER,
                LanguageHandlerInterface.determineImageSpecifier("docker_image", DockerImageReference.DYNAMIC)
        );
    }

    @Test
    public void testURLHandler() {
        ParsedInformation parsedInformation = new ParsedInformation();
        CWLHandler.setImportsBasedOnMapValue(parsedInformation, "https://potato.com");
        Assert.assertTrue(parsedInformation.isHasHTTPImports());
        Assert.assertFalse(parsedInformation.isHasLocalImports());
        ParsedInformation parsedInformation2 = new ParsedInformation();
        CWLHandler.setImportsBasedOnMapValue(parsedInformation2, "http://potato.com");
        Assert.assertTrue(parsedInformation2.isHasHTTPImports());
        Assert.assertFalse(parsedInformation2.isHasLocalImports());
        ParsedInformation parsedInformation3 = new ParsedInformation();
        CWLHandler.setImportsBasedOnMapValue(parsedInformation3, "ftp://potato.com");
        Assert.assertFalse(parsedInformation3.isHasHTTPImports());
        Assert.assertTrue(parsedInformation3.isHasLocalImports());
        ParsedInformation parsedInformation4 = new ParsedInformation();
        CWLHandler.setImportsBasedOnMapValue(parsedInformation4, "potato.cwl");
        Assert.assertFalse(parsedInformation4.isHasHTTPImports());
        Assert.assertTrue(parsedInformation4.isHasLocalImports());
        ParsedInformation parsedInformation5 = new ParsedInformation();
        CWLHandler.setImportsBasedOnMapValue(parsedInformation5, "httppotato.cwl");
        Assert.assertFalse(parsedInformation5.isHasHTTPImports());
        Assert.assertTrue(parsedInformation5.isHasLocalImports());
    }

    @Test
    public void testURLFromEntry() {
        final LanguageHandlerInterface handler = Mockito.mock(LanguageHandlerInterface.class, Mockito.CALLS_REAL_METHODS);
        final ToolDAO toolDAO = Mockito.mock(ToolDAO.class);

        // Cases that don't rely on toolDAO
        Assert.assertNull(handler.getURLFromEntry("", toolDAO, null));
        Assert.assertEquals("https://images.sbgenomics.com/foo/bar",
                handler.getURLFromEntry("images.sbgenomics.com/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        Assert.assertEquals("https://images.sbgenomics.com/foo/bar",
                handler.getURLFromEntry("images.sbgenomics.com/foo/bar:1", toolDAO, DockerSpecifier.TAG));

        Assert.assertEquals("https://hub.docker.com/_/foo", handler.getURLFromEntry("foo", toolDAO, DockerSpecifier.NO_TAG));
        Assert.assertEquals("https://hub.docker.com/_/foo", handler.getURLFromEntry("foo:1", toolDAO, DockerSpecifier.TAG));

        Assert.assertEquals("https://gallery.ecr.aws/foo/bar/test", // Public ECR repo name with a "/" -> "bar/test"
                handler.getURLFromEntry("public.ecr.aws/foo/bar/test", toolDAO, DockerSpecifier.NO_TAG));
        Assert.assertEquals("https://gallery.ecr.aws/foo/bar",
                handler.getURLFromEntry("public.ecr.aws/foo/bar:1", toolDAO, DockerSpecifier.TAG));
        Assert.assertEquals("https://gallery.ecr.aws/foo/bar",
                handler.getURLFromEntry("public.ecr.aws/foo/bar@sha256:123456789abc", toolDAO, DockerSpecifier.DIGEST));
        Assert.assertEquals("https://012345678912.dkr.ecr.us-east-1.amazonaws.com/foo/bar", // Private ECR repo name with a "/" -> "foo/bar"
                handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        Assert.assertEquals("https://012345678912.dkr.ecr.us-east-1.amazonaws.com/foo",
                handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo:1", toolDAO, DockerSpecifier.TAG));
        Assert.assertEquals("https://012345678912.dkr.ecr.us-east-1.amazonaws.com/foo",
                handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo@sha256:123456789abc", toolDAO,
                        DockerSpecifier.DIGEST));

        Assert.assertEquals("https://ghcr.io/foo/bar",
                handler.getURLFromEntry("ghcr.io/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        Assert.assertEquals("https://ghcr.io/foo/bar",
                handler.getURLFromEntry("ghcr.io/foo/bar:1", toolDAO, DockerSpecifier.TAG));
        Assert.assertEquals("https://ghcr.io/foo/bar",
                handler.getURLFromEntry("ghcr.io/foo/bar@sha256:123456789abc", toolDAO, DockerSpecifier.DIGEST));

        // When toolDAO.findAllByPath() returns null/empty
        when(toolDAO.findAllByPath(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(null);
        Assert.assertEquals("https://quay.io/repository/foo/bar",
                handler.getURLFromEntry("quay.io/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        Assert.assertEquals("https://quay.io/repository/foo/bar",
                handler.getURLFromEntry("quay.io/foo/bar:1", toolDAO, DockerSpecifier.TAG));

        Assert.assertEquals("https://hub.docker.com/r/foo/bar", handler.getURLFromEntry("foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        Assert.assertEquals("https://hub.docker.com/r/foo/bar", handler.getURLFromEntry("foo/bar:1", toolDAO, DockerSpecifier.TAG));

        Assert.assertEquals("https://gallery.ecr.aws/foo/bar",
                handler.getURLFromEntry("public.ecr.aws/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        Assert.assertEquals("https://gallery.ecr.aws/foo/bar",
                handler.getURLFromEntry("public.ecr.aws/foo/bar:1", toolDAO, DockerSpecifier.TAG));
        Assert.assertEquals("https://gallery.ecr.aws/foo/bar",
                handler.getURLFromEntry("public.ecr.aws/foo/bar@sha256:123456789abc", toolDAO, DockerSpecifier.DIGEST));
        Assert.assertEquals("https://012345678912.dkr.ecr.us-east-1.amazonaws.com/foo",
                handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo", toolDAO, DockerSpecifier.NO_TAG));
        Assert.assertEquals("https://012345678912.dkr.ecr.us-east-1.amazonaws.com/foo",
                handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo:1", toolDAO, DockerSpecifier.TAG));
        Assert.assertEquals("https://012345678912.dkr.ecr.us-east-1.amazonaws.com/foo",
                handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo@sha256:123456789abc", toolDAO,
                        DockerSpecifier.DIGEST));

        Assert.assertEquals("https://ghcr.io/foo/bar",
                handler.getURLFromEntry("ghcr.io/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        Assert.assertEquals("https://ghcr.io/foo/bar",
                handler.getURLFromEntry("ghcr.io/foo/bar:1", toolDAO, DockerSpecifier.TAG));
        Assert.assertEquals("https://ghcr.io/foo/bar",
                handler.getURLFromEntry("ghcr.io/foo/bar@sha256:123456789abc", toolDAO, DockerSpecifier.DIGEST));
        // A specific architecture image is referenced by digest but it may also include a tag from the multi-arch image
        Assert.assertEquals("https://ghcr.io/foo/bar",
                handler.getURLFromEntry("ghcr.io/foo/bar:1@sha256:123456789abc", toolDAO, DockerSpecifier.DIGEST));

        // When toolDAO.findAllByPath() returns non-empty List<Tool>
        when(toolDAO.findAllByPath(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(List.of(Mockito.mock(Tool.class)));
        Assert.assertEquals("https://www.dockstore.org/containers/quay.io/foo/bar",
                handler.getURLFromEntry("quay.io/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        Assert.assertEquals("https://www.dockstore.org/containers/quay.io/foo/bar",
                handler.getURLFromEntry("quay.io/foo/bar:1", toolDAO, DockerSpecifier.TAG));

        Assert.assertEquals("https://www.dockstore.org/containers/registry.hub.docker.com/foo/bar",
                handler.getURLFromEntry("foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        Assert.assertEquals("https://www.dockstore.org/containers/registry.hub.docker.com/foo/bar",
                handler.getURLFromEntry("foo/bar:1", toolDAO, DockerSpecifier.TAG));

        Assert.assertEquals("https://www.dockstore.org/containers/public.ecr.aws/foo/bar",
                handler.getURLFromEntry("public.ecr.aws/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        Assert.assertEquals("https://www.dockstore.org/containers/public.ecr.aws/foo/bar",
                handler.getURLFromEntry("public.ecr.aws/foo/bar:1", toolDAO, DockerSpecifier.TAG));
        Assert.assertEquals("https://www.dockstore.org/containers/public.ecr.aws/foo/bar",
                handler.getURLFromEntry("public.ecr.aws/foo/bar@sha256:123456789", toolDAO, DockerSpecifier.DIGEST));
        Assert.assertEquals("https://www.dockstore.org/containers/012345678912.dkr.ecr.us-east-1.amazonaws.com/foo",
                handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo", toolDAO, DockerSpecifier.NO_TAG));
        Assert.assertEquals("https://www.dockstore.org/containers/012345678912.dkr.ecr.us-east-1.amazonaws.com/foo",
                handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo:1", toolDAO, DockerSpecifier.TAG));
        Assert.assertEquals("https://www.dockstore.org/containers/012345678912.dkr.ecr.us-east-1.amazonaws.com/foo",
                handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo@sha256:123456789", toolDAO,
                        DockerSpecifier.DIGEST));

        Assert.assertEquals("https://www.dockstore.org/containers/ghcr.io/foo/bar",
                handler.getURLFromEntry("ghcr.io/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        Assert.assertEquals("https://www.dockstore.org/containers/ghcr.io/foo/bar",
                handler.getURLFromEntry("ghcr.io/foo/bar:1", toolDAO, DockerSpecifier.TAG));
        Assert.assertEquals("https://www.dockstore.org/containers/ghcr.io/foo/bar",
                handler.getURLFromEntry("ghcr.io/foo/bar@sha256:123456789abc", toolDAO, DockerSpecifier.DIGEST));
    }

    @Test
    public void testGetImageNameWithoutSpecifier() {
        final LanguageHandlerInterface handler = Mockito.mock(LanguageHandlerInterface.class, Mockito.CALLS_REAL_METHODS);

        Assert.assertEquals("foo/bar", handler.getImageNameWithoutSpecifier("foo/bar", DockerSpecifier.NO_TAG));
        Assert.assertEquals("foo/bar", handler.getImageNameWithoutSpecifier("foo/bar:1", DockerSpecifier.TAG));
        Assert.assertEquals("foo/bar", handler.getImageNameWithoutSpecifier("foo/bar@sha256:123456789abc", DockerSpecifier.DIGEST));
        Assert.assertEquals("quay.io/foo/bar", handler.getImageNameWithoutSpecifier("quay.io/foo/bar:1", DockerSpecifier.TAG));
        Assert.assertEquals("ghcr.io/foo/bar", handler.getImageNameWithoutSpecifier("ghcr.io/foo/bar:1@sha256:123456789abc", DockerSpecifier.DIGEST));
        Assert.assertEquals("public.ecr.aws/foo/bar", handler.getImageNameWithoutSpecifier("public.ecr.aws/foo/bar@sha256:123456789abc", DockerSpecifier.DIGEST));
    }

    @Test
    public void testGetRepositoryName() {
        final LanguageHandlerInterface handler = Mockito.mock(LanguageHandlerInterface.class, Mockito.CALLS_REAL_METHODS);

        Assert.assertEquals("foo/bar", handler.getRepositoryName(Registry.QUAY_IO, "quay.io/foo/bar:1", DockerSpecifier.TAG));
        Assert.assertEquals("foo/bar", handler.getRepositoryName(Registry.QUAY_IO, "quay.io/foo/bar@sha256:123456789abc", DockerSpecifier.DIGEST));

        Assert.assertEquals("library/foo", handler.getRepositoryName(Registry.DOCKER_HUB, "foo:1", DockerSpecifier.TAG));
        Assert.assertEquals("foo/bar", handler.getRepositoryName(Registry.DOCKER_HUB, "foo/bar:1", DockerSpecifier.TAG));
        Assert.assertEquals("foo/bar", handler.getRepositoryName(Registry.DOCKER_HUB, "foo/bar@sha256:123456789abc", DockerSpecifier.DIGEST));

        Assert.assertEquals("foo/bar", handler.getRepositoryName(Registry.GITHUB_CONTAINER_REGISTRY, "ghcr.io/foo/bar:1", DockerSpecifier.TAG));
        Assert.assertEquals("foo/bar/test", handler.getRepositoryName(Registry.GITHUB_CONTAINER_REGISTRY, "ghcr.io/foo/bar/test:1", DockerSpecifier.TAG));
        Assert.assertEquals("foo/bar", handler.getRepositoryName(Registry.GITHUB_CONTAINER_REGISTRY, "ghcr.io/foo/bar@sha256:123456789abc", DockerSpecifier.DIGEST));
        Assert.assertEquals("foo/bar", handler.getRepositoryName(Registry.GITHUB_CONTAINER_REGISTRY, "ghcr.io/foo/bar:1@sha256:123456789abc", DockerSpecifier.DIGEST));

        Assert.assertEquals("foo/bar", handler.getRepositoryName(Registry.AMAZON_ECR, "public.ecr.aws/foo/bar:1", DockerSpecifier.TAG));
        Assert.assertEquals("foo/bar/test", handler.getRepositoryName(Registry.AMAZON_ECR, "public.ecr.aws/foo/bar/test:1", DockerSpecifier.TAG));
        Assert.assertEquals("foo/bar", handler.getRepositoryName(Registry.AMAZON_ECR, "public.ecr.aws/foo/bar@sha256:123456789abc", DockerSpecifier.DIGEST));
        Assert.assertEquals("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo/bar",
                handler.getRepositoryName(Registry.AMAZON_ECR, "012345678912.dkr.ecr.us-east-1.amazonaws.com/foo/bar:1", DockerSpecifier.TAG));
        Assert.assertEquals("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo/bar",
                handler.getRepositoryName(Registry.AMAZON_ECR, "012345678912.dkr.ecr.us-east-1.amazonaws.com/foo/bar@sha256:123456789abc", DockerSpecifier.DIGEST));
    }

    @Test
    public void testGetSpecifierName() {
        final LanguageHandlerInterface handler = Mockito.mock(LanguageHandlerInterface.class, Mockito.CALLS_REAL_METHODS);

        Assert.assertEquals("", handler.getSpecifierName("foo/bar", DockerSpecifier.NO_TAG));
        Assert.assertEquals("1", handler.getSpecifierName("foo/bar:1", DockerSpecifier.TAG));
        Assert.assertEquals("sha256:123456789abc", handler.getSpecifierName("foo@sha256:123456789abc", DockerSpecifier.DIGEST));
        Assert.assertEquals("sha256:123456789abc", handler.getSpecifierName("ghcr.io/foo/bar/test@sha256:123456789abc", DockerSpecifier.DIGEST));
        Assert.assertEquals("sha256:123456789abc", handler.getSpecifierName("ghcr.io/foo/bar/test:1@sha256:123456789abc", DockerSpecifier.DIGEST));
    }

    /**
     * Test that the calculated digest matches the actual digest of the image.
     * @throws URISyntaxException
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void testCalculateDockerImageDigest() throws URISyntaxException, IOException, InterruptedException {
        final LanguageHandlerInterface handler = Mockito.mock(LanguageHandlerInterface.class, Mockito.CALLS_REAL_METHODS);
        // GHCR image used: ghcr.io/helm/tiller@sha256:4c43eb385032945cad047d2350e4945d913b90b3ab43ee61cecb32a495c6df0f (associated tag is 'v2.17.0')
        String repo = "helm/tiller";
        String digest = "sha256:4c43eb385032945cad047d2350e4945d913b90b3ab43ee61cecb32a495c6df0f";
        Optional<String> token = handler.getDockerToken(Registry.GITHUB_CONTAINER_REGISTRY, repo);
        Assert.assertTrue(token.isPresent());
        Optional<HttpResponse<String>> manifestResponse = handler.getDockerManifest(token.get(), Registry.GITHUB_CONTAINER_REGISTRY.getDockerPath(), repo, digest);
        Assert.assertEquals(HttpStatus.SC_OK, manifestResponse.get().statusCode());
        String manifestBody = manifestResponse.get().body();
        String calculatedDigest = "sha256:" + handler.calculateDockerImageDigest(manifestBody);
        Assert.assertEquals(digest, calculatedDigest);

        // Amazon ECR image used: public.ecr.aws/nginx/unit:1.24.0-minimal
        repo = "nginx/unit";
        digest = "sha256:5711186c4c24cf544c1d6ea1f64de288fc3d1f47bc506cae251a75047b15a89a";
        token = handler.getDockerToken(Registry.AMAZON_ECR, repo);
        Assert.assertTrue(token.isPresent());
        manifestResponse = handler.getDockerManifest(token.get(), Registry.AMAZON_ECR.getDockerPath(), repo, digest);
        Assert.assertEquals(HttpStatus.SC_OK, manifestResponse.get().statusCode());
        manifestBody = manifestResponse.get().body();
        calculatedDigest = "sha256:" + handler.calculateDockerImageDigest(manifestBody);
        Assert.assertEquals(digest, calculatedDigest);
    }

    @Test
    public void testGetContentWithMalformedDescriptors() throws IOException {
        CWLHandler cwlHandler = new CWLHandler();

        // create and mock parameters for getContent()
        final Set<SourceFile> emptySet = Collections.emptySet();
        final ToolDAO toolDAO = Mockito.mock(ToolDAO.class);
        when(toolDAO.findAllByPath(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(null);

        // expect parsing error
        File cwlFile = new File(ResourceHelpers.resourceFilePath("brokenCWL.cwl"));
        try {
            cwlHandler.getContent("/brokenCWL.cwl", FileUtils.readFileToString(cwlFile, StandardCharsets.UTF_8), emptySet,
                LanguageHandlerInterface.Type.TOOLS, toolDAO);
            Assert.fail("Expected parsing error");
        } catch (CustomWebApplicationException e) {
            Assert.assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, e.getResponse().getStatus());
            assertThat(e.getErrorMessage()).contains(CWLHandler.CWL_PARSE_ERROR);

        }

        // expect error based on invalid cwlVersion
        cwlFile = new File(ResourceHelpers.resourceFilePath("badVersionCWL.cwl"));
        try {
            cwlHandler.getContent("/badVersionCWL.cwl", FileUtils.readFileToString(cwlFile, StandardCharsets.UTF_8), emptySet,
                LanguageHandlerInterface.Type.TOOLS, toolDAO);
            Assert.fail("Expected cwlVersion error");
        } catch (CustomWebApplicationException e) {
            Assert.assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, e.getResponse().getStatus());
            assertThat(e.getErrorMessage()).contains(CWLHandler.CWL_VERSION_ERROR);
        }

        // expect error based on an undefined cwlVersion
        cwlFile = new File(ResourceHelpers.resourceFilePath("noVersionCWL.cwl"));
        try {
            cwlHandler.getContent("/noVersionCWL.cwl", FileUtils.readFileToString(cwlFile, StandardCharsets.UTF_8), emptySet,
                LanguageHandlerInterface.Type.TOOLS, toolDAO);
            Assert.fail("Expected undefined cwlVersion error");
        } catch (CustomWebApplicationException e) {
            Assert.assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, e.getResponse().getStatus());
            assertThat(e.getErrorMessage()).contains(CWLHandler.CWL_NO_VERSION_ERROR);
        }

        // expect error based on invalid JSON $import/$include
        cwlFile = new File(ResourceHelpers.resourceFilePath("invalidMapCWL.cwl"));
        try {
            cwlHandler.getContent("/invalidMapCWL.cwl", FileUtils.readFileToString(cwlFile, StandardCharsets.UTF_8), emptySet,
                LanguageHandlerInterface.Type.TOOLS, toolDAO);
            Assert.fail("Expected ($)import/($)include error");
        } catch (CustomWebApplicationException e) {
            Assert.assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, e.getResponse().getStatus());
            assertThat(e.getErrorMessage()).contains(CWLHandler.CWL_PARSE_SECONDARY_ERROR);
        }
    }
}
