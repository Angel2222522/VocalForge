cmake_minimum_required(VERSION 3.22.1)
project(vocalforge_dsp)

add_library(vocalforge_dsp SHARED native_dsp.cpp)

find_library(log-lib log)
target_compile_features(vocalforge_dsp PRIVATE cxx_std_17)
target_compile_options(vocalforge_dsp PRIVATE -O3 -ffast-math -fno-math-errno)
target_link_libraries(vocalforge_dsp ${log-lib})
