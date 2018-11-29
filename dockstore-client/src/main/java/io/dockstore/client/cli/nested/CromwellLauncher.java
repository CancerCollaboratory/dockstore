package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.common.io.Files;
import com.google.gson.Gson;
import io.dockstore.common.Utilities;
import io.swagger.client.ApiException;
import io.swagger.client.model.ToolDescriptor;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.Client.ENTRY_NOT_FOUND;
import static io.dockstore.client.cli.Client.IO_ERROR;

/**
 * This is a base class for clients that launch workflows with Cromwell
 */
public abstract class CromwellLauncher {

    protected static final String DEFAULT_CROMWELL_VERSION = "36";
    protected final AbstractEntryClient abstractEntryClient;

    public CromwellLauncher(AbstractEntryClient abstractEntryClient) {
        this.abstractEntryClient = abstractEntryClient;
    }

    /**
     * Creates a local copy of the Cromwell JAR (May have to download from the GitHub).
     * Uses the default version unless a version is specified in the Dockstore config.
     * @return File object of the Cromwell JAR
     */
    public File getCromwellTargetFile() {
        // initialize cromwell location from ~/.dockstore/config
        INIConfiguration config = Utilities.parseConfig(abstractEntryClient.getConfigFile());
        String cromwellVersion = config.getString("cromwell-version", DEFAULT_CROMWELL_VERSION);
        String cromwellLocation =
                "https://github.com/broadinstitute/cromwell/releases/download/" + cromwellVersion + "/cromwell-" + cromwellVersion + ".jar";
        if (!Objects.equals(DEFAULT_CROMWELL_VERSION, cromwellVersion)) {
            System.out.println("Running with Cromwell " + cromwellVersion + " , Dockstore tests with " + DEFAULT_CROMWELL_VERSION);
        }

        // grab the cromwell jar if needed
        String libraryLocation =
                System.getProperty("user.home") + File.separator + ".dockstore" + File.separator + "libraries" + File.separator;
        URL cromwellURL;
        String cromwellFileName;
        try {
            cromwellURL = new URL(cromwellLocation);
            cromwellFileName = new File(cromwellURL.toURI().getPath()).getName();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException("Could not create cromwell location", e);
        }
        String cromwellTarget = libraryLocation + cromwellFileName;
        File cromwellTargetFile = new File(cromwellTarget);
        if (!cromwellTargetFile.exists()) {
            try {
                FileUtils.copyURLToFile(cromwellURL, cromwellTargetFile);
            } catch (IOException e) {
                throw new RuntimeException("Could not download cromwell location", e);
            }
        }
        return cromwellTargetFile;
    }

    /**
     * Creates a working directory and downloads descriptor files
     * @param type CWL or WDL
     * @param isLocalEntry Is the entry local
     * @param entry Either entry path on Dockstore or local path
     * @return Triple of working dir, primary descriptor and zip file
     */
    public Triple<File, File, File> initializeWorkingDirectoryWithFiles(ToolDescriptor.TypeEnum type, boolean isLocalEntry, String entry) {
        File workingDir;
        try {
            workingDir = Files.createTempDir();
        } catch (IllegalStateException ex) {
            exceptionMessage(ex, "Could not create a temporary working directory.", IO_ERROR);
            throw new RuntimeException(ex);
        }

        out("Created temporary working directory at '" + workingDir.getAbsolutePath() + "'");
        File primaryDescriptor;
        File zipFile;
        if (!isLocalEntry) {
            try {
                primaryDescriptor = abstractEntryClient.downloadTargetEntry(entry, type, true, workingDir);
                String[] parts = entry.split(":");
                String path = parts[0];
                String convertedName = path.replaceAll("/", "_") + ".zip";
                zipFile = new File(workingDir, convertedName);
                out("Successfully downloaded files for entry '" + path + "'");
            } catch (ApiException ex) {
                if (abstractEntryClient.getEntryType().toLowerCase().equals("tool")) {
                    exceptionMessage(ex, "The tool entry does not exist. Did you mean to launch a local tool or a workflow?",
                            ENTRY_NOT_FOUND);
                } else {
                    exceptionMessage(ex, "The workflow entry does not exist. Did you mean to launch a local workflow or a tool?",
                            ENTRY_NOT_FOUND);
                }
                throw new RuntimeException(ex);
            } catch (IOException ex) {
                exceptionMessage(ex, "Problem downloading and unzipping entry.", IO_ERROR);
                throw new RuntimeException(ex);
            }
        } else {
            primaryDescriptor = new File(entry);
            //File parentDir = primaryDescriptor.getParentFile();
            //zipFile = zipDirectory(workingDir, parentDir);
            zipFile = null;
            out("Using local file '" + entry + "' as primary descriptor");
        }

        return new MutableTriple<>(workingDir, primaryDescriptor, zipFile);
    }

    /**
     * Zips the given directoryToZip and returns the zip file
     * @param workingDir The working dir to place the zip file
     * @param directoryToZip The directoryToZip to zip
     * @return The zip file created
     */
    public File zipDirectory(File workingDir, File directoryToZip) {
        String zipFilePath = workingDir.getAbsolutePath() + "/directory.zip";
        try {
            FileOutputStream fos = new FileOutputStream(zipFilePath);
            ZipOutputStream zos = new ZipOutputStream(fos);
            zipFile(directoryToZip, directoryToZip.getName(), zos);
            zos.close();
            fos.close();
        } catch (IOException ex) {
            exceptionMessage(ex, "There was a problem zipping the directoryToZip '" + directoryToZip.getPath() + "'", IO_ERROR);
        }
        return new File(zipFilePath);
    }

    /**
     * A helper function for zipping directories
     * @param fileToZip File being looked at (could be a directory)
     * @param fileName Name of file being looked at
     * @param zos Zip Output Stream
     * @throws IOException
     */
    public void zipFile(File fileToZip, String fileName, ZipOutputStream zos) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zos.putNextEntry(new ZipEntry(fileName.endsWith("/") ? fileName : fileName + "/"));
                zos.closeEntry();
            }
            File[] children = fileToZip.listFiles();
            for (File childFile : children) {
                zipFile(childFile, fileName + "/" + childFile.getName(), zos);
            }

            zos.putNextEntry(new ZipEntry(fileName + "/"));
            zos.closeEntry();
            return;
        }
        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zos.putNextEntry(zipEntry);
        final int byteLength = 1024;
        byte[] bytes = new byte[byteLength];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zos.write(bytes, 0, length);
        }
        fis.close();
    }

    /**
     * Retrieves the output object from the Cromwell stdout
     * TODO: There has to be a better way to do this!
     * @param stdout Output from Cromwell Run
     * @param gson Gson object
     * @return Object for Cromwell output
     */
    public Map<String, String> parseOutputObjectFromCromwellStdout(String stdout, Gson gson) {
        String outputPrefix = "Final Outputs:";
        int startIndex = stdout.indexOf("\n{\n", stdout.indexOf(outputPrefix));
        int endIndex = stdout.indexOf("\n}\n", startIndex) + 2;
        String bracketContents = stdout.substring(startIndex, endIndex).trim();

        if (bracketContents.isEmpty()) {
            throw new RuntimeException("No cromwell output");
        }

        return gson.fromJson(bracketContents, HashMap.class);
    }
}
