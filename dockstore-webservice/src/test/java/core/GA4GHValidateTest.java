/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package core;

import java.io.IOException;

import io.dockstore.common.SlowTest;
import io.dockstore.common.UnitTest;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * This is used to run the Python GA4GH tool registry validator to ensure that our implementation is compliant.
 *
 * @author dyuen
 */
@Category({SlowTest.class, UnitTest.class})
public class GA4GHValidateTest {

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstore.yml"));

    @Test
    public void validateGA4GH() {
        final int localPort = RULE.getLocalPort();

        final ImmutablePair<String, String> output = Utilities
                .executeCommand("/bin/bash src/test/resources/ga4gh_validate.sh " + localPort, System.out, System.err);
        System.out.println(output.left);
        System.out.println(output.right);
    }
}
