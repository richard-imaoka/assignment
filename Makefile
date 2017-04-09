.PHONY: docker-stage

docker-stage:
	sbt docker:stage

docker-publishLocal: docker-stage
	sbt docker:publishLocal
