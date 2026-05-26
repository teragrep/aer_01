# Container Assembly for Teragrep
# (C) 2026 Suomen Kanuuna Oy
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#
#
# Additional permission under GNU Affero General Public License version 3
# section 7
#
# If you modify this Program, or any covered work, by linking or combining it
# with other code, such other code is not for that reason alone subject to any
# of the requirements of the GNU Affero GPL version 3 as long as this Program
# is the same Program as licensed from Suomen Kanuuna Oy without any additional
# modifications.
#
# Supplemented terms under GNU Affero General Public License version 3
# section 7
#
# Origin of the software must be attributed to Suomen Kanuuna Oy. Any modified
# versions must be marked as "Modified version of" The Program.
#
# Names of the licensors and authors may not be used for publicity purposes.
#
# No rights are granted for use of trade names, trademarks, or service marks
# which are in The Program if any.
#
# Licensee must indemnify licensors and authors for any liability that these
# contractual assumptions impose on licensors and authors.
#
# To the extent this program is licensed as part of the Commercial versions of
# Teragrep, the applicable Commercial License may apply to this file if you as
# a licensee so wish it.
#

FROM rockylinux/rockylinux:9-ubi-micro AS runtime_image
FROM rockylinux/rockylinux:9-ubi AS assembly_container

# assembly tools
RUN dnf install -y rpm-build java-21-openjdk-devel java-21-openjdk-jmods maven

# create microjre
COPY artifact/com.teragrep-aer_01-*.rpm /artifact/
RUN dnf install -y /artifact/com.teragrep-aer_01-*.rpm
COPY container/microjre.pom.xml /container/
WORKDIR /container
RUN mvn -B -f microjre.pom.xml clean package

# patch runtime_image
RUN mkdir -p /sysroot
COPY --from=runtime_image / /sysroot
RUN dnf install --releasever 9 --setopt install_weak_deps=false --nodocs --installroot /sysroot -y /container/target/rpm/com.teragrep-aer_01_microjre/RPMS/x86_64/com.teragrep-aer_01_microjre-*.rpm /artifact/com.teragrep-aer_01-*.rpm
RUN dnf --installroot /sysroot clean all

# switch to runtime
FROM scratch
COPY --from=assembly_container /sysroot /
USER srv-aer_01
WORKDIR /opt/teragrep/aer_01
ENTRYPOINT [ "/opt/teragrep/aer_01_microjre/bin/java", "-Dconfig.source=environment", "-jar", "/opt/teragrep/aer_01/lib/aer_01.jar" ]
CMD [""]
