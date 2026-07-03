import cv2
import os
import time
import requests

import numpy as np
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

#extract a quote from zenquotes API and turn it into a string.
try:
    response = requests.get("https://zenquotes.io/api/random")
    data = response.json()
    qoute = f'"{data[0]["q"]}" - {data[0]["a"]}'
except:
    qoute = "You are doing good! Keep it up!"

max_chars = 60
words = qoute.split(" ")
sentence = []
current_sen = ""

#do-what : splitting the quote into characters and reconstruct it into a newer line for cv2.putText() to show.
#missing this line may cause the quote go out of the window frame and not showing it properly.
for word in words:
    if len(current_sen) + len(word) < max_chars:
        current_sen += word + " "
    else:
        sentence.append(current_sen)
        current_sen = word + " "
sentence.append(current_sen)

RunningMode = mp.tasks.vision.RunningMode

#get the exact folder path where main.py lives
script_dir = os.path.dirname(os.path.abspath(__file__))

#implementing model file into the code.
model_path = os.path.join(script_dir,'pose_landmarker_full.task')

base_options = python.BaseOptions(model_asset_path=model_path)
options = vision.PoseLandmarkerOptions(base_options=base_options, running_mode=RunningMode.VIDEO)
detector = vision.PoseLandmarker.create_from_options(options)

POSE_CONNECTIONS = vision.PoseLandmarksConnections.POSE_LANDMARKS

#Note : everyy landmark has a visibility score between 0.0 and 1.0 (1.0 = fully visible, very confident : 0.0 = not visible at all)
#setting the MIN_VISIBILITY into 0.6 , which is a good value for the threshold.
MIN_VISIBILITY = 0.6

#threshold variables for up and down angle
up_angle = 160
down_angle = 90

#variable of the stage of current person.
stage = None

#counter variable to store the value of the number of push-up.
counter = 0

#function that takes 3 landmark (shoulder, elbow, wrist) and calculate the angle.
def calculate_angle(a, b, c):
    ba = a - b
    bc = c - b

    cosine = np.dot(ba,bc) / (np.linalg.norm(ba) * np.linalg.norm(bc))
    cosine = np.clip(cosine, -1.0, 1.0)

    angle = np.degrees(np.arccos(cosine))
    return angle

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
            #Draw lines connecting the landmarks.
            for connections in POSE_CONNECTIONS:
                start_idx = connections.start
                end_idx = connections.end

                #draw line connecting between the shoulder, elbow and wrist.
                if 11 <= start_idx <= 16 and 11 <= end_idx <= 16:
                    start_lm = pose_landmarks[start_idx]
                    end_lm = pose_landmarks[end_idx]

                    x1, y1 = int(start_lm.x * w), int(start_lm.y * h)
                    x2, y2 = int(end_lm.x * w), int(end_lm.y*h)

                    cv2.line(frame, (x1,y1), (x2,y2), (0, 255, 0), 2)

            #locate the left elbow using it's ID in MediaPipe[13].
            left_elbow = np.array([pose_landmarks[13].x * w, pose_landmarks[13].y * h])
            #locate the right elbow using it's ID in MediaPipe[14].
            right_elbow = np.array([pose_landmarks[14].x * w, pose_landmarks[14].y * h])
            #locate the left shoulder using it's ID in MediaPipe[11].
            left_shoulder = np.array([pose_landmarks[11].x * w, pose_landmarks[11].y * h])
            #locate the right shoulder using it's ID in MediaPipe[12].
            right_shoulder = np.array([pose_landmarks[12].x * w, pose_landmarks[12].y * h])
            #locate the left wrist using it's ID in MediaPipe[15].
            left_wrist = np.array([pose_landmarks[15].x * w, pose_landmarks[15].y * h])
            #locate the right wrist using it's ID in MediaPipe[16].
            right_wrist = np.array([pose_landmarks[16].x * w, pose_landmarks[16].y * h])

            #print out the location of both left and right elbow on the console.
            #print(f"Left Angle -> {left_angle}\nRight Angle -> {right_angle}")

            for idx in range(11,17):
                landmark = pose_landmarks[idx]
                cx, cy = int(landmark.x * w), int(landmark.y * h)
                cv2.circle(frame, (cx,cy), 5, (0,255,0), -1)
            
            if (pose_landmarks[13].visibility > pose_landmarks[14].visibility):
                vis_shoulder = pose_landmarks[11]
                vis_elbow = pose_landmarks[13]
                vis_wrist = pose_landmarks[15]
                shoulder = left_shoulder
                elbow = left_elbow
                wrist = left_wrist
            else:
                vis_shoulder = pose_landmarks[12]
                vis_elbow = pose_landmarks[14]
                vis_wrist = pose_landmarks[16]
                shoulder = right_shoulder
                elbow = right_elbow
                wrist = right_wrist

            #do-what : check the visibility of the user's shoulder, elbow and wrist. If one of them didn't detected by the camera, the function 
            #calculate angle will not be run, anle will not be drawn on the frame and counter will not be counting.
            #missing this can cause confusion on the algorithm, small movements can caused the counter to start counting.
            if (vis_shoulder.visibility > MIN_VISIBILITY and vis_elbow.visibility > MIN_VISIBILITY and vis_wrist.visibility > MIN_VISIBILITY):
                left_angle = calculate_angle(left_shoulder,left_elbow,left_wrist)
                right_angle = calculate_angle(right_shoulder,right_elbow,right_wrist)
                angle = calculate_angle(shoulder, elbow, wrist)

                #show the angle of the left and right arm on the frame.
                cv2.putText(frame, f"{left_angle:.2f}", (int(left_elbow[0]),int(left_elbow[1]) - 20), cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
                cv2.putText(frame, f"{right_angle:.2f}", (int(right_elbow[0]), int(right_elbow[1]) - 20), cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)

                #if statement to increase the push-up counter.
                if angle >= up_angle:
                    if stage == "down":
                        counter+=1
                    stage = "up"
                elif angle <= down_angle:
                    if stage == "up":
                        stage = "down"

                if angle > up_angle:
                    arc_color = (0, 255, 0)
                elif angle < down_angle:
                    arc_color = (0, 0, 255)
                else:
                    arc_color = (0, 255, 255)

                cv2.ellipse(frame, (int(vis_elbow.x*w), int(vis_elbow.y*h)), (30, 30), 0, 0, angle, arc_color, 2)
                cv2.putText(frame, f"Push-Up (Stage : {stage}) : {counter}", (45,100), cv2.FONT_HERSHEY_SIMPLEX, 1, (255,0,0), 2)
            else:
                cv2.putText(frame, "Please adjust your position!", (390, 150), cv2.FONT_HERSHEY_SIMPLEX, 1, (0,0,255), 2)

            for i, sen in enumerate(sentence):
                cv2.putText(frame, sen, (650,70 + i * 30), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0,255,0), 2)
            
            #print(f"Stage -> {stage}")
            #print(f"counter -> {counter}")

    cv2.imshow('frame',frame)

    key = cv2.waitKey(1) & 0xFF

    #allow the user to press q to quit, and r to reset the counter.
    if key == ord('q'):
        break
    elif key == ord('r'):
        counter = 0
        stage = None

cam.release()
cv2.destroyAllWindows()