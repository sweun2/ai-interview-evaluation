import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:web_socket_channel/html.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: VideoStreamPage(),
    );
  }
}

class VideoStreamPage extends StatefulWidget {
  @override
  _VideoStreamPageState createState() => _VideoStreamPageState();
}

class _VideoStreamPageState extends State<VideoStreamPage> {
  final _channel = HtmlWebSocketChannel.connect('ws://localhost:8080/stream');
  RTCPeerConnection? _peerConnection;
  MediaStream? _localStream;
  final RTCVideoRenderer _localRenderer = RTCVideoRenderer();
  bool _isStreaming = false;
  final TextEditingController _textController = TextEditingController();
  final List<String> _messages = [];
  @override
  void initState() {
    super.initState();
    _localRenderer.initialize();
    _channel.stream.listen(onMessage, onError: onError, onDone: onDone);
  }

  void onMessage(message) {
    try {
      var decodedMessage = json.decode(message);
      switch (decodedMessage['type']) {
        case 'answer':
          _handleSDPAnswer(decodedMessage['sdpAnswer']);
          break;
        case 'candidate':
          _handleRemoteCandidate(decodedMessage);
          break;
        case 'chat':
          setState(() {
            _messages.add(decodedMessage['message']);
            _textController.text = _messages.first;
          });
        default:
          break;
      }
    } catch (e) {
    }
  }

  void onError(error) {
  }

  void onDone() {
  }
  void _sendMessage() {
    if (_textController.text.isNotEmpty) {
      _channel.sink.add(json.encode({
        'type': 'chat',
        'message': _textController.text,
      }));
      _textController.clear();
    }
  }
  void _handleSDPAnswer(String sdpAnswer) async {
    RTCSessionDescription description = RTCSessionDescription(sdpAnswer, 'answer');
    await _peerConnection!.setRemoteDescription(description);
    _channel.sink.add(json.encode({
      'command': 'start'
    }));
  }

  void _handleRemoteCandidate(Map<String, dynamic> candidateMap) {
    RTCIceCandidate candidate = RTCIceCandidate(
      candidateMap['candidate'],
      candidateMap['sdpMid'],
      candidateMap['sdpMLineIndex']
    );
    _peerConnection!.addCandidate(candidate);
  }

  void _startStreaming() async {
    final Map<String, dynamic> configuration = {
      'iceServers': [
        {'url': 'stun:stun.l.google.com:19302'},
      ]
    };

    final Map<String, dynamic> constraints = {
      'mandatory': {
        'OfferToReceiveAudio': false,
        'OfferToReceiveVideo': true,
      },
      'optional': [],
    };

    _peerConnection = await createPeerConnection(configuration, constraints);
    _localStream = await navigator.mediaDevices.getUserMedia({
    'audio': true,
    'video': {
      'facingMode': 'user',
    },});
    _peerConnection!.addStream(_localStream!);
    _localRenderer.srcObject = _localStream;

    RTCSessionDescription description = await _peerConnection!.createOffer(constraints);
    await _peerConnection!.setLocalDescription(description);

    _peerConnection!.onIceCandidate = (candidate) {
      _channel.sink.add(json.encode({
        'type': 'candidate',
        'candidate': candidate.candidate,
        'sdpMid': candidate.sdpMid,
        'sdpMLineIndex': candidate.sdpMLineIndex
      }));
    };

    _channel.sink.add(json.encode({
      'type': 'offer',
      'sdp': description.sdp
    }));
    
    setState(() {
      _isStreaming = true;
    });
  }

  void _stopStreaming() {
    _localRenderer.srcObject = null;
    _localStream?.getTracks().forEach((track) {
      track.stop();
    });
    _localStream?.dispose();
    _peerConnection?.close();
    _channel.sink.add(json.encode({
      'command': 'stop'
    }));
    _channel.sink.close();
    setState(() {
      _isStreaming = false;
    });
  }

  @override
  void dispose() {
    _localRenderer.dispose();
    _peerConnection?.dispose();
    super.dispose();
  }

  @override
Widget build(BuildContext context) {
  return Scaffold(
    appBar: AppBar(title: const Text('WebRTC Video Stream')),
    body: Row(
      children: [
        Expanded(
          flex: 1613,
          child: Column(
            children: [
              Expanded(child: RTCVideoView(_localRenderer)),
              if (!_isStreaming)
                ElevatedButton(
                  onPressed: _startStreaming,
                  child: const Text('Start Streaming'),
                ),
              if (_isStreaming)
                ElevatedButton(
                  onPressed: _stopStreaming,
                  child: const Text('Stop Streaming'),
                ),
            ],
          ),
        ),
        Expanded(
          flex: 1000,  // 채팅창 부분의 크기를 늘립니다.
          child: Container(
            color: Colors.grey[200],  // 채팅창 배경색을 설정합니다.
            child: Column(
              children: [
                Expanded(
                  child: ListView.builder(
                    itemCount: _messages.length,
                    itemBuilder: (context, index) => ListTile(
                      title: Text(_messages[index]),
                    ),
                  ),
                ),
                Padding(
                  padding: EdgeInsets.all(8.0),
                  child: TextField(
                    controller: _textController,
                    decoration: InputDecoration(
                      labelText: 'Send a message',
                      suffixIcon: IconButton(
                        icon: Icon(Icons.send),
                        onPressed: _sendMessage,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    ),
  );
}


}
