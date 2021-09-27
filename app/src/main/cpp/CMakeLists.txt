cmake_minimum_required(VERSION 3.10.2)

project("native")

include_directories(prebuilt)

# badvpn static lib
add_library(badvpn
        STATIC
        IMPORTED)
set_target_properties(
        badvpn
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/libbadvpn.a)

# pegasocks static lib
add_library(pegasocks
        STATIC
        IMPORTED)
set_target_properties(
        pegasocks
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/libpegasocks.a)

# native
add_library(native
        SHARED
        native.cpp)

# log-lib
find_library(log-lib
        log)

target_link_libraries(
        native
        badvpn
        pegasocks
        ${log-lib})