[![CircleCI](https://circleci.com/gh/dockstore/dockstore.svg?style=svg)](https://circleci.com/gh/dockstore/dockstore)
[![codecov](https://codecov.io/gh/dockstore/dockstore/branch/develop/graph/badge.svg)](https://codecov.io/gh/dockstore/dockstore)
[![Website](https://img.shields.io/website/https/dockstore.org.svg)](https://dockstore.org)

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.7259426.svg)](https://doi.org/10.5281/zenodo.7259426)
[![Uptime Robot status](https://img.shields.io/uptimerobot/status/m779655940-a297af07d1cac2d6ad40c491.svg)]()
[![license](https://img.shields.io/hexpm/l/plug.svg?maxAge=2592000)](LICENSE)
[![Documentation Status](https://readthedocs.org/projects/dockstore/badge/?version=stable)](https://dockstore.readthedocs.io/en/stable/?badge=stable)


# Dockstore

Dockstore provides a place for users to share tools encapsulated in Docker and described with the Common 
Workflow Language (CWL), WDL (Workflow Description Language), Nextflow, or Galaxy. This enables scientists to share analytical 
workflows so that they are  machine readable as well as runnable in a variety of environments. While the 
Dockstore is focused on serving researchers in the biosciences, the combination of Docker + workflow languages can be used by 
anyone to describe the tools and services in their Docker images in a standardized, machine-readable way.  
Dockstore is also a leading implementor of the GA4GH API standard for container registries, [TRS](https://www.ga4gh.org/news/tool-registry-service-api-enabling-an-interoperable-library-of-genomics-analysis-tools/). The usage of this is to enumerate the docker containers 
(from quay.io and docker hub) and the workflows (from github/bitbucket/local) that are available 
to users of Dockstore.org.

For the live site see [dockstore.org](https://dockstore.org)

This repo contains the web service component for Dockstore as well as collecting issues for the project as a whole. 

For the related web UI see the [dockstore-ui](https://github.com/dockstore/dockstore-ui2) project.
For the related CLI see the [cli](https://github.com/dockstore/cli) project.

## For Dockstore Users

The following section is useful for users of Dockstore (e.g. those that want to browse, register, and 
launch tools). 

After registering at [dockstore.org](https://dockstore.org), you will be able to download the Dockstore 
CLI at https://dockstore.org/onboarding

### Configuration File

A basic Dockstore configuration file is available/should be created in `~/.dockstore/config` and contains the following
at minimum:
```
token = <your token generated by the dockstore site>
server-url = https://www.dockstore.org/api
```

## For Dockstore Developers

The following section is useful for Dockstore developers (e.g. those that want to improve or fix the Dockstore web service and UI)

### Dependencies

The dependency environment for Dockstore is described by our 
[CircleCI config](https://github.com/dockstore/dockstore/blob/develop/.circleci/config.yml) or [docker compose](docker-compose.yml). In addition to the dependencies for 
Dockstore users, note the setup instructions for postgres. Specifically, you will need to have postgres installed 
and setup with the database user specified in [.circleci/config.yml](https://github.com/dockstore/dockstore/blob/1.11.10/.circleci/config.yml#L279) (ideally, postgres is needed only for integration tests but not unit tests).

### Building

As an alternative to the following commands, if you do not have Maven installed you can use the maven wrapper as a substitute. For example:

    ./mvnw clean install
    # instead of
    mvn clean install

If you maven build in the root directory this will build all modules:

    mvn clean install
    # or
    mvn clean install -Punit-tests
    
Consider the following if you need to build a specific version (such as in preparation for creating a tag for a release):

    mvnw clean install  -Dchangelist=.0-beta.5 #or whatever version you need 
    
If you're running tests on CircleCI (or otherwise have access to the confidential data bundle) Run them via:

    mvn clean install -Pintegration-tests
    
There are also certain categories for tests that they can be added to when writing new tests. 
Categories include:

1. `ToilCompatibleTest` are tests that can be run with our default cwltool and with Toil
2. `ConfidentialTest` are tests that require access to our confidential testing bundle (ask a member of the development team if you're on the team)

### Running Locally

You can also run it on your local computer but will need to setup postgres separately.

1. Fill in the template dockstore.yml and stash it somewhere outside the git repo (like ~/.dockstore)
2. The dockstore.yml is mostly a standard [Dropwizard configuration file](https://www.dropwizard.io/en/release-2.0.x/manual/configuration.html). 
Refer to the linked document to setup httpClient and database. 
3. Start with `java -jar dockstore-webservice/target/dockstore-webservice-*.jar   server ~/.dockstore/dockstore.yml`
4. If you need integration with GitHub.com, Quay.io. or Bitbucket for your work, you will need to follow the appropriate 
sections below and then fill out the corresponding fields in your 
[dockstore.yml](https://github.com/dockstore/dockstore/blob/develop/dockstore-integration-testing/src/test/resources/dockstore.yml). 

One alternative if you prefer running things in containers would be using [docker-compose](docker-compose.yml)

### View Swagger UI

The Swagger UI is reachable while the Dockstore webservice is running. This allows you to explore available web resources.

1. Browse to [http://localhost:8080/static/swagger-ui/index.html](http://localhost:8080/static/swagger-ui/index.html)

### Commits using `[skipTests]`
If you would like to save build minutes on CircleCI (particularly for changes that do not affect code), consider adding 
the `[skipTests]` tag to your commit message. When included in the most recent commit, a partial CI pipeline will be run,
consisting of only the build and unit tests.

`[skipTests]` acts as an alternative to `[skip ci]`, which is provided by CircleCI. This is because our automatic 
deployment process requires a build to be run on every tag.


## Development

### Coding Standards

[codestyle.xml](codestyle.xml) defines the coding style for Dockstore as an IntelliJ Code Style XML file that should be imported into IntelliJ IDE. 
We also have a matching [checkstyle.xml](checkstyle.xml) that can be imported into other IDEs and is run during the build.  

For users of Intellij or comparable IDEs, we also suggest loading the checkstyle.xml with a plugin in order to display warnings and errors while coding live rather than encountering them later when running a build. 

#### Installing git-secrets

Dockstore uses git-secrets to help make sure that keys and private data stay out
of the source tree. For information on installing it on your platform check <https://github.com/awslabs/git-secrets#id6> .

If you're on mac with homebrew use `brew install git-secrets`.

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
1. All of swagger-java-client (talks to our webservice for the CLI via Swagger 2.0)
2. All of openapi-java-client (talks to our webservice for the CLI, but in OpenAPI 3.0)
3. The Tool Registry Server components (serves up the TRS endpoints)

To update these, you will need to point at a new version of the swagger.yaml provided by a service. For example, update the equivalent of [inputSpec](https://github.com/dockstore/dockstore/blob/0afe35682bdfb6fa7285b2acab8f80648346e835/dockstore-webservice/pom.xml#L854) in your branch.  

### Encrypted Documents for CircleCI

Encrypted documents necessary for confidential testing are decrypted via [decrypt.sh](scripts/decrypt.sh) with access being granted to developers at UCSC and OICR.

A convenience script is provided as [encrypt.sh](encrypt.sh) which will compress confidential files, encrypt them, and then update an encrypted archive on GitHub. Confidential files should also be added to .gitignore to prevent accidental check-in. The unencrypted secrets.tar should be privately distributed among members of the team that need to work with confidential data. When using this script you will likely want to alter the [CUSTOM\_DIR\_NAME](https://github.com/dockstore/dockstore/blob/0b59791440af6e3d383d1aede1774c0675b50404/encrypt.sh#L13). This is necessary since running the script will overwrite the existing encryption keys, instantly breaking existing builds using that key. Our current workaround is to use a new directory when providing a new bundle. 

### Adding Copyright header to all files with IntelliJ

To add copyright headers to all files with IntelliJ

1. Ensure the Copyright plugin is installed (Settings -> Plugins)
2. Create a new copyright profile matching existing copyright header found on all files, name it Dockstore (Settings -> Copyright -> Copyright Profiles -> Add New)
3. Set the default project copyright to Dockstore (Settings -> Copyright)

[//]: # (DO_NOT_DELETE_START_MACOS_INSTRUCTIONS: This section is automatically generated by scripts/macos-instructions.sh)
### Setting up a Mac for Dockstore development
Install Docker (Be sure to click on 'Mac with Apple chip' if you have Apple silicon)
https://docs.docker.com/desktop/mac/install/

Install Brew
https://brew.sh/
```
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

Run 'git' to trigger the install of Xcode or the Command Line Tools which will install and or update git
https://developer.apple.com/forums/thread/672087?answerId=659036022#659036022
```
git
```
_(If that doesn't work install git manually https://git-scm.com/download/mac)_


Setup git user information
```
git config --global user.email "you@example.com"
git config --global user.name "Your Name"
```
[Read about git token requirements](https://github.blog/2020-12-15-token-authentication-requirements-for-git-operations/)

[Setup personal access token for git CLI](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token)

[It's helpful to cache your git personal access token](https://docs.github.com/en/get-started/getting-started-with-git/caching-your-github-credentials-in-git)

Install Hubflow
https://datasift.github.io/gitflow/TheHubFlowTools.html
```
git clone https://github.com/datasift/gitflow
cd gitflow
sudo ./install.sh
```

Install JDK 17
https://formulae.brew.sh/formula/openjdk@17
```
brew install openjdk@17
```
Download and install node.js
https://github.com/nvm-sh/nvm
```
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.1/install.sh | bash
```
Install git secrets
https://github.com/awslabs/git-secrets
```
brew install git-secrets
```
Install wget
```
brew install wget
```

Install jq
```
brew install jq
```
#### Build the webservice
(cd to where you cloned the dockstore/dockstore repo)
```
./mvnw clean install
```

#### Build the UI
(cd to where you cloned the dockstore/dockstore-ui2 repo)

Set up UI requirements
NOTE: You must use the --legacy-peer-deps switch due to using npm version 8.11.0 (> npm 6) 
for reasons mentioned in [this post](https://stackoverflow.com/questions/66239691/what-does-npm-install-legacy-peer-deps-do-exactly-when-is-it-recommended-wh)
```
npm ci --legacy-peer-deps
```

Run build
```
npm run build
```
#### Optional
Install IntelliJ _(if on Apple Silicon, select the .dmg (Apple Silicon), otherwise select .dmg(Intel)_

https://www.jetbrains.com/idea/download/#section=mac

Add the Scala plugin to IntelliJ
https://www.jetbrains.com/help/idea/managing-plugins.html

[//]: # (DO_NOT_DELETE_END_MACOS_INSTRUCTIONS: This section is automatically generated by scripts/macos-instructions.sh)


### Database Schema Documentation 

This is autogenerated at [https://dockstore.github.io/dockstore/](https://dockstore.github.io/dockstore/)


### Legacy Material

Additional documentation on developing Dockstore is available at [legacy.md](https://github.com/dockstore/dockstore/blob/develop/legacy.md)
