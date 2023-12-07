FROM rockylinux:8
COPY rpm/target/rpm/com.teragrep-aer_01/RPMS/noarch/com.teragrep-aer_01-*.rpm /rpm/
RUN dnf -y install /rpm/*.rpm && rm -f /dnf/*.rpm && dnf clean all
WORKDIR /opt/teragrep/aer_01
ENTRYPOINT [ "/usr/bin/java", "-jar", "/opt/teragrep/aer_01/lib/aer_01.jar" ]
CMD [""]
