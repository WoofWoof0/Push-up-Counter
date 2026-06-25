import cv2
import os
import time

import numpy as np
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

RunningMode = mp.tasks.vision.RunningMode

# get the exact folder path where main.py lives
script_dir = os.path.dirname(os.path.abspath(__file__))

#implementing model file into the code.
model_path = os.path.join(script_dir,'pose_landmarker_full.task')

base_options = python.BaseOptions(model_asset_path=model_path)
options = vision.PoseLandmarkerOptions(base_options=base_options, running_mode=RunningMode.VIDEO) #Turn off background mask
detector = vision.PoseLandmarker.create_from_options(options)

cam = cv2.VideoCapture(0)

while cam.isOpened():
    ret, frame = cam.read()

    if not ret:
        print('camera has stoppped working!')
        break

    #converting BGR to RGB for mediapipe (webcam and openCV read video frame in BGR, however, mediaPipe expects images in RGB format)
    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)

    #Create a millisecond timestamp for this frame
    frame_timestamp_ms = int(time.time() * 1000)

    #
    detection_result = detector.detect_for_video(mp_image, frame_timestamp_ms)

    if detection_result.pose_landmarks:
        h, w, _ = frame.shape
        for pose_landmarks in detection_result.pose_landmarks:
            for landmark in pose_landmarks:
                cx, cy = int(landmark.x * w), int(landmark.y * h)
                cv2.circle(frame, (cx,cy), 5, (0,255,0), -1)

    cv2.imshow('frame',frame)

    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cam.release()
cv2.destroyAllWindows()