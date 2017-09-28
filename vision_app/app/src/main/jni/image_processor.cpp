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

enum DisplayMode {
  DISP_MODE_RAW = 0,
  DISP_MODE_THRESH = 1,
  DISP_MODE_TARGETS = 2,
  DISP_MODE_TARGETS_PLUS = 3
};

struct TargetInfo {
  double centroid_x;
  double centroid_y;
  double width;
  double height;
  std::vector<cv::Point> points;
};

std::vector<TargetInfo> processImpl(int w, int h, int texOut, DisplayMode mode,
                                    int h_min, int h_max, int s_min, int s_max,
                                    int v_min, int v_max, cv::Mat *&display) {
  LOGD("Image is %d x %d", w, h);
  LOGD("H %d-%d S %d-%d V %d-%d", h_min, h_max, s_min, s_max, v_min, v_max);
  int64_t t;

  static cv::Mat input;
  input.create(h, w, CV_8UC4);

  // read
  t = getTimeMs();
  glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, input.data);
  LOGD("glReadPixels() costs %d ms", getTimeInterval(t));

  // modify
  t = getTimeMs();
  static cv::Mat hsv;
  cv::cvtColor(input, hsv, CV_RGBA2RGB);
  cv::cvtColor(hsv, hsv, CV_RGB2HSV);
  LOGD("cvtColor() costs %d ms", getTimeInterval(t));

  t = getTimeMs();
  static cv::Mat thresh;
  cv::inRange(hsv, cv::Scalar(h_min, s_min, v_min),
              cv::Scalar(h_max, s_max, v_max), thresh);
  LOGD("inRange() costs %d ms", getTimeInterval(t));

  t = getTimeMs();
  static cv::Mat contour_input;
  contour_input = thresh.clone();
  std::vector<std::vector<cv::Point>> contours;
  std::vector<cv::Point> convex_contour;
  std::vector<cv::Point> poly;
  std::vector<TargetInfo> accepted_targets;
  std::vector<TargetInfo> targets;
  std::vector<TargetInfo> rejected_targets;
  cv::findContours(contour_input, contours, cv::RETR_EXTERNAL,
                   cv::CHAIN_APPROX_TC89_KCOS);
  for (auto &contour : contours) {
    convex_contour.clear();
    cv::convexHull(contour, convex_contour, false);
    poly.clear();
    //cv::approxPolyDP(convex_contour, poly, 20, true);

    if (cv::isContourConvex(convex_contour)) {
      TargetInfo target;
      cv::Rect bounding_rect = cv::boundingRect(convex_contour);
      target.centroid_x = bounding_rect.x + (bounding_rect.width / 2);
      // centroid Y is top of target because it changes shape as you move
      target.centroid_y = bounding_rect.y + bounding_rect.height;
      target.width = bounding_rect.width;
      target.height = bounding_rect.height;
      target.points = convex_contour;

      // Filter based on size
      // Keep in mind width/height are in imager terms...
      const double kMinTargetWidth = 20;
      const double kMaxTargetWidth = 300;
      const double kMinTargetHeight = 10;
      const double kMaxTargetHeight = 100;
      if (target.width < kMinTargetWidth || target.width > kMaxTargetWidth ||
          target.height < kMinTargetHeight ||
          target.height > kMaxTargetHeight) {
        LOGD("Rejecting target due to size. H: %.2lf | W: %.2lf",
                  target.height, target.width);
        rejected_targets.push_back(std::move(target));
        continue;
      }

      // Filter based on shape
      const double kMaxWideness = 7.0;
      const double kMinWideness = 1.5;
      double wideness = target.width / target.height;
      if (wideness < kMinWideness || wideness > kMaxWideness) {
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
      if (fullness < kMinFullness || fullness > kMaxFullness) {
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

  const double kMaxOffset = 10;
  bool found = false;
  for (int i = 0; !found && i < accepted_targets.size(); i++) {
    for (int j = 0; !found && j < accepted_targets.size(); j++) {
      if (i == j) {
        continue;
      }
      TargetInfo targetI = accepted_targets[i];
      TargetInfo targetJ = accepted_targets[j];
      double offset = abs(targetI.centroid_x - targetJ.centroid_x);
      if (offset < kMaxOffset) {
        TargetInfo topTarget = targetI.centroid_y > targetJ.centroid_y ? targetI : targetJ;
        TargetInfo bottomTarget = targetI.centroid_y < targetJ.centroid_y ? targetI : targetJ;
        if (topTarget.height > bottomTarget.height) {
          targets.push_back(std::move(topTarget));
          found = true;
          break;
        }
      }
    }
  }

  // write back
  t = getTimeMs();
  static cv::Mat vis;
  if (mode == DISP_MODE_RAW) {
    vis = input;
  } else if (mode == DISP_MODE_THRESH) {
    cv::cvtColor(thresh, vis, CV_GRAY2RGBA);
  } else {
    vis = input;
    // Render the targets
    for (auto &target : targets) {
      cv::polylines(vis, target.points, true, cv::Scalar(0, 112, 255), 3);
      cv::circle(vis, cv::Point(target.centroid_x, target.centroid_y), 4,
                 cv::Scalar(255, 50, 255), 3);
    }
  }
  if (mode == DISP_MODE_TARGETS_PLUS) {
    for (auto &target : rejected_targets) {
      cv::polylines(vis, target.points, true, cv::Scalar(255, 0, 0), 3);
    }
  }
  LOGD("Creating vis costs %d ms", getTimeInterval(t));

  glActiveTexture(GL_TEXTURE0);
  glBindTexture(GL_TEXTURE_2D, texOut);
  t = getTimeMs();
  glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE,
                  vis.data);
  LOGD("glTexSubImage2D() costs %d ms", getTimeInterval(t));

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

static void ensureJniRegistered(JNIEnv *env) {
  if (sFieldsRegistered) {
    return;
  }
  sFieldsRegistered = true;
  jclass targetsInfoClass =
      env->FindClass("com/team254/cheezdroid/NativePart$TargetsInfo");
  sNumTargetsField = env->GetFieldID(targetsInfoClass, "numTargets", "I");
  sTargetsField = env->GetFieldID(
      targetsInfoClass, "targets",
      "[Lcom/team254/cheezdroid/NativePart$TargetsInfo$Target;");
  jclass targetClass =
      env->FindClass("com/team254/cheezdroid/NativePart$TargetsInfo$Target");

  sCentroidXField = env->GetFieldID(targetClass, "centroidX", "D");
  sCentroidYField = env->GetFieldID(targetClass, "centroidY", "D");
  sWidthField = env->GetFieldID(targetClass, "width", "D");
  sHeightField = env->GetFieldID(targetClass, "height", "D");
}

inline unsigned int colorRGBAToARGB(unsigned int x) {
    //https://stackoverflow.com/questions/11259391/fast-converting-rgba-to-argb
    return (unsigned int) (((x & 0xff000000) >> 24) << 24 | (x & 0x000000ff) << 16 | ((x & 0x0000ff00) >> 8 << 8) | ((x & 0x00ff0000) >> 16));
}

extern "C" void processFrame(JNIEnv *env, int tex1, int tex2, int w, int h,
                             int mode, int h_min, int h_max, int s_min,
                             int s_max, int v_min, int v_max,
                             jobject destTargetInfo) {
  cv::Mat *dis;
  auto targets = processImpl(w, h, tex2, static_cast<DisplayMode>(mode), h_min,
                             h_max, s_min, s_max, v_min, v_max, dis);
  int numTargets = targets.size();
  ensureJniRegistered(env);
  env->SetIntField(destTargetInfo, sNumTargetsField, numTargets);
  if (numTargets == 0) {
    return;
  }
  jobjectArray targetsArray = static_cast<jobjectArray>(
      env->GetObjectField(destTargetInfo, sTargetsField));
  for (int i = 0; i < std::min(numTargets, 3); ++i) {
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
                               jobject destTargetInfo) {
    cv::Mat *dis;
    int64_t t;
    auto targets = processImpl(w, h, tex2, static_cast<DisplayMode>(mode), h_min,
                               h_max, s_min, s_max, v_min, v_max, dis);
    int numTargets = targets.size();
    ensureJniRegistered(env);
    env->SetIntField(destTargetInfo, sNumTargetsField, numTargets);

    t = getTimeMs();
    jbyte *arr = env->GetByteArrayElements((jbyteArray) out_dis, NULL);

    std::vector<unsigned char> buffer;
    cv::Mat tempMat(dis->rows, dis->cols, dis->type());

    for(int i = dis->rows - 1; i >= 0; i--) {
        for(int j = dis->cols - 1; j >= 0; j--) {
            tempMat.at<unsigned int>(dis->rows - i - 1, j) = colorRGBAToARGB(dis->at<unsigned int>(i,j));
        }
    }

    LOGD("Array Conversion Costs %d ms", getTimeInterval(t));
    t = getTimeMs();

    cv::InputArray inputArray(tempMat);
    cv::imencode(".jpg", inputArray, buffer);

    int bufferSize = buffer.size();
    LOGD("Copying %d Entries", bufferSize);

    for(int i = 0; i < bufferSize; i++) {
        arr[i] = buffer[i];
    }

    arr[w * h * 4] = (unsigned char) ((bufferSize & 0xff000000) >> 24);
    arr[w * h * 4 + 1] = (unsigned char) ((bufferSize & 0x00ff0000) >> 16);
    arr[w * h * 4 + 2] = (unsigned char) ((bufferSize & 0x0000ff00) >> 8);
    arr[w * h * 4 + 3] = (unsigned char) ((bufferSize & 0x000000ff));

    env->ReleaseByteArrayElements((jbyteArray) out_dis, arr, 0);
    LOGD("Array Transfer Costs %d ms", getTimeInterval(t));

    if (numTargets == 0) {
      return;
    }
    jobjectArray targetsArray = static_cast<jobjectArray>(
        env->GetObjectField(destTargetInfo, sTargetsField));
    for (int i = 0; i < std::min(numTargets, 3); ++i) {
      jobject targetObject = env->GetObjectArrayElement(targetsArray, i);
      const auto &target = targets[i];
      env->SetDoubleField(targetObject, sCentroidXField, target.centroid_x);
      env->SetDoubleField(targetObject, sCentroidYField, target.centroid_y);
      env->SetDoubleField(targetObject, sWidthField, target.width);
      env->SetDoubleField(targetObject, sHeightField, target.height);
    }
}
