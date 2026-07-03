// Copyright 2018 Alibaba Group
// Licensed under the Apache License, Version 2.0.
//
// Created by ruoyi.sjd on 2025/4/18.
// Modified by Termux Launcher on 2026/06/24: validate UTF-8 continuation bytes before emitting a chunk, so a
// malformed multibyte sequence produced by the model (e.g. an eagle/byte-BPE model emitting only
// 3 of an emoji's 4 bytes followed by ',') can never reach JNI NewStringUTF, which aborts the
// process (SIGABRT) on invalid Modified UTF-8. Malformed lead bytes are dropped instead.
//
#include <functional>
#include <string>
namespace mls {
class Utf8StreamProcessor {
public:
    explicit Utf8StreamProcessor(std::function<void(const std::string &)> callback)
            : callback(std::move(callback)) {}
    void processStream(const char *str, size_t len) {
        utf8Buffer.append(str, len);

        size_t i = 0;
        std::string completeChars;
        while (i < utf8Buffer.size()) {
            int length = utf8CharLength(static_cast<unsigned char>(utf8Buffer[i]));
            if (length == 0) {
                // Orphan/invalid lead byte: drop it rather than emit invalid UTF-8.
                i += 1;
                continue;
            }
            if (i + length > utf8Buffer.size()) {
                // Incomplete trailing sequence: keep it buffered for the next chunk.
                break;
            }
            bool valid = true;
            for (int k = 1; k < length; ++k) {
                if ((static_cast<unsigned char>(utf8Buffer[i + k]) & 0xC0) != 0x80) {
                    valid = false;
                    break;
                }
            }
            if (!valid) {
                // Malformed continuation (e.g. truncated emoji): drop the lead byte.
                i += 1;
                continue;
            }
            completeChars.append(utf8Buffer, i, length);
            i += length;
        }
        utf8Buffer = utf8Buffer.substr(i);
        if (!completeChars.empty()) {
            callback(completeChars);
        }
    }

    static int utf8CharLength(unsigned char byte) {
        if ((byte & 0x80) == 0) return 1;
        if ((byte & 0xE0) == 0xC0) return 2;
        if ((byte & 0xF0) == 0xE0) return 3;
        if ((byte & 0xF8) == 0xF0) return 4;
        return 0;
    }

private:
    std::string utf8Buffer;
    std::function<void(const std::string &)> callback;
};
}
