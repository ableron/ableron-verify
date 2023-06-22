#TODO: Move to gradle:8-jdk20 once it is available
FROM gradle:8-jdk19
COPY . /opt/app
WORKDIR /opt/app

ENTRYPOINT ["gradle", "bootRun"]
