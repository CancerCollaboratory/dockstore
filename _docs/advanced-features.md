---
title: Advanced CLI Features
permalink: /docs/publisher-tutorials/advanced-features/
---
# Advanced CLI Features

## File Provisioning

As a convenience, the dockstore command-line can perform file provisioning for inputs and outputs.

File provisioning for some protocols like HTTP and FTP is built-in while other protocols are handled by plugins as documented [here](https://github.com/ga4gh/dockstore/tree/develop/dockstore-file-plugin-parent).

To illustrate, for this [tool](https://dockstore.org/containers/quay.io/collaboratory/dockstore-tool-bamstats) we provide a couple of parameter files that can be used to parameterize a run of bamstats.

In the following JSON file, this file indicates for a CWL run that the input file should be present and readable at `/tmp/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam`. The output file will be copied to `/tmp/bamstats_report.zip` (which should be writeable).

```
{
  "bam_input": {
        "class": "File",
        "path": "/tmp/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam"
    },
    "bamstats_report": {
        "class": "File",
        "path": "/tmp/bamstats_report.zip"
    }
}
```

The Dockstore command-line allows you to specify that the input file can be at an HTTP(S) location, an FTP location, an AWS S3 location, a [synapse id](http://python-docs.synapse.org/#accessing-data), an [ICGC storage id](https://docs.icgc.org/download/guide/), or a [DRS URI](https://github.com/ga4gh/data-repository-service-schemas/issues/49) in place of that path. For example the following indicates that the input file will be downloaded under HTTP.

```
{
  "bam_input": {
        "class": "File",
        "path": "https://s3.amazonaws.com/oconnor-test-bucket/sample-data/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam"
    },
    "bamstats_report": {
        "class": "File",
        "path": "/tmp/bamstats_report.zip"
    }
}
```

Provisioning for output files works in the same way and has been tested with S3 output locations.

For some file provisioning methods, additional configuration may be required.

### AWS S3

For AWS S3, create a `~/.aws/credentials` file and a `~/.aws/config` file as documented at the following [location](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html#cli-config-files).

Get more information on the implementing plugin at [s3-plugin](https://github.com/dockstore/s3-plugin).

### ICGC Storage

For ICGC Storage, configure the location of the client using the configuration key `dockstore-file-icgc-storage-client-plugin.client` in `~/.dockstore/config` under the section `[dockstore-file-icgc-storage-client-plugin]`. Then configure the ICGC storage client as documented [here](https://docs.icgc.org/download/guide/#configuration).

Get more information on the implementing plugin at [icgc-storage-client-plugin](https://github.com/dockstore/icgc-storage-client-plugin).


### Synapse

For Synapse, you can add `synapse-api-key` and `synapse-user-name` to `~/.dockstore/config` under the section `[dockstore-file-synapse-plugin]`.

Get more information on the implementing plugin at [synapse-plugin
](https://github.com/dockstore/synapse-plugin).

### Data Object Service (DOS)

> For Dockstore 1.5.0+

Currently, no additional configuration is directly supported by the Data Object Service plugin.
However, specifying a DOS URI will lead to downloading a file by either built-in
support or by one of the plugins. If one of the plugins, that plugin may need to be
configured, e.g., if a DOS URI leads to downloading a file from AWS S3, then you 
may need to configure your AWS S3 plugin.

Get more information on the implementing plugin at [data-object-service-plugin](https://github.com/dockstore/data-object-service-plugin).


## Input File Cache

When developing or debugging tools, it can be time consuming (and space-consuming) to repeatedly download input files for your tools. A feature of the Dockstore CLI is the ability to cache input files locally so that they can be quickly re-used for multiple attempts at launching a tool.

This feature relies upon Linux [hard-linking](https://en.wikipedia.org/wiki/Hard_link) so when enabling this feature, it is important to ensure that the location of the cache directory (by default, at `~/.dockstore/cache/`) is on the same filesystem as the working directory where you intend on running your tools.

There are two configuration file keys that can be used to activate input file caching and to configure the location of the cache.  These are added (or changed) inside your configuration file at `~/.dockstore/config`.

```
use-cache = true
cache-dir =
```

The former is false by default and can be set to true in order to activate the cache.
The latter is `~/.dockstore/cache/` by default and can be set to any directory location.

## File Provision Retries

By default, Dockstore will attempt to download files up to three times. Control this with the `file-provision-retries` parameter inside `~/.dockstore/config`.

## Running CWL-runner with extra tags

When running a CWL tool, you may want to add additional parameters/flags to the `cwl-runner` command. You can do this by updating your dockstore config file (`~/.dockstore/config`).

As an example, adding the following line to your config file will stop cwl-runner from cleaning up (the Docker container and the temp directory as mounted on the host) and make it run in debug mode.

```
cwltool-extra-parameters: --debug, --leave-container, --leave-tmpdir
```

## Alternative CWL Launchers

By default, the dockstore CLI launches CWL tools/workflows using [cwltool](https://github.com/common-workflow-language/cwltool). However, we have an experimental integration with other launchers such as:
- [cwl-runner](http://www.commonwl.org/v1.0/CommandLineTool.html#Executing_CWL_documents_as_scripts)
- [Cromwell](https://cromwell.readthedocs.io/en/stable/) (For Dockstore 1.6.0+)

Keep in mind that there are a few differences in how locked-down the Docker execution environments are between the launchers, so a workflow that succeeds in one may not necessarily succeed in another.

You can test all the launchers by cloning the dockstore-tool-md5sum repository: `git clone git@github.com:briandoconnor/dockstore-tool-md5sum.git` and then test with cwl-runner, Cromwell, and cwltool using `dockstore tool launch --local-entry Dockstore.cwl --json test.json` after the required configurations have been made.

Even though it's the default, you can also explicitly use cwltool by adding the following to your `~/.dockstore/config`:
 ```
 cwlrunner: cwltool
 ```

### cwl-runner
If your workflow platform provides the cwl-runner alias as the platform's default CWL implementation, you can activate it by adding the following to your `~/.dockstore/config`:
```
cwlrunner: cwl-runner
```

### Cromwell (Beta)
> For Dockstore 1.6.0+


You can launch CWL tools/workflows using Cromwell by adding the following to your `~/.dockstore/config`:
```
cwlrunner: cromwell
```

Cromwell with CWL handles imports differently than cwltool with CWL. Cromwell requires imports of a workflow to be given in a zip directory, where the files are referenced relative to the root of the zip directory. With cwltool, the files imported are referenced relative to the file importing them. You can read more about how Cromwell handles imports [here](https://cromwell.readthedocs.io/en/stable/Imports/).

When launching local CWL workflows with Cromwell, we zip the directory where the primary descriptor file is located and use this zip file for imports. This way the imports are resolved relative to the primary descriptor. **You should store your descriptor files in a clean directory if you can.**

For remote launches, we download the zip directory as returned by the Dockstore API. Note that this should work for most cases where the primary descriptor is in the root directory of its git repository.


## WDL Launcher Configuration

By default, WDL tools/workflows will automatically be ran with [cromwell](https://github.com/broadinstitute/cromwell) 30.2.
Additionally, you can override the cromwell version in your `~/.dockstore/config` using:
```
cromwell-version = 34
```

You can test cromwell by cloning the dockstore-tool-md5sum repository: `git clone git@github.com:briandoconnor/dockstore-tool-md5sum.git` and then test using `dockstore tool launch --local-entry Dockstore.wdl --json test.wdl.json`

Note: The cromwell-version mentioned in `~/.dockstore/config` will also be used to specify the version of Cromwell used to launch CWL tools and workflows if you set `cwlrunner: cromwell`.

## Notifications
The Dockstore CLI has the ability to provide notifications via an HTTP post to a user-defined endpoint for the following steps:
- The beginning of input files provisioning
- The beginning of tool/workflow execution
- The beginning of output files provisioning
- Final launch completion

Additionally, it will also provide notifications when any of these steps have failed.

### Usage
- Define a webhook URL in the Dockstore config file with the "notifications" property like:
```
token: iamafakedockstoretoken
server-url: https://dockstore.org:8443
notifications: https://hooks.slack.com/services/aaa/bbb/ccc
```
- UUID can be generated or user-defined uuid in the dockstore launch command like:
```bash
dockstore tool launch --local-entry Dockstore.cwl --json test.json --uuid fakeUUID
```
- An HTTP post with a JSON payload will be sent to the url defined earlier that looks like:
```json
{
  "text": "someTextBasedOnMilestoneAndStatus",
  "username": "your linux username",
  "platform": "Dockstore CLI 1.4",
  "uuid": "someUserDefinedOrGeneratedUUID"
}
```

### Notes
- To disable notifications, simply remove the webhook URL from the Dockstore config file
- If the UUID is generated, the generated UUID will be displayed in beginning of the launch stdout
