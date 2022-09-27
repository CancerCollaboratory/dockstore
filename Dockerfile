FROM eclipse-temurin:17.0.3_7-jdk-focal

# wipe them out, all of them, to reduce CVEs
RUN apt-get purge -y -- *python*  && apt-get -y autoremove

# Update the APT cache
# prepare for Java download
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends
# note locale settings seem redundant, temurin already has en_US.UTF-8 set
#    locales \
#    && apt-get clean \
#    && rm -rf /var/lib/apt/lists/* \
#    && localedef -i en_US -c -f UTF-8 -A /usr/share/locale/locale.alias en_US.UTF-8
# ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

# copy the jar not ending in 's', to make sure we get don't get the one ending in 'sources'
COPY dockstore-webservice/target/dockstore-webservice*[^s].jar /home

RUN mkdir /dockstore_logs && chmod a+rx /dockstore_logs

# Install aide, file integrity verification
RUN apt install cron aide aide-common -y --no-install-recommends && aideinit
RUN update-aide.conf && cp /var/lib/aide/aide.conf.autogenerated /etc/aide/aide.conf
# Ignore these directories
RUN echo "!/dockstore_logs\n!/var/log\n!/tmp!/var/spool\n" >> /etc/aide/aide.conf
# Add a script to send daily reports to dockstore-security lambda
RUN echo "#!/bin/bash\nset -e\n\nset -C\necho \""{\\\"aide-report\\\": {\\\"hostname\\\": \\\"\$\(hostname\)\\\", \\\"report\\\": \\\"\$\(aide -c /etc/aide/aide.conf -u\; cp /var/lib/aide/aide.db.new /var/lib/aide/aide.db\)\\\"}}"\" | curl -X POST https://api.dockstore-security.org/csp-report --data-binary @-" > /etc/cron.daily/aide
RUN chmod a+x /etc/cron.daily/aide
RUN rm /etc/cron.daily/apt-compat /etc/cron.daily/dpkg
RUN aide -c /etc/aide/aide.conf --update || true
RUN cp /var/lib/aide/aide.db.new /var/lib/aide/aide.db

CMD ["/home/init_webservice.sh"]

