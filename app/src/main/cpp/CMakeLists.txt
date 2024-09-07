# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_STANDARD 90)

include_directories(prboom/include)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Ofast -ffast-math -DNORMALUNIX -DLINUX -DHAVE_CONFIG_H -DHAVE_NET")


# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
        dvr

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        prboom/am_map.c
        prboom/m_cheat.c
        prboom/p_lights.c
        prboom/p_user.c
        prboom/sounds.c
        prboom/hu_lib.c
        prboom/md5.c
        prboom/p_map.c
        prboom/r_bsp.c
        prboom/s_sound.c
        prboom/d_deh.c
        prboom/hu_stuff.c
        prboom/m_menu.c
        prboom/p_maputl.c
        prboom/r_data.c
        prboom/st_lib.c
        prboom/d_items.c
        prboom/m_misc.c
        prboom/p_mobj.c
        prboom/r_demo.c
        prboom/st_stuff.c
        prboom/d_main.c
        prboom/info.c
        prboom/p_plats.c
        prboom/r_draw.c
        prboom/tables.c
        prboom/doomdef.c
        prboom/m_random.c
        prboom/p_pspr.c
        prboom/r_filter.c
        prboom/version.c
        prboom/doomstat.c
        prboom/p_ceilng.c
        prboom/p_saveg.c
        prboom/r_fps.c
        prboom/v_video.c
        prboom/p_checksum.c
        prboom/p_setup.c
        prboom/r_main.c
        prboom/wi_stuff.c
        prboom/dstrings.c
        prboom/p_doors.c
        prboom/p_sight.c
        prboom/r_patch.c
        prboom/w_memcache.c
        prboom/f_finale.c
        prboom/p_enemy.c
        prboom/p_spec.c
        prboom/r_plane.c
        prboom/w_mmap.c
        prboom/f_wipe.c
        prboom/lprintf.c
        prboom/p_floor.c
        prboom/p_switch.c
        prboom/r_segs.c
        prboom/w_wad.c
        prboom/g_game.c
        prboom/m_argv.c
        prboom/p_genlin.c
        prboom/p_telept.c
        prboom/r_sky.c
        prboom/z_bmalloc.c
        prboom/m_bbox.c
        prboom/p_inter.c
        prboom/p_tick.c
        prboom/r_things.c
        prboom/z_zone.c
        prboom/d_client.c
        prboom/i_video.c
        prboom/i_network.c
        prboom/i_system.c
        prboom/i_main.c
        prboom/i_sound.c
        prboom/jni_doom.c
        prboom/mmus2mid.c
        prboom/pcm2wav.c
)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

find_library( # Sets the name of the path variable.
        android-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        android)


# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        dvr
        ${android-lib}

        android
        jnigraphics
        EGL
        OpenSLES
        GLESv2

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})
