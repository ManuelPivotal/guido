package oss.guido.com.fasterxml.jackson.databind.cfg;

import oss.guido.com.fasterxml.jackson.core.Version;
import oss.guido.com.fasterxml.jackson.core.Versioned;
import oss.guido.com.fasterxml.jackson.core.util.VersionUtil;

/**
 * Automatically generated from PackageVersion.java.in during
 * packageVersion-generate execution of maven-replacer-plugin in
 * pom.xml.
 */
public final class PackageVersion implements Versioned {
    public final static Version VERSION = VersionUtil.parseVersion(
        "2.5.4", "com.fasterxml.jackson.core", "jackson-databind");

    @Override
    public Version version() {
        return VERSION;
    }
}
