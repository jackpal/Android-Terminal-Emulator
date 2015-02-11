bindir = assets
srcdir = assets-src
bin = $(bindir)/execpty-$(ARCH)

NDK = $(shell dirname `which ndk-build`)
ifeq ($(NDK),)
	check-ndk = ndk-not-found
else
	check-ndk =
endif

ifeq ($(ARCH),mips)
	TOOLCHAIN_DIR = $(NDK)/toolchains/mipsel-*/prebuilt/*/bin
endif
TOOLCHAIN_DIR ?= $(NDK)/toolchains/$(ARCH)-*/prebuilt/*/bin
CC = $(firstword $(shell echo $(TOOLCHAIN_DIR)/*-gcc))
STRIP = $(firstword $(shell echo $(TOOLCHAIN_DIR)/*-strip))

ifeq ($(ARCH),arm)
	SYSROOT = $(NDK)/platforms/android-3/arch-arm
endif
SYSROOT ?= $(NDK)/platforms/android-9/arch-$(ARCH)

all: $(check-ndk) arm x86 mips
arm: $(check-ndk)
	@$(MAKE) ARCH=arm _do_build
x86: $(check-ndk)
	@$(MAKE) ARCH=x86 _do_build
mips: $(check-ndk)
	@$(MAKE) ARCH=mips _do_build

ndk-not-found:
	$(error Android NDK not found. Make sure ndk-build is in your PATH)

_do_build: make_bindir $(bin)

make_bindir:
	mkdir -p $(bindir)

$(bindir)/%-$(ARCH) : $(srcdir)/%.c
	$(CC) --sysroot=$(SYSROOT) -static -o $@ $<
	$(STRIP) $@

clean:
	rm -f $(bindir)/*

.PHONY: clean make_bindir _do_build ndk-not-found arm x86 mips all
