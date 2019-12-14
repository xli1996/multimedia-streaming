# you have to run gstreamner to generate frame files first
# gst-launch-1.0 autovideosrc ! queue ! videoconvert ! jpegenc ! multifilesink location="frames/%d.jpg"

import cv2
import os
from kafka import KafkaProducer

server = "see slack channel"


image_folder = 'frames'
video_name = 'video.avi'
cmd = ''
order_images = []

images = [int(img) for img in os.listdir(image_folder)]
images.sort()

def producer():
    producer = KafkaProducer(bootstrap_servers=server)
    topic = "frame-test"
    for image in images:
        key = bytes(image)
        frame = open('frames/'+str(image), 'rb')
        data = frame.read()
        frame.close()
        producer.send(topic, data)
    producer.close()
    return

def main():
    producer()


if __name__== "__main__":
    main()
