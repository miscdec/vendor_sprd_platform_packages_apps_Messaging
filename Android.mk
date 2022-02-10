#  Copyright (C) 2015 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

include $(CLEAR_VARS)
# cmcc
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += sms-plus:libs/cmcc/sms-plus-7.4.jar

# cucc
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += libsamaplocation:libs/cucc/AMap_Location.jar
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += libscallme:libs/cucc/unicom_callme.jar
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += libsrichmedia:libs/cucc/unicom_richmedia.jar
#LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += libsrhino:libs/cucc/rhino-1.7.7.1.jar

# ctcc
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += libsims:libs/ctcc/ims.jar

include $(BUILD_MULTI_PREBUILT)
include $(CLEAR_VARS)

ifeq (user,$(strip $(TARGET_BUILD_VARIANT)))
    LOCAL_MANIFEST_FILE := overlay/androidmanifest/release/AndroidManifest.xml
else
    LOCAL_MANIFEST_FILE := overlay/androidmanifest/debug/AndroidManifest.xml
endif

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_SRC_FILES += src/com/sprd/gallery3d/aidl/IFloatWindowController.aidl

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
LOCAL_USE_AAPT2 := true

LOCAL_STATIC_ANDROID_LIBRARIES := \
    androidx.core_core \
    androidx.media_media \
    androidx.legacy_legacy-support-core-utils \
    androidx.legacy_legacy-support-core-ui \
    androidx.fragment_fragment \
    androidx.appcompat_appcompat \
    androidx.palette_palette \
    androidx.recyclerview_recyclerview \
    androidx.legacy_legacy-support-v13 \
    colorpicker \
    libchips \
    libphotoviewer

LOCAL_STATIC_JAVA_LIBRARIES := \
    androidx.annotation_annotation \
    android-common \
    android-common-framesequence \
    com.android.vcard \
    guava \
    libphonenumber \
    glide

# cmcc
LOCAL_STATIC_JAVA_LIBRARIES += sms-plus

#add for cucc smart message start
LOCAL_STATIC_JAVA_LIBRARIES += libsamaplocation
LOCAL_STATIC_JAVA_LIBRARIES += libscallme
LOCAL_STATIC_JAVA_LIBRARIES += libsrichmedia
#LOCAL_STATIC_JAVA_LIBRARIES += libsrhino
#add for cucc smart message end

#add for CTC smart message start
LOCAL_STATIC_JAVA_LIBRARIES += libsims
#add for CTC smart message end

LOCAL_JAVA_LIBRARIES := telephony-common

LOCAL_JAVA_LIBRARIES += radio_interactor_common
LOCAL_JAVA_LIBRARIES += unisoc_ims_common

include $(LOCAL_PATH)/version.mk

LOCAL_AAPT_FLAGS += --version-name "$(version_name_package)"
LOCAL_AAPT_FLAGS += --version-code $(version_code_package)

ifdef TARGET_BUILD_APPS
    LOCAL_JNI_SHARED_LIBRARIES := libframesequence libgiftranscode
else
    LOCAL_REQUIRED_MODULES:= libframesequence libgiftranscode
endif

LOCAL_PROGUARD_FLAGS := -ignorewarnings -include build/core/proguard_basic_keeps.flags

LOCAL_PROGUARD_ENABLED := obfuscation optimization

LOCAL_PROGUARD_FLAG_FILES := proguard.flags
#ifeq (eng,$(TARGET_BUILD_VARIANT))
    LOCAL_PROGUARD_FLAG_FILES += proguard-test.flags
#else
#    LOCAL_PROGUARD_FLAG_FILES += proguard-release.flags
#endif

LOCAL_PACKAGE_NAME := messaging

ifeq ($(strip $(TARGET_SIMULATOR)),true)
LOCAL_PRODUCT_MODULE := true
endif

# smart message, begin
ifeq ($(strip $(TARGET_ARCH)),arm64)
LOCAL_PREBUILT_JNI_LIBS := \
    libs/ctcc/arm64-v8a/libcallme.so
else
ifeq ($(strip $(TARGET_ARCH)),x86_64)
LOCAL_PREBUILT_JNI_LIBS := \
    libs/ctcc/x86_64/libcallme.so
else
ifeq ($(strip $(TARGET_ARCH)),arm)
LOCAL_PREBUILT_JNI_LIBS := \
    libs/ctcc/armeabi-v7a/libcallme.so
else
LOCAL_PREBUILT_JNI_LIBS := \
    libs/ctcc/x86/libcallme.so
endif
endif
endif
# smart message, end

# forbidding pre-opt
#LOCAL_DEX_PREOPT := false

LOCAL_CERTIFICATE := platform

#LOCAL_SDK_VERSION := current

LOCAL_PRIVATE_PLATFORM_APIS := true

include $(BUILD_PACKAGE)

include $(call all-makefiles-under, $(LOCAL_PATH))
