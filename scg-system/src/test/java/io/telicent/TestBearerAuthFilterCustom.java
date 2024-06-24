/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.telicent;

import io.telicent.servlet.auth.jwt.configuration.ConfigurationParameters;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.util.Properties;


class TestBearerAuthFilterCustom extends AbstractBearerAuthTests {

    private static final String CUSTOM_AUTH_HEADER = "X-API-Token";

    @BeforeAll
    static void setUpClass() throws Exception {
        LibTestsSCG.setupAuthentication();

        // Configure for a completely custom header and prefix
        LibTestsSCG.DEFAULT_AUTH_HEADER = CUSTOM_AUTH_HEADER;
        LibTestsSCG.DEFAULT_AUTH_PREFIX = "Test";
        Properties properties = LibTestsSCG.getAuthConfigurationProperties(false);
        properties.put(ConfigurationParameters.PARAM_HEADER_NAMES, CUSTOM_AUTH_HEADER);
        properties.put(ConfigurationParameters.PARAM_HEADER_PREFIXES, "Test");
        Configurator.setSingleSource(new PropertiesSource(properties));

        setupFuseki();
    }

    @AfterAll
    static void tearDownClass() throws Exception {
        teardownFuseki();
        LibTestsSCG.teardownAuthentication();
    }

}
