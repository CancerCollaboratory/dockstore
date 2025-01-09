package io.dockstore.webservice.helpers.doi;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.MetadataResourceHelper;
import org.apache.commons.lang3.StringUtils;

public final class DoiHelper {

    private static DockstoreWebserviceConfiguration config;

    private DoiHelper() {
        // this space intentionally left empty
    }

    public static void setConfig(DockstoreWebserviceConfiguration config) {
        DoiHelper.config = config;
    }

    public static String createDoi(Entry<?, ?> entry, Version<?> version) {
        String name = computeName(entry, version);
        String url = computeUrl(entry, version);
        String metadata = computeMetadata(name, entry, version);
        return getDoiService().createDoi(name, url, metadata);
    }

    public static String createDoi(Entry<?, ?> entry) {
        /*
        // TODO, this method might look something like:
        Version defaultVersion = getRepresentativeVersion(entry);
        String name = computeName(entry);
        String metadata = computeMetadata(id, entry, defaultVersion);
        String url = computeUrl(entry);
        return getDoiService().createDoi(id, metadata, url);
        */
        return "10.TODO/TODO";
    }

    private static String computeName(Entry<?, ?> entry, Version<?> version) {
        String shoulder = StringUtils.firstNonEmpty(config.getCustomDoiShoulder(), "10.5072/FK2");
        return "%s.%d.%d".formatted(shoulder, entry.getId(), version.getId());
    }

    private static String computeUrl(Entry<?, ?> entry, Version<?> version) {
        return MetadataResourceHelper.createVersionURL(entry, version);
    }

    private static String computeMetadata(String name, Entry<?, ?> entry, Version<?> version) {
        return DataCiteHelper.createDataCiteXmlMetadataForVersion(name, entry, version);
    }

    private static DoiService getDoiService() {
        String ezidUser = config.getEzidUser();
        String ezidPassword = config.getEzidPassword();
        if (ezidUser != null && ezidPassword != null) {
            return new EzidDoiService(ezidUser, ezidPassword);
        } else {
            return new DummyDoiService();
        }
    }
}
