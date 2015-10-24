.PHONY: clean cleanall build

clean:
	rm -rf build/

cleanall:
	$(MAKE) clean
	docker rmi -f platypus
	docker rmi -f scp-nexus

platypus: Dockerfile.platypus
	docker build -t platypus -f Dockerfile.platypus .
	docker run -v $(PWD)/build:/build:z platypus bash -c 'cp -r /nexus-platypus/platypus/target/* /build'
	echo '==> platypus-nexus plugin build complete'

plugin: Dockerfile.plugin
	(cd build/ &&\
	rm -f Dockerfile &&\
	cp ../Dockerfile.plugin Dockerfile &&\
	docker build -t scp-nexus .)
	echo '==> scp-nexus image build complete'

build:
	mkdir -p build

all:
	$(MAKE) build
	$(MAKE) platypus
	$(MAKE) plugin
	echo '==> image build complete!'
