FROM ubuntu:24.04

RUN apt-get update && \
    apt-get install -y git python3 binutils build-essential \
     maven zlib1g-dev linux-tools-common linux-tools-generic 

# PERF
RUN apt-get update && apt-get install -y \
    pkg-config libtraceevent-dev libelf-dev libdw-dev \
    flex bison

RUN git clone --depth 1 https://github.com/torvalds/linux.git && \
    make -C linux/tools/perf \
    NO_JEVENTS=1 NO_LIBBABELTRACE=1 NO_LIBCAPSTONE=1 \
    NO_LIBPFM=1 NO_LIBNUMA=1 NO_LIBPERL=1 NO_PYTHON=1 \
    NO_SDT=1 NO_SLANG=1 -j$(nproc) && \
    cp linux/tools/perf/perf /usr/local/bin/


WORKDIR /workspace

RUN git clone https://github.com/JacobSalvi/graal-analysis.git
RUN git clone https://github.com/graalvm/mx.git
RUN git clone https://github.com/JacobSalvi/graal.git && cd graal && git switch feature/perf-match 

ENV PATH="/workspace/mx:${PATH}"

RUN mx -y fetch-jdk default

RUN cd graal/substratevm && mx build

COPY ./Control Control
RUN cd Control && mvn package

# Actually run the native image now.
WORKDIR graal-analysis
ENV JAVA_HOME=/workspace/graal/sdk/latest_graalvm_home/

RUN apt update && apt install -y libelf1 libdebuginfod1

CMD ["./auto.sh", "exp_f2", "-jar /workspace/Control/target/Control-1.0-SNAPSHOT.jar", "100000 17"]

