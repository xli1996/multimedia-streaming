import threading, logging, time
import multiprocessing
import cv2
import numpy as np

from kafka import KafkaConsumer, KafkaProducer

server = "ec2-34-217-2-237.us-west-2.compute.amazonaws.com:9093"
video_name = "video-from-kafka.avi"
# def producer():
#     producer = KafkaProducer(bootstrap_servers=server)
#     producer.send('my-topic', b"test")
#     producer.send('my-topic', b"\xc2Hola, mundo!")
#     producer.close()

def consumer():
    topic = "frame-test"
    consumer = KafkaConsumer(bootstrap_servers=server,
                                auto_offset_reset='earliest',
                                consumer_timeout_ms=1000)
    consumer.subscribe(topic)
    cnt = 0

    for message in consumer:
        nparr = np.frombuffer(message.value, np.uint8)
        frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if cnt==0:
            height, width, layers = frame.shape
            video = cv2.VideoWriter(video_name, 0, 24, (width,height))
        video.write(frame)
        cnt = cnt + 1
    cv2.destroyAllWindows()
    video.release()   

    consumer.close()
        
consumer()