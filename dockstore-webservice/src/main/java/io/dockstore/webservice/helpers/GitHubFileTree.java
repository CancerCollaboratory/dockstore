package io.dockstore.webservice.helpers;

import io.dockstore.webservice.CustomWebApplicationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitHubFileTree implements FileTree {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubFileTree.class);

    private final GitHubSourceCodeRepo gitHubSourceCodeRepo;
    private final String repository;
    private final String ref;
    private final ZipFile zipFile;
    private final Map<String, ZipArchiveEntry> pathToEntry;

    public GitHubFileTree(GitHubSourceCodeRepo gitHubSourceCodeRepo, String repository, String ref) {
        this.gitHubSourceCodeRepo = gitHubSourceCodeRepo;
        this.repository = repository;
        this.ref = ref;
        // Read the Zip contents and create an in-memory ZipFile.
        LOG.error("downloading Zip");
        byte[] zipBytes = gitHubSourceCodeRepo.readZip(repository, ref);
        LOG.error("downloaded Zip " + zipBytes.length);
        SeekableByteChannel zipChannel = new SeekableInMemoryByteChannel(zipBytes);
        try {
            zipFile = new ZipFile(zipChannel);
        } catch (IOException e) {
            throw new CustomWebApplicationException("could not read zip archive", HttpStatus.SC_BAD_REQUEST);
        }
        // Create a Map of absolute paths to Zip file entries.
        pathToEntry = new HashMap<>();
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            if (!entry.isDirectory() && !entry.isUnixSymlink()) {
                String path = getPathFromEntry(entry);
                pathToEntry.put(path, entry);
            }
        }
    }

    public String readFile(String path) {
        ZipArchiveEntry entry = pathToEntry.get(path);
        if (entry != null) {
            try (InputStream in = zipFile.getInputStream(entry)) {
                return IOUtils.toString(in, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new CustomWebApplicationException("could not read file from zip archive", HttpStatus.SC_BAD_REQUEST);
            }
        }
        return gitHubSourceCodeRepo.readFile(path, repository, ref);
    }

    public List<String> listFiles(String path) {
        return gitHubSourceCodeRepo.listFiles(path, repository, ref);
    }

    public List<String> listPaths() {
        return new ArrayList<>(pathToEntry.keySet());
    }

    private String getPathFromEntry(ZipArchiveEntry entry) {
        // TODO improve
        return "/" + entry.getName().split("/", 2)[1];
    }
}
