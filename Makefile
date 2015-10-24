.PHONY: clean cleanall build

clean:
	rm -rf build/

cleanall:
	$(MAKE) clean
	docker rmi -f platypus
	docker rmi -f scp-nexus

platypus: Dockerfile.platypus
	docker build -t platypus -f Dockerfile.platypus .
	@echo '==> platypus-nexus plugin build complete, pulling artefacts'
	docker run -v $(PWD)/build:/build:z --rm platypus bash -c \
		'cp -r /nexus-platypus/platypus/target/* /build && chown -R 1000:1000 /build'
	@echo '==> artefacts pull done'

plugin: Dockerfile.plugin
	(cd build/ &&\
	rm -f Dockerfile &&\
	cp ../Dockerfile.plugin Dockerfile &&\
	docker build -t scp-nexus .)
	@echo '==> scp-nexus image build complete'

launch:
	@echo '=> launching queue'
	docker run -dp 80:80 --name queue nginx
	@echo '=> launching scp-nexus'
	docker run -dp 8081:8081 --name nexus --link queue scp-nexus
	@echo '=> launch done'

stopclean:
	@echo '=> stopping and cleaning up'
	docker stop nexus queue
	docker rm nexus queue
	@echo '=> cleanup done'

build:
	mkdir -p build

all:
	$(MAKE) build
	$(MAKE) platypus
	$(MAKE) plugin
	echo '==> image build complete!'
