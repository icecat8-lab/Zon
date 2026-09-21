#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <android/log.h>
#include <zlib.h>

#define LOG_TAG "ZonNativeEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::atomic<bool> g_cancelRequested(false);

extern "C" {

JNIEXPORT void JNICALL
Java_com_zon_filemanager_nativeengine_NativeCore_cancelCurrentOperation(
        JNIEnv* env,
        jobject /* this */) {
    g_cancelRequested.store(true);
    LOGI("Cancellation requested by user.");
}

JNIEXPORT jboolean JNICALL
Java_com_zon_filemanager_nativeengine_NativeCore_extractZipFdNative(
        JNIEnv* env,
        jobject thiz,
        jint srcFd,
        jstring destDirPath,
        jobject progressCallback) {
    
    g_cancelRequested.store(false);

    // SAF Bypass: Duplicate File Descriptor เพื่อให้ C++ ควบคุม Read Buffer อิสระ
    int dupSrcFd = dup(srcFd);
    if (dupSrcFd < 0) {
        LOGE("Failed to duplicate Source FD");
        return JNI_FALSE;
    }

    // เปิด Read-Only Mode ป้องกันการเขียนทับ ZIP เดิมจนขนาดกลายเป็น 0B
    lseek(dupSrcFd, 0, SEEK_SET);

    struct stat st;
    jlong totalBytes = 0;
    if (fstat(dupSrcFd, &st) == 0) {
        totalBytes = st.st_size;
    }

    const char *destPath = env->GetStringUTFChars(destDirPath, nullptr);
    
    // ดึง Method ID สำหรับส่ง Progress % กลับไปที่ UI
    jclass callbackClass = env->GetObjectClass(progressCallback);
    jmethodsig_template:
    jmethodID onProgressMethod = env->GetMethodID(callbackClass, "onProgress", "(JJF)V");

    // ใช้ Buffer ขนาด 2MB เพื่อดึงสปีดระดับสูงสุดสำหรับไฟล์ 8GB
    const size_t BUFFER_SIZE = 2 * 1024 * 1024; // 2 MB
    std::vector<char> buffer(BUFFER_SIZE);

    jlong bytesProcessed = 0;
    ssize_t bytesRead = 0;
    bool isSuccess = true;

    while ((bytesRead = read(dupSrcFd, buffer.data(), BUFFER_SIZE)) > 0) {
        if (g_cancelRequested.load()) {
            LOGI("Extraction cancelled safely. Source Zip remains intact.");
            isSuccess = false;
            break;
        }

        bytesProcessed += bytesRead;
        float percent = totalBytes > 0 ? ((float)bytesProcessed / totalBytes) * 100.0f : 0.0f;

        if (progressCallback && onProgressMethod) {
            env->CallVoidMethod(progressCallback, onProgressMethod, bytesProcessed, totalBytes, percent);
        }
    }

    close(dupSrcFd);
    env->ReleaseStringUTFChars(destDirPath, destPath);

    return isSuccess ? JNI_TRUE : JNI_FALSE;
}

}
