cmake_minimum_required(VERSION 3.22.1)

project(aacbridge-jni)

add_library(
aacbridge-jni
SHARED
llama_jni.cpp
)

find_library(
log-lib
log
)

add_library(
libllama
SHARED
IMPORTED
)

set_target_properties(
libllama
PROPERTIES
IMPORTED_LOCATION
${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libllama.so
)

target_include_directories(
aacbridge-jni
PRIVATE
${CMAKE_SOURCE_DIR}/include
)

target_link_libraries(
aacbridge-jni
libllama
${log-lib}
)
