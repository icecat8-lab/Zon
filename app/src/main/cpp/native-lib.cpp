#include <jni.h>
#include <string>
#include <vector>
#include <sys/stat.h>
#include <dirent.h>
#include <unistd.h>
#include <fcntl.h>
#include <android/log.h>

#define LOG_TAG "ZonNativeEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_zon_filemanager_nativeengine_NativeCore_getEngineVersion(
        JNIEnv* env,
        jobject /* this */) {
    std::string version = "Zon Native Engine v1.0.0 (High-Performance C++17)";
    LOGI("Native Engine Initialized: %s", version.c_str());
    return env->NewStringUTF(version.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_zon_filemanager_nativeengine_NativeCore_getFileSizeNative(
        JNIEnv* env,
        jobject /* this */,
        jstring filePath) {
    const char *nativePath = env->GetStringUTFChars(filePath, nullptr);
    struct stat st;
    jlong size = -1;
    
    if (stat(nativePath, &st) == 0) {
        size = st.st_size;
    } else {
        LOGE("Failed to get size for path: %s", nativePath);
    }
    
    env->ReleaseStringUTFChars(filePath, nativePath);
    return size;
}

JNIEXPORT jboolean JNICALL
Java_com_zon_filemanager_nativeengine_NativeCore_copyFileNative(
        JNIEnv* env,
        jobject /* this */,
        jstring srcPath,
        jstring destPath) {
    const char *src = env->GetStringUTFChars(srcPath, nullptr);
    const char *dest = env->GetStringUTFChars(destPath, nullptr);

    int srcFd = open(src, O_RDONLY);
    if (srcFd < 0) {
        LOGE("Cannot open source file: %s", src);
        env->ReleaseStringUTFChars(srcPath, src);
        env->ReleaseStringUTFChars(destPath, dest);
        return JNI_FALSE;
    }

    int destFd = open(dest, O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (destFd < 0) {
        LOGE("Cannot open destination file: %s", dest);
        close(srcFd);
        env->ReleaseStringUTFChars(srcPath, src);
        env->ReleaseStringUTFChars(destPath, dest);
        return JNI_FALSE;
    }

    char buffer[8192];
    ssize_t bytesRead;
    bool success = true;

    while ((bytesRead = read(srcFd, buffer, sizeof(buffer))) > 0) {
        if (write(destFd, buffer, bytesRead) != bytesRead) {
            LOGE("Write error on file copy");
            success = false;
            break;
        }
    }

    close(srcFd);
    close(destFd);

    env->ReleaseStringUTFChars(srcPath, src);
    env->ReleaseStringUTFChars(destPath, dest);

    return success ? JNI_TRUE : JNI_FALSE;
}

}
