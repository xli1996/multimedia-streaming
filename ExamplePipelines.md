For audio:
=========

```
gst-launch-1.0 audiotestsrc ! audioconvert ! audioresample ! wavenc ! filesink location=output.wav
gst-launch-1.0 filesrc location=output.wav ! wavparse ! audioconvert ! audioresample ! autoaudiosink
```


```
gst-launch-1.0 videotestsrc ! avimux ! filesink location=test.avi
gst-launch-1.0 filesrc location=test.avi ! avidemux ! xvimagesink
```
