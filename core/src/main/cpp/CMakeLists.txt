cmake_minimum_required(VERSION 3.22.1)
project("llama-jni")

set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "llama.cpp: build examples")
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "llama.cpp: build tests")
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "llama.cpp: build server")

add_subdirectory(llama.cpp build-llama)

add_library(
        llama-jni
        SHARED
        llama-jni.cpp)

find_library(
        log-lib
        log)

target_include_directories(llama-jni PRIVATE
        llama.cpp/include
        llama.cpp/ggml/include)

target_link_libraries(
        llama-jni
        PRIVATE
        llama
        android
        ${log-lib})
