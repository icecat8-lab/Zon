cmake_minimum_required(VERSION 3.22.1)

project("zonengine")

add_library(
        zonengine
        SHARED
        native-lib.cpp
)

find_library(
        log-lib
        log
)

find_package(ZLIB REQUIRED)

target_link_libraries(
        zonengine
        ${log-lib}
        ZLIB::ZLIB
)
