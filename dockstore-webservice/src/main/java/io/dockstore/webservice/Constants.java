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
package io.dockstore.webservice;

/**
 * @author gluu
 * @since 22/09/17
 */
public final class Constants {
    public static final String JWT_SECURITY_DEFINITION_NAME = "BEARER";
    public static final int LAMBDA_FAILURE = 418; // Tell lambda to not try again
    public static final String OPTIONAL_AUTH_MESSAGE = "Does not require authentication for published workflows,"
            + " authentication can be provided for restricted workflows";
    public static final String DOCKSTORE_YML_PATH = "/.dockstore.yml";
    public static final String SKIP_COMMIT_ID = "skip";

    private Constants() {
        // not called
    }
}
