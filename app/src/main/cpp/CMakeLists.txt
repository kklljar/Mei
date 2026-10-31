cmake_minimum_required(VERSION 3.22.1)
project(mei_beatnet LANGUAGES C CXX)

# BeatNet transition inference ships native code; the smart-transition deck switcher
# itself stays out of this branch's single-player core.
add_library(mei_beatnet SHARED beatnet_inference.cpp)
target_compile_features(mei_beatnet PRIVATE cxx_std_17)
target_compile_options(mei_beatnet PRIVATE -O3 -fno-math-errno)

add_subdirectory(usbdriver)
