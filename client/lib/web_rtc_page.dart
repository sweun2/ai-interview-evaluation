import 'package:flutter/material.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'web_rtc_manager.dart';

class WebRTCPage extends StatefulWidget {
  const WebRTCPage({super.key});

  @override
  WebRTCPageState createState() => WebRTCPageState();
}

class WebRTCPageState extends State<WebRTCPage> {
  final WebRTCManager _webRTCManager = WebRTCManager();

  @override
  void initState() {
    super.initState();
    _webRTCManager.initialize();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('WebRTC Demo'),
      ),
      body: Column(
        children: <Widget>[
          Expanded(
            child: RTCVideoView(_webRTCManager.localRenderer),
          ),
          Expanded(
            child: RTCVideoView(_webRTCManager.remoteRenderer),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _webRTCManager.dispose();
    super.dispose();
  }
}
