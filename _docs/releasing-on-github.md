At this point, we've successfully created our tool in Docker, tested it, written a workflow language descriptor that describes how to run it, and tested running this via the Dockstore command line.  All of this work has been done locally, so if we encounter problems along the way it is fast to perform debug cycles, fixing problems as we go.  At this point, we're confident that the tool is ready to share with others and bug free.  It's time to release `1.25-6_1.1`

Releasing will tag your GitHub repository with a version tag so you always can get back to this particular release.  I'm going to use the tag `1.25-6_1.1` which I'll need to update in my Docker image tag and also my CWL file. Note that if you're following the tutorial using a forked version of the bamstats repo, your organization name should be different. GitHub makes it very easy to release:

![Release](/assets/images/docs/release.png)

I click on "releases" in my forked version of the GitHub project [page](https://github.com/CancerCollaboratory/dockstore-tool-bamstats) and then follow the directions to create a new release. Simple as that!

**Tip:** [HubFlow](https://datasift.github.io/gitflow/) is an excellent way to manage the lifecycle of releases on GitHub.  Take a look!
