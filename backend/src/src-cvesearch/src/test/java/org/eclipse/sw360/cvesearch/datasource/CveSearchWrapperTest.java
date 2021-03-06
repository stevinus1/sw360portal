/*
 * Copyright (c) Bosch Software Innovations GmbH 2016.
 * With modifications by Siemens AG, 2016.
 * Part of the SW360 Portal Project.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.sw360.cvesearch.datasource;

import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;
import org.eclipse.sw360.cvesearch.service.CveSearchHandler;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.eclipse.sw360.cvesearch.datasource.CveSearchDataTestHelper.isEquivalent;
import static org.eclipse.sw360.cvesearch.datasource.CveSearchDataTestHelper.isUrlReachable;

public class CveSearchWrapperTest {
    CveSearchApi cveSearchApi;
    CveSearchWrapper cveSearchWrapper;

    String VENDORNAME = "zyxel";
    String PRODUCTNAME = "zywall";
    String CPE = "cpe:2.3:a:zyxel:zywall:1050";

    private class ReleaseBuilder {
        private String releaseName, releaseVersion, cpe, vendorFullname, vendorShortname;

        public ReleaseBuilder setName(String releaseName) {
            this.releaseName = releaseName;
            return this;
        }

        public ReleaseBuilder setVersion(String releaseVersion) {
            this.releaseVersion = releaseVersion;
            return this;
        }

        public ReleaseBuilder setCpe(String cpe) {
            this.cpe = cpe;
            return this;
        }

        public ReleaseBuilder setVendorFullname(String vendorFullname) {
            this.vendorFullname = vendorFullname;
            return this;
        }

        public ReleaseBuilder setVendorShortname(String vendorShortname) {
            this.vendorShortname = vendorShortname;
            return this;
        }

        public Release get() {
            return new Release() {
                @Override
                public String getName() {
                    return releaseName;
                }
                @Override
                public boolean isSetName() {
                    return name!=null;
                }
                @Override
                public String getVersion() {
                    return releaseVersion;
                }
                @Override
                public boolean isSetVersion() {
                    return releaseVersion!=null;
                }
                @Override
                public Vendor getVendor() {
                    return new Vendor() {
                        @Override
                        public String getFullname() {
                            return vendorFullname;
                        }
                        @Override
                        public boolean isSetFullname() {
                            return vendorFullname!=null;
                        }
                        @Override
                        public String getShortname() {
                            return vendorShortname;
                        }
                        @Override
                        public boolean isSetShortname() {
                            return vendorShortname!=null;
                        }
                    };
                }
                @Override
                public String getCpeid() {
                    return cpe;
                }
                @Override
                public boolean isSetCpeid() {
                    return cpe!=null;
                }
            };
        }
    }

    @Before
    public void setUp() {
        Properties props = CommonUtils.loadProperties(CveSearchWrapperTest.class, "/cvesearch.properties");
        String host = props.getProperty(CveSearchHandler.CVESEARCH_HOST_PROPERTY, "https://cve.circl.lu");
        Assume.assumeTrue("CVE Search host is reachable", isUrlReachable(host));
        cveSearchApi = new CveSearchApiImpl(host);

        cveSearchWrapper = new CveSearchWrapper(cveSearchApi);
    }

    @Ignore
    @Test
    public void testLargeData() throws IOException {
        Release release = new ReleaseBuilder()
                .setName("server")
                .get();

        Optional<List<CveSearchData>> resultWrapped = cveSearchWrapper.searchForRelease(release);
        assert(resultWrapped.isPresent());
        assert(resultWrapped.get() != null);
    }

    @Test
    public void compareToSearchByCPE() throws IOException {
        Release release = new ReleaseBuilder()
                .setName("blindstring")
                .setVendorFullname("blindstring")
                .setVendorShortname("blindstring")
                .setCpe(CPE)
                .get();

        List<CveSearchData> resultDirect = cveSearchApi.cvefor(CPE);

        Optional<List<CveSearchData>> resultWrapped = cveSearchWrapper.searchForRelease(release);

        assert(resultWrapped.isPresent());
        assert(resultWrapped.get() != null);

        assert(isEquivalent(resultDirect,resultWrapped.get()));
    }

    @Ignore("meanwhile cveSearchWrapper implementation changed, test maybe suitable for later use")
    @Test
    public  void compareToWithoutWrapper() throws IOException {
        Release release = new ReleaseBuilder()
                .setName(PRODUCTNAME)
                .setVendorFullname(VENDORNAME)
                .get();

        List<CveSearchData> resultDirect = cveSearchApi.search(VENDORNAME, PRODUCTNAME);

        Optional<List<CveSearchData>> resultWrapped = cveSearchWrapper.searchForRelease(release);

        assert(resultWrapped.isPresent());
        assert(resultWrapped.get() != null);
        assert(resultWrapped.get().size() > 0);
        assert(isEquivalent(resultDirect,resultWrapped.get()));
    }
}
