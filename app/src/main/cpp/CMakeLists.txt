cmake_minimum_required(VERSION 3.10.2)
project("native-lib")

# log-lib
find_library(log-lib
        log)

# native-lib
add_library(native-lib
        SHARED
        native-lib.cpp)

target_include_directories(native-lib PRIVATE
        ${CMAKE_SOURCE_DIR}/prebuilt/include
        ${CMAKE_SOURCE_DIR}/prebuilt/include/${ANDROID_ABI})

target_link_libraries(native-lib
        ${CMAKE_SOURCE_DIR}/prebuilt/lib/${ANDROID_ABI}/libtun2socks.a

        ${CMAKE_SOURCE_DIR}/prebuilt/lib/${ANDROID_ABI}/libpegas.a

        ${CMAKE_SOURCE_DIR}/prebuilt/lib/${ANDROID_ABI}/libevent.a
        ${CMAKE_SOURCE_DIR}/prebuilt/lib/${ANDROID_ABI}/libevent_mbedtls.a

        ${CMAKE_SOURCE_DIR}/prebuilt/lib/${ANDROID_ABI}/libmbedtls.a
        ${CMAKE_SOURCE_DIR}/prebuilt/lib/${ANDROID_ABI}/libmbedx509.a
        ${CMAKE_SOURCE_DIR}/prebuilt/lib/${ANDROID_ABI}/libmbedcrypto.a

        ${log-lib})
