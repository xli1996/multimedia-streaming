from flask import Flask, render_template, Response
from kafka import KafkaConsumer
import cv2
import numpy as np

app = Flask(__name__)

server = "see slack"

@app.route('/')
def index():
    return "hello"

def gen():
    topic = "frame-test"
    consumer = KafkaConsumer(bootstrap_servers=server,
                            auto_offset_reset='earliest',
                            consumer_timeout_ms=1000)
    consumer.subscribe(topic)
    video = cv2.VideoWriter()
    while True:
        for message in consumer:
            yield (b'--frame\r\n'
                b'Content-Type: image/jpeg\r\n\r\n' + message.value + b'\r\n\r\n')

@app.route('/video_feed')
def video_feed():
    return Response(gen(),
                    mimetype='multipart/x-mixed-replace; boundary=frame')

if __name__ == '__main__':
    app.run(host='0.0.0.0', debug=True)