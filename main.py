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
options = vision.PoseLandmarkerOptions(base_options=base_options, running_mode=RunningMode.VIDEO)
detector = vision.PoseLandmarker.create_from_options(options)
POSE_CONNECTIONS = vision.PoseLandmarksConnections.POSE_LANDMARKS

cam = cv2.VideoCapture(0)

while cam.isOpened():
    ret, frame = cam.read()

    if not ret:
        print('camera has stoppped working!')
        break

    #do-what : converting BGR to RGB for mediapipe (webcam and openCV read video frame in BGR, however, mediaPipe expects images in RGB format)
    #missing this line will causes inaccurate results, since mediapipe reads them in RGB
    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

    #do-what : wraps raw video frame (a NumPy array) into a psecail container classs that MediaPipe's C++ (high speed C++) engine to process.
    #missing this line will cause the code to crash with TypeError, detector.detect_for_video() function can't process raw OpenCv imgae.
    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)

    #do-what : calculate the timestamp in milliseconds to mark each cam frame was capture. MediaPipe use the time difference between framees to predict the next move.
    #missing this will cause the code to crash with an error. time markers are strictly required for every single frame.
    frame_timestamp_ms = int(time.time() * 1000)

    #do-what : runs the AI model, scans the converted image, processes the body coordinates, and saves the data into detection_result.
    #missing this will cause a normal webcam feedback. This line does all the AL analysis.
    detection_result = detector.detect_for_video(mp_image, frame_timestamp_ms)

    #do-what : mutiples the fractions between 0.0 and 1.0 by  camera's act width and height to find the exact pixel coordinates, and draw a green dot on the feedback by using OpenCV.
    #missing this will cause a normal webcame feedback. This function draws the green dots on your body in the cam feedback.
    if detection_result.pose_landmarks:
        h, w, _ = frame.shape
        for pose_landmarks in detection_result.pose_landmarks:
            for connections in POSE_CONNECTIONS:
                start_idx = connections.start
                end_idx = connections.end

                start_lm = pose_landmarks[start_idx]
                end_lm = pose_landmarks[end_idx]

                x1, y1 = int(start_lm.x * w), int(start_lm.y * h)
                x2, y2 = int(end_lm.x * w), int(end_lm.y*h)

                cv2.line(frame, (x1,y1), (x2,y2), (0, 255, 0), 2)

            for landmark in pose_landmarks:
                cx, cy = int(landmark.x * w), int(landmark.y * h)
                cv2.circle(frame, (cx,cy), 5, (0,255,0), -1)

    cv2.imshow('frame',frame)

    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cam.release()
cv2.destroyAllWindows()