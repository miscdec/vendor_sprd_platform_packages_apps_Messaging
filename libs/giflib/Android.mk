LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libgif-ex

LOCAL_SRC_FILES := \
    dgif_lib.c     \
    egif_lib.c     \
    gifalloc.c     \
    gif_err.c      \
    gif_hash.c     \
    openbsd-reallocarray.c    \
    quantize.c

LOCAL_CFLAGS += -Wno-format -Wno-sign-compare -Wno-unused-parameter -DHAVE_CONFIG_H
LOCAL_SDK_VERSION := 8
LOCAL_NDK_STL_VARIANT := c++_static # LLVM libc++
$(info entry build giflib static library)
include $(BUILD_STATIC_LIBRARY)
