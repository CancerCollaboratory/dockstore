[![Build Status](https://travis-ci.org/ga4gh/dockstore.svg?branch=develop)](https://travis-ci.org/ga4gh/dockstore) [![Coverage Status](https://coveralls.io/repos/github/ga4gh/dockstore/badge.svg?branch=develop)](https://coveralls.io/github/ga4gh/dockstore?branch=develop)
[![Website](https://img.shields.io/website/https/dockstore.org.svg)](https://dockstore.org)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/ga4gh/dockstore?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)  
[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.168593.svg)](https://doi.org/10.5281/zenodo.168593)
[![Uptime Robot status](https://img.shields.io/uptimerobot/status/m779655940-a297af07d1cac2d6ad40c491.svg)]()
[![license](https://img.shields.io/hexpm/l/plug.svg?maxAge=2592000)](LICENSE)


# Dockstore

Dockstore provides a place for users to share tools encapsulated in Docker and described with the Common 
Workflow Language (CWL) or WDL (Workflow Description Language). This enables scientists to share analytical 
workflows so that they are  machine readable as well as runnable in a variety of environments. While the 
Dockstore is focused on serving researchers in the biosciences, the combination of Docker + CWL/WDL can be used by 
anyone to describe the tools and services in their Docker images in a standardized, machine-readable way.  
We hope to use this project as motivation to create a GA4GH API standard for container registries.

For the live site see [dockstore.org](https://dockstore.org)

This repo contains the web service and CLI components for Dockstore as well as collecting documentation and 
the issues for the project as a whole. The usage of this is to enumerate the docker containers 
(from quay.io and hopefully docker hub) and the workflows (from github/bitbucket) that are available 
to users of Dockstore.org.

For the related web UI see the [dockstore-ui](https://github.com/dockstore/dockstore-ui2) project.

## For Dockstore Users

The following section is useful for users of Dockstore (e.g. those that want to browse, register, and 
launch tools). 

After registering at [dockstore.org](https://dockstore.org), you will be able to download the Dockstore 
CLI at https://dockstore.org/onboarding

The CLI has the following dependencies

* Java 8 (Update 101 or newer)
* cwltool (to run CWL workflows locally)

To install CWL tool:

    pip install --user cwl-runner cwltool==1.0.20170828135420 schema-salad==2.6.20170806163416 avro==1.8.1 ruamel.yaml==0.14.12 requests==2.18.4

You may need other pip installable tools like `typing` or `setuptools`.  This depends on your python environment.

### Configuration File

A basic Dockstore configuration file is available/should be created in `~/.dockstore/config` and contains the following
at minimum:
```
token = <your generated by the dockstore site>
server-url = https://www.dockstore.org:8443
```

### Migration to Dockstore 1.3

1. Ensure that your Java 8 version is newer than update 101. 

### Migration to Dockstore 1.2

This keeps track of breaking changes when migrating from Dockstore 1.1 to beta releases of 1.2 on the client side. 

1. Paths for input files are standardized to paths like `s3://test.bucket/test`, `icgc://1234-efg`, `https://file.org/test.txt`. This means paths like `icgc:1234-efg` will no longer work
2. A new version of cwltool 
3. The syntax for launching tools has been simplified. `--local-entry` is no longer a flag, but is an alternative to `--entry`.

### Migration to Dockstore 1.2.5

Unfortunately, new unique indexes enforcing better data consistency require a clean-up of unpublished workflows. Published content should remain unaffected. 
```
delete from workflow_workflowversion ww where ww.workflowid in (select id from workflow where ispublished='f');
delete from workflow where ispublished='f';
```

### File Provisioning

By default, cwltool reads input files from the local filesystem. Dockstore also adds support for additional file systems
such as http, https, and ftp. Through a plug-in system, Dockstore also supports 
the Amazon S3, [Synapse](http://docs.synapse.org/articles/downloading_data.html), and 
[ICGC Storage Client](http://docs.icgc.org/cloud/guide/#storage-client-usage) via [plugins](https://github.com/dockstore).

Download the above set of default plugins via: 
```
dockstore plugin download
```

Configuration for plugins can be placed inside the Dockstore configuration file in the following format

```
token = <your generated by the dockstore site>
server-url = https://www.dockstore.org:8443

# options below this are optional

use-cache = false                           #set this to true to cache input files for rapid development
cache-dir = /home/<user>/.dockstore/cache   #set this to determine where input files are cached (should be the same filesystem as your tool working directories)

[dockstore-file-synapse-plugin]

[dockstore-file-s3-plugin]
endpoint = #set this to point at a non AWS S3 endpoint

[dockstore-file-icgc-storage-client-plugin]
client = /media/large_volume/icgc-storage-client-1.0.23/bin/icgc-storage-client
```

Additional plugins can be created by taking one of the repos in [plugins](https://github.com/dockstore) as a model and 
using [pf4j](https://github.com/decebals/pf4j) as a reference. See [additional documentation](dockstore-file-plugin-parent) for more details. 

## For Dockstore Developers

The following section is useful for Dockstore developers (e.g. those that want to improve or fix the Dockstore web service and UI)

### Dependencies

The dependency environment for Dockstore is described by our 
[Travis-CI config](https://github.com/ga4gh/dockstore/blob/develop/.travis.yml). In addition to the dependencies for 
Dockstore users, note the setup instructions for postgres. Specifically, you will need to have postgres installed 
and setup with the database user specified in [.travis.yml](https://github.com/ga4gh/dockstore/blob/develop/.travis.yml#L26). 

### Building

If you maven build in the root directory this will build not only the web service but the client tool:

    mvn clean install
    # or
    mvn clean install -Punit-tests
    
If you're running tests on Travis-CI (or otherwise have access to the confidential data bundle) Run them via:

    mvn clean install -Pintegration-tests
    
There are also certain categories for tests that they can be added to when writing new tests. 
Categories include:

1. `ToilOnlyTest` are tests that can only be run by Toil (which also installs a different version of cwltool)
2. `ToilCompatibleTest` are tests that can be run with our default cwltool and with Toil
3. `ConfidentialTest` are tests that require access to our confidential testing bundle (ask a member of the development team if you're on the team)

### Running Locally

You can also run it on your local computer but will need to setup postgres separately.

1. Fill in the template dockstore.yml and stash it somewhere outside the git repo (like ~/.dockstore)
2. The dockstore.yml is mostly a standard [Dropwizard configuration file](http://www.dropwizard.io/1.0.5/docs/manual/configuration.html). 
Refer to the linked document to setup httpClient and database. 
3. Start with `java -jar dockstore-webservice/target/dockstore-webservice-*.jar   server ~/.dockstore/dockstore.yml`
4. If you need integration with GitHub.com, Quay.io. or Bitbucket for your work, you will need to follow the appropriate 
sections below and then fill out the corresponding fields in your 
[dockstore.yml](https://github.com/ga4gh/dockstore/blob/develop/dockstore.yml#L2). 

### View Swagger UI

The Swagger UI is reachable while the Dockstore webservice is running. This allows you to explore available web resources.

1. Browse to [http://localhost:8080/static/swagger-ui/index.html](http://localhost:8080/static/swagger-ui/index.html)


## Development

### Coding Standards

[codestyle.xml](codestyle.xml) defines the coding style for Dockstore as an IntelliJ Code Style XML file that should be imported into IntelliJ IDE. 
We also have a matching [checkstyle.xml](checkstyle.xml) that can be imported into other IDEs and is run during the build.  

For users of Intellij or comparable IDEs, we also suggest loading the checkstyle.xml with a plugin in order to display warnings and errors while coding live rather than encountering them later when running a build. 

### Dockstore Command Line

The dockstore command line should be installed in a location in your path.

  /dockstore-client/bin/dockstore

You then need to setup a `~/.dockstore/config` file with the following contents:

```
token: <dockstore_token_from_web_app>
server-url: http://www.dockstore.org:8080
```

If you are working with a custom-built or updated dockstore client you will need to update the jar in: `~/.dockstore/config/self-installs`.

### Swagger Web Service Components for GA4GH Tool Registry Schema

Background:

 * The [tool-registry-schema](https://github.com/ga4gh/tool-registry-schemas) are intended on allowing different tool registries to exchange and compare data
 * Defined in swagger yaml
 * We use the online swagger editor to generate a JAX-RS skeleton for implementation
 * Unlike the above components, this is a server component rather than client component, thus we cannot use swagger-codegen (client-only for now?)
 
 To regenerate the swagger client:
 
1. Open up the yaml document for the specification in the editor.swagger.io
2. Hit Generate Server and select JAX-RS
3. Replace the appropriate classes in dockstore-webservice
4. Unlike the client classes, we cannot separate quite as cleanly. Classes to watch out for are io.swagger.api.ToolsApi (includes DropWizard specific UnitOfWork annotations and a custom path) and io.swagger.api.impl.ToolsApiServiceImpl (includes our implementation).
5. Customizations include, `@Path(DockstoreWebserviceApplication.GA4GH_API_PATH + <depends on api class>)` for Api classes, `@UnitOfWork` added to resources, and `@JsonNaming(PropertyNamingStrategy.KebabCaseStrategy.class)` added to model classes for GA4GH.

### HubFlow Operations

#### How to perform a Maven release for a Unstable Release

This is for pre-release versions that have not been released to production. 

1. Create a release tag and iterate pom file versions `mvn release:prepare`
2. Release from the tag into artifactory (you may need permissions) `mvn release:perform`
3. Merge to master if this is a stable release `git checkout master; git merge <your tag here>`

Special note: If a test is failing during perform, but did not fail during prepare or Travis-CI builds, you may have a non-deterministic error. Skip tests during a release with `mvn release:perform -Darguments="-DskipTests"`

After the release to Artifactory, document the release on GitHub via the Releases page. Take a look at commits since the last release and closed pull requests for information on what goes into the changelog. Also attach the newly created Dockstore script and shaded client jar.

#### How to perform a Maven release for an Stable Release

This is for release versions that have been released to production. 

1. Create a hotfix branch  `mvn hf hotfix start <version>`
2. Iterate pom file versions to a SNAPSHOT if needed `mvn versions:set -DnewVersion=<version>-SNAPSHOT` 
3. Prepare the release and the perform it (you may need permissions) `mvn release:prepare` and `mvn release:perform`
3. Merge to master and develop `mvn hf hotfix finish`

As with the unstable release, document the release and attach the new Dockstore script and shaded client jar. 

### Encrypted Documents for Travis-CI

Encrypted documents necessary for confidential testing are handled as indicated in the documents at Travis-CI for  
[files](https://docs.travis-ci.com/user/encrypting-files/#Encrypting-multiple-files) and [environment variables](https://docs.travis-ci.com/user/encryption-keys).

A convenience script is provided as encrypt.sh which will compress confidential files, encrypt them, and then update an encrypted archive on GitHub. Confidential files should also be added to .gitignore to prevent accidental check-in. The unencrypted secrets.tar should be privately distributed among members of the team that need to work with confidential data. 

To dump a new copy of the encrypted database from one that you have setup, use the following (or similar):

    pg_dump --data-only --column-inserts   webservice_test &> dockstore-integration-testing/src/test/resources/db_confidential_dump_full.sql

### Adding Copyright header to all files with IntelliJ

To add copyright headers to all files with IntelliJ

1. Ensure the Copyright plugin is installed (Settings -> Plugins)
2. Create a new copyright profile matching existing copyright header found on all files, name it Dockstore (Settings -> Copyright -> Copyright Profiles -> Add New)
3. Set the default project copyright to Dockstore (Settings -> Copyright)

### Legacy Material

Additional documentation on developing Dockstore is available at [legacy.md](https://github.com/ga4gh/dockstore/blob/develop/legacy.md)
