LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

$(info $(shell ($(LOCAL_PATH)/gradlew assembleRelease -p $(LOCAL_PATH))))
$(info $(shell ($(LOCAL_PATH)/prepare.sh $(PRODUCT_OUT))))

LOCAL_MODULE := TerminalEmulator
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := APPS
LOCAL_SRC_FILES := $(LOCAL_MODULE).apk
LOCAL_CERTIFICATE := platform
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)

include $(BUILD_PREBUILT)
