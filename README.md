[![Codacy Badge](https://api.codacy.com/project/badge/Grade/bc446ce0a9bd4f81b3258c50f95e01b5)](https://app.codacy.com/app/dockstore/dockstore?utm_source=github.com&utm_medium=referral&utm_content=dockstore/dockstore&utm_campaign=Badge_Grade_Dashboard)
[![Build Status](https://travis-ci.org/dockstore/dockstore.svg?branch=develop)](https://travis-ci.org/dockstore/dockstore) 
[![codecov](https://codecov.io/gh/dockstore/dockstore/branch/develop/graph/badge.svg)](https://codecov.io/gh/dockstore/dockstore)
[![Website](https://img.shields.io/website/https/dockstore.org.svg)](https://dockstore.org)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/dockstore/dockstore?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)  
[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.2630727.svg)](https://doi.org/10.5281/zenodo.2630727)
[![Uptime Robot status](https://img.shields.io/uptimerobot/status/m779655940-a297af07d1cac2d6ad40c491.svg)]()
[![license](https://img.shields.io/hexpm/l/plug.svg?maxAge=2592000)](LICENSE)
[![CircleCI](https://circleci.com/gh/dockstore/dockstore/tree/develop.svg?style=svg)](https://circleci.com/gh/dockstore/dockstore/tree/develop)
[![Documentation Status](https://readthedocs.org/projects/dockstore/badge/?version=develop)](https://dockstore.readthedocs.io/en/develop/?badge=develop)


# Dockstore

Dockstore provides a place for users to share tools encapsulated in Docker and described with the Common 
Workflow Language (CWL), WDL (Workflow Description Language), or Nextflow. This enables scientists to share analytical 
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

### Configuration File

A basic Dockstore configuration file is available/should be created in `~/.dockstore/config` and contains the following
at minimum:
```
token = <your generated by the dockstore site>
server-url = https://www.dockstore.org/api
```

### Migration to Dockstore 1.7

1. Ensure that you are using Java 11. Java 8 (both Open and Oracle) will not work.

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
server-url = https://www.dockstore.org/api

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
[Travis-CI config](https://github.com/dockstore/dockstore/blob/develop/.travis.yml). In addition to the dependencies for 
Dockstore users, note the setup instructions for postgres. Specifically, you will need to have postgres installed 
and setup with the database user specified in [.travis.yml](https://github.com/dockstore/dockstore/blob/develop/.travis.yml#L26) (ideally, postgres is need only for integration tests but not unit tests).
Additional, OpenAPI generation requires swagger2openapi installed with `npm install -g swagger2openapi@2.11.16`.

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
2. The dockstore.yml is mostly a standard [Dropwizard configuration file](http://www.dropwizard.io/1.3.9/docs/manual/configuration.html). 
Refer to the linked document to setup httpClient and database. 
3. Start with `java -jar dockstore-webservice/target/dockstore-webservice-*.jar   server ~/.dockstore/dockstore.yml`
4. If you need integration with GitHub.com, Quay.io. or Bitbucket for your work, you will need to follow the appropriate 
sections below and then fill out the corresponding fields in your 
[dockstore.yml](https://github.com/dockstore/dockstore/blob/develop/dockstore.yml#L2). 

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

### Swagger Client Generation 

We use the swagger-codegen-maven-plugin to generate several sections of code which are not checked in. 
These include
1. All of swagger-java-client (talks to our webservice for the CLI)
2. All of swagger-java-quay-client (talks to Quay.io for our webservice)
3. The Tool Registry Server components (serves up the TRS endpoints)

To update these, you will need to point at a new version of the swagger.yaml provided by a service. For example, update the equivalent of [inputSpec](https://github.com/dockstore/dockstore/blob/0afe35682bdfb6fa7285b2acab8f80648346e835/dockstore-webservice/pom.xml#L854) in your branch.  

### Encrypted Documents for Travis-CI

Encrypted documents necessary for confidential testing are handled as indicated in the documents at Travis-CI for  
[files](https://docs.travis-ci.com/user/encrypting-files/#Encrypting-multiple-files) and [environment variables](https://docs.travis-ci.com/user/encryption-keys).

A convenience script is provided as encrypt.sh which will compress confidential files, encrypt them, and then update an encrypted archive on GitHub. Confidential files should also be added to .gitignore to prevent accidental check-in. The unencrypted secrets.tar should be privately distributed among members of the team that need to work with confidential data. When using this script you will likely want to alter the [CUSTOM\_DIR\_NAME](https://github.com/dockstore/dockstore/blob/0b59791440af6e3d383d1aede1774c0675b50404/encrypt.sh#L13). This is necessary since running the script will overwrite the existing encryption keys, instantly breaking existing builds using that key. Our current workaround is to use a new directory when providing a new bundle. 

### Adding Copyright header to all files with IntelliJ

To add copyright headers to all files with IntelliJ

1. Ensure the Copyright plugin is installed (Settings -> Plugins)
2. Create a new copyright profile matching existing copyright header found on all files, name it Dockstore (Settings -> Copyright -> Copyright Profiles -> Add New)
3. Set the default project copyright to Dockstore (Settings -> Copyright)

### Legacy Material

Additional documentation on developing Dockstore is available at [legacy.md](https://github.com/dockstore/dockstore/blob/develop/legacy.md)
