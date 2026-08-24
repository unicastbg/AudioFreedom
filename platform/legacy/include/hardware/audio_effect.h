/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <stdint.h>
#include <system/audio_effect.h>

#define EFFECT_MAKE_API_VERSION(major, minor) (((major) << 16) | ((minor) & 0xFFFF))
#define EFFECT_CONTROL_API_VERSION EFFECT_MAKE_API_VERSION(2, 0)
#define EFFECT_LIBRARY_API_VERSION EFFECT_MAKE_API_VERSION(3, 0)
#define AUDIO_EFFECT_LIBRARY_TAG \
    ((('A') << 24) | (('E') << 16) | (('L') << 8) | ('T'))

typedef struct effect_interface_s** effect_handle_t;

struct effect_interface_s {
    int32_t (*process)(effect_handle_t self, audio_buffer_t* in_buffer,
                       audio_buffer_t* out_buffer);
    int32_t (*command)(effect_handle_t self, uint32_t command_code,
                       uint32_t command_size, void* command_data,
                       uint32_t* reply_size, void* reply_data);
    int32_t (*get_descriptor)(effect_handle_t self,
                              effect_descriptor_t* descriptor);
    int32_t (*process_reverse)(effect_handle_t self, audio_buffer_t* in_buffer,
                               audio_buffer_t* out_buffer);
};

typedef struct audio_effect_library_s {
    uint32_t tag;
    uint32_t version;
    const char* name;
    const char* implementor;
    int32_t (*create_effect)(const effect_uuid_t* uuid, int32_t session_id,
                             int32_t io_id, effect_handle_t* handle);
    int32_t (*release_effect)(effect_handle_t handle);
    int32_t (*get_descriptor)(const effect_uuid_t* uuid,
                              effect_descriptor_t* descriptor);
    int32_t (*create_effect_3_1)(const effect_uuid_t* uuid, int32_t session_id,
                                 int32_t io_id, int32_t device_id,
                                 effect_handle_t* handle);
} audio_effect_library_t;

#define AUDIO_EFFECT_LIBRARY_INFO_SYM AELI
