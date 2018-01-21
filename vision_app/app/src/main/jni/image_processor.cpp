#include "image_processor.h"

#include <stdlib.h>
#include <algorithm>

#include <GLES2/gl2.h>
#include <EGL/egl.h>

#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/core/ocl.hpp>
#include <opencv2/imgcodecs.hpp>

#include "common.hpp"

enum DisplayMode
{
    DISP_MODE_RAW = 0,
    DISP_MODE_THRESH = 1,
    DISP_MODE_TARGETS = 2,
    DISP_MODE_TARGETS_PLUS = 3
};

struct TargetInfo
{
    double centroid_x;
    double centroid_y;
    double width;
    double height;
    std::vector<cv::Point> points;
};

std::vector<TargetInfo> processImpl(int w, int h, int texOut, DisplayMode mode,
                                    int h_min, int h_max, int s_min, int s_max,
                                    int v_min, int v_max, cv::Mat *&display)
{
    LOGD("Image is %d x %d", w, h);
    LOGD("H %d-%d S %d-%d V %d-%d", h_min, h_max, s_min, s_max, v_min, v_max);
    int64_t t;

    //Creates Pixel Array (Mat): https://docs.opencv.org/3.1.0/d3/d63/classcv_1_1Mat.html
    static cv::Mat input;
    input.create(h, w, CV_8UC4);

    //Retrieves the Image Bitmap from the 'OpenGL Buffer'
    //https://stackoverflow.com/questions/29003414/render-camera-preview-on-a-texture-with-target-gl-texture-2d
    t = getTimeMs();
    glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, input.data);
    LOGD("glReadPixels() costs %d ms", getTimeInterval(t));

    //Creates a Copy of the Mat Formatted in HSV
    t = getTimeMs();
    static cv::Mat hsv;
    cv::cvtColor(input, hsv, CV_RGBA2RGB);
    cv::cvtColor(hsv, hsv, CV_RGB2HSV);
    LOGD("cvtColor() costs %d ms", getTimeInterval(t));

    //Creates a Copy of the HSV Mat as a Binary Image (Colors in Range are White, Else Black)
    t = getTimeMs();
    static cv::Mat thresh;
    cv::inRange(hsv, cv::Scalar(h_min, s_min, v_min), cv::Scalar(h_max, s_max, v_max), thresh);
    LOGD("inRange() costs %d ms", getTimeInterval(t));

    //Begins Algorithm to Determine Visible Targets
    t = getTimeMs();
    //Clones the Binary Threshold Image
    static cv::Mat contour_input;
    contour_input = thresh.clone();
    //Creates Lists for Different Shapes (Before Consideration for Target)
    std::vector<std::vector<cv::Point>> contours;
    std::vector<cv::Point> convex_contour;
    std::vector<cv::Point> poly;
    //Creates Lists for Different Target Considerations
    std::vector<TargetInfo> accepted_targets;
    std::vector<TargetInfo> targets;
    std::vector<TargetInfo> rejected_targets;
    //Starts Finding the 'Contours' on the Binary Image
    cv::findContours(contour_input, contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_TC89_KCOS);
    //Loops Through Each Found Contour for Target Consideration
    for (auto &contour : contours)
    {
        convex_contour.clear();
        cv::convexHull(contour, convex_contour, false);
        poly.clear();
        //cv::approxPolyDP(convex_contour, poly, 20, true);

        if (cv::isContourConvex(convex_contour))
        {
            TargetInfo target;
            cv::Rect bounding_rect = cv::boundingRect(convex_contour);
            target.centroid_x = bounding_rect.x + (bounding_rect.width / 2);
            // centroid Y is top of target because it changes shape as you move
            target.centroid_y = bounding_rect.y + bounding_rect.height;
            target.width = bounding_rect.width;
            target.height = bounding_rect.height;
            target.points = convex_contour;

            //Filter based on size
            //Keep in mind width/height are in image's terms...
            const double kMinTargetWidth = 10;
            const double kMaxTargetWidth = 300;
            const double kMinTargetHeight = 10;
            const double kMaxTargetHeight = 300;
            if (target.width < kMinTargetWidth || target.width > kMaxTargetWidth ||
                target.height < kMinTargetHeight || target.height > kMaxTargetHeight)
            {
                LOGD("Rejecting target due to size. H: %.2lf | W: %.2lf",
                    target.height, target.width);
                rejected_targets.push_back(std::move(target));
                continue;
            }

            // Filter based on shape
            const double kMaxWideness = 3.0;
            const double kMinWideness = 0.25;
            double wideness = target.width / target.height;
            if (wideness < kMinWideness || wideness > kMaxWideness)
            {
                LOGD("Rejecting target due to shape : %.2lf", wideness);
                rejected_targets.push_back(std::move(target));
                continue;
            }

            // Filter based on fullness
            const double kMinFullness = .45;
            const double kMaxFullness = .95;
            double original_contour_area = cv::contourArea(contour);
            double area = target.width * target.height * 1.0;
            double fullness = original_contour_area / area;
            if (fullness < kMinFullness || fullness > kMaxFullness)
            {
                LOGD("Rejecting target due to fullness : %.2lf", fullness);
                rejected_targets.push_back(std::move(target));
                continue;
            }

            // We found a target
            LOGD("Found target at %.2lf, %.2lf %.2lf, %.2lf",
                target.centroid_x, target.centroid_y, target.width, target.height);
            accepted_targets.push_back(std::move(target));
        }
    }

    LOGD("Contour analysis costs %d ms", getTimeInterval(t));

    //Sorts (Insertion Sort) Array Based on Size
    for (int i = 0; i < accepted_targets.size(); i++)
    {
        for (int j = accepted_targets.size() - 1; j > i; j--)
        {
            TargetInfo targetA = accepted_targets[i];
            TargetInfo targetB = accepted_targets[j];

            if (targetB.width * targetB.height > targetA.width * targetA.height)
            {
                std::swap(accepted_targets[i], accepted_targets[j]);
            }
        }
    }

    LOGD("Total Number of Targets: %d", (int) accepted_targets.size());

    //Takes the top so many blocks in size
    const int kMaxTargets = 6;
    for (int i = 0; i < kMaxTargets && i < accepted_targets.size(); i++)
    {
        LOGD("True Target Identified");
        TargetInfo target = accepted_targets[i];
        targets.push_back(std::move(target));
    }

    //Write Back - 'vis' is the Image Array that Will Be Displayed
    t = getTimeMs();
    static cv::Mat vis;
    if (mode == DISP_MODE_RAW)
    {
        vis = input;
    }
    else if (mode == DISP_MODE_THRESH)
    {
        cv::cvtColor(thresh, vis, CV_GRAY2RGBA);
    }
    else
    {
        vis = input;
        //Render the targets - Creates the On-Screen Visualization to Show Identified Targets
        for (auto &target : targets)
        {
            cv::polylines(vis, target.points, true, cv::Scalar(0, 112, 255), 3);
        }
    }

    if (mode == DISP_MODE_TARGETS_PLUS)
    {
        for (auto &target : rejected_targets)
        {
            cv::polylines(vis, target.points, true, cv::Scalar(255, 0, 0), 3);
        }
    }
    LOGD("Creating vis costs %d ms", getTimeInterval(t));

    //OpenGL Code - Pushes Image Pixel (Mat) Out to Screen 'glTexSubImage2D'
    //https://developer.android.com/reference/android/opengl/GLUtils.html
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, texOut);
    t = getTimeMs();
    //https://www.khronos.org/registry/OpenGL-Refpages/gl4/html/glTexSubImage2D.xhtml
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE,
        vis.data);
    LOGD("glTexSubImage2D() costs %d ms", getTimeInterval(t));

    //Sets the Mat Pointer to the Pointer of the Displayed Mat
    display = &vis;
    return targets;
}

static bool sFieldsRegistered = false;

static jfieldID sNumTargetsField;
static jfieldID sTargetsField;

static jfieldID sCentroidXField;
static jfieldID sCentroidYField;
static jfieldID sWidthField;
static jfieldID sHeightField;

static void ensureJniRegistered(JNIEnv *env)
{
    if (sFieldsRegistered)
    {
        return;
    }

    sFieldsRegistered = true;
    jclass targetsInfoClass =
        env->FindClass("com/androidFRC/androidVision/NativePart$TargetsInfo");
    sNumTargetsField = env->GetFieldID(targetsInfoClass, "numTargets", "I");
    sTargetsField = env->GetFieldID(targetsInfoClass, "targets",
        "[Lcom/androidFRC/androidVision/NativePart$TargetsInfo$Target;");
    jclass targetClass =env->FindClass("com/androidFRC/androidVision/NativePart$TargetsInfo$Target");

    sCentroidXField = env->GetFieldID(targetClass, "centroidX", "D");
    sCentroidYField = env->GetFieldID(targetClass, "centroidY", "D");
    sWidthField = env->GetFieldID(targetClass, "width", "D");
    sHeightField = env->GetFieldID(targetClass, "height", "D");
}

inline unsigned int colorRGBAToARGB(unsigned int x)
{
    //https://stackoverflow.com/questions/11259391/fast-converting-rgba-to-argb
    return (unsigned int) ((x & 0xff000000) |
                            (x & 0x000000ff) << 16 |
                            (x & 0x0000ff00) |
                            ((x & 0x00ff0000) >> 16));
}

extern "C" void processFrame(JNIEnv *env, int tex1, int tex2, int w, int h,
                             int mode, int h_min, int h_max, int s_min,
                             int s_max, int v_min, int v_max,
                             jobject destTargetInfo)
{
    cv::Mat *dis;
    auto targets = processImpl(w, h, tex2, static_cast<DisplayMode>(mode), h_min,h_max, s_min, s_max, v_min, v_max, dis);
    int numTargets = targets.size();
    ensureJniRegistered(env);
    env->SetIntField(destTargetInfo, sNumTargetsField, numTargets);
    if (numTargets == 0)
    {
        return;
    }

    //Sends all of the Target Information to Java-Side Objects
    jobjectArray targetsArray = static_cast<jobjectArray>(env->GetObjectField(destTargetInfo, sTargetsField));
    for (int i = 0; i < std::min(numTargets, 3); ++i)
    {
        jobject targetObject = env->GetObjectArrayElement(targetsArray, i);
        const auto &target = targets[i];
        env->SetDoubleField(targetObject, sCentroidXField, target.centroid_x);
        env->SetDoubleField(targetObject, sCentroidYField, target.centroid_y);
        env->SetDoubleField(targetObject, sWidthField, target.width);
        env->SetDoubleField(targetObject, sHeightField, target.height);
    }
}

extern "C" void processFrameAndSetImage(JNIEnv *env, int tex1, int tex2, int w, int h,
                               int mode, int h_min, int h_max, int s_min,
                               int s_max, int v_min, int v_max, jbyte *out_dis,
                               jobject destTargetInfo)
{
    cv::Mat *dis;
    int64_t t;
    auto targets = processImpl(w, h, tex2, static_cast<DisplayMode>(mode), h_min,
                               h_max, s_min, s_max, v_min, v_max, dis);
    int numTargets = targets.size();
    ensureJniRegistered(env);
    env->SetIntField(destTargetInfo, sNumTargetsField, numTargets);

    //Sets Up Timing Variable and Grabs Output Byte Array From JNI
    t = getTimeMs();
    jbyte *arr = env->GetByteArrayElements((jbyteArray) out_dis, NULL);
    cv::Mat tempMat(dis->rows, dis->cols, dis->type());

    //https://stackoverflow.com/questions/14276655/passing-a-byte-array-from-jni-directly-in-android-bitmap
    //https://stackoverflow.com/questions/16059389/pass-bitmap-reference-from-java-to-c
    //https://stackoverflow.com/questions/23001512/c-and-opencv-get-and-set-pixel-color-to-mat
    //https://stackoverflow.com/questions/4665122/android-pass-bitmap-to-native-in-2-1-and-lower

    //Copies Over Individual Integer (Representing the Pixel) and Converts to ARGB, which Cv:imencode can turn to jpeg format
    for(int i = dis->rows - 1; i >= 0; i--)
    {
        for(int j = dis->cols - 1; j >= 0; j--)
        {
            tempMat.at<unsigned int>(dis->rows - i - 1, j) = colorRGBAToARGB(dis->at<unsigned int>(i,j));
        }
    }
    LOGD("Array Conversion Costs %d ms", getTimeInterval(t));

    t = getTimeMs();
    //Converts Picture on Screen to jpeg format (so that it can be streamed over ip)
    cv::InputArray inputArray(tempMat);
    std::vector<unsigned char> buffer;
    cv::imencode(".jpg", inputArray, buffer);
    int bufferSize = buffer.size();
    LOGD("Copying %d Entries", bufferSize);

    //Copies Buffer Bytes to The Java-Side Array
    for(int i = 0; i < bufferSize; i++)
    {
        arr[i] = buffer[i];
    }

    //Places a Size Integer at the End of the "Worse-Case Scenario" Sized Byte Array
    arr[w * h * 4] = (unsigned char) ((bufferSize & 0xff000000) >> 24);
    arr[w * h * 4 + 1] = (unsigned char) ((bufferSize & 0x00ff0000) >> 16);
    arr[w * h * 4 + 2] = (unsigned char) ((bufferSize & 0x0000ff00) >> 8);
    arr[w * h * 4 + 3] = (unsigned char) ((bufferSize & 0x000000ff));

    //http://adndevblog.typepad.com/cloud_and_mobile/2013/08/android-ndk-passing-complex-data-to-jni.html

    //Releases Java Array Back to Java
    env->ReleaseByteArrayElements((jbyteArray) out_dis, arr, 0);
    LOGD("Array Transfer Costs %d ms", getTimeInterval(t));

    if (numTargets == 0)
    {
      return;
    }
    //Sends all of the Target Information to Java-Side Objects
    jobjectArray targetsArray = static_cast<jobjectArray>(
        env->GetObjectField(destTargetInfo, sTargetsField));
    for (int i = 0; i < std::min(numTargets, 3); ++i)
    {
        jobject targetObject = env->GetObjectArrayElement(targetsArray, i);
        const auto &target = targets[i];
        env->SetDoubleField(targetObject, sCentroidXField, target.centroid_x);
        env->SetDoubleField(targetObject, sCentroidYField, target.centroid_y);
        env->SetDoubleField(targetObject, sWidthField, target.width);
        env->SetDoubleField(targetObject, sHeightField, target.height);
    }
}
