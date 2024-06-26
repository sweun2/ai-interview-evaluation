import 'dart:async'; // Timer를 사용하기 위해 import
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:web_socket_channel/html.dart';
import 'package:http/http.dart' as http;

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      theme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: Colors.black,
        primaryColor: Colors.blueGrey[900],
      ),
      home: VideoStreamPage(),
    );
  }
}

class VideoStreamPage extends StatefulWidget {
  @override
  _VideoStreamPageState createState() => _VideoStreamPageState();
}

class _VideoStreamPageState extends State<VideoStreamPage> {
  final Map<String, Timer> _messageTimers = {}; // Track timers for each message
  final _channel = HtmlWebSocketChannel.connect('ws://localhost:8080/stream');
  RTCPeerConnection? _peerConnection;
  MediaStream? _localStream;
  final RTCVideoRenderer _localRenderer = RTCVideoRenderer();
  bool _isStreaming = false;
  bool _showNextQuestionButton = false;
  bool _isLoading = false;
  final TextEditingController _textController = TextEditingController();
  final List<String> _messages = [];
  final List<String> _receivedMessageIds = [];
  String _statusMessage = "Loading...";
  String _sessionId = "";
  String _questionType = "text";
  Timer? _timer; // 주기적으로 상태를 확인하기 위한 Timer

  @override
  void initState() {
    super.initState();
    _localRenderer.initialize();
    _channel.stream.listen(onMessage, onError: onError, onDone: onDone);

    WidgetsBinding.instance.addPostFrameCallback((_) {
      _promptStartStreaming();
    });

  }

  void _checkChatMessages() {
    setState(() {
      print('Checking chat messages...');
      print(_messages);
      // _isLoading이 true 상태로 너무 오래 있으면 false로 설정
      if (_isLoading && _messages.isNotEmpty) {
        _isLoading = false;
        print('Loading state set to false due to chat message presence.');
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel(); // Timer 취소
    _localRenderer.dispose();
    _peerConnection?.dispose();
    super.dispose();
  }

  Future<void> fetchStatusMessage(String sessionId) async {
    setState(() {
      _isLoading = true;
    });

    var response = await http.post(
      Uri.parse('http://localhost:8080/question/rand/$sessionId'),
      headers: <String, String>{
        'Content-Type': 'application/json; charset=UTF-8',
      },
    );

    if (response.statusCode == 200) {
      var body = utf8.decode(response.bodyBytes);
      var data = jsonDecode(body);
      setState(() {
        _statusMessage = data['question'];
        _questionType = data['type'];
        _textController.clear();
        _messages.clear();
        _isLoading = false;
      });
    } else {
      setState(() {
        _statusMessage = "Failed to fetch data";
        _isLoading = false;
      });
    }
  }

  void _promptStartStreaming() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (context) => AlertDialog(
          title: const Text("면접을 시작합니다."),
          content: const Text("시작과 동시에 캠이 녹화됩니다. 주어지는 질문에 답변하세요."),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(context).pop();
                _startStreaming();
              },
              child: const Text("Start"),
            ),
          ],
        ),
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('AI Interview Evaluation')),
      body: Stack(
        children: [
          Row(
            children: [
              Expanded(
                flex: 3,
                child: Column(
                  children: [
                    Expanded(
                      flex: 1,
                      child: Container(
                        color: Colors.blueGrey[800],
                        child: Center(
                          child: Text(
                            _statusMessage,
                            style: const TextStyle(
                              fontSize: 20,
                              color: Colors.white,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ),
                    ),
                    Expanded(
                      flex: 4,
                      child: Container(
                        color: Colors.grey[850],
                        child: _questionType == '음성'
                            ? RTCVideoView(_localRenderer)
                            : const Center(
                                child: Text(
                                  "주관식입니다. 채팅창에 답변을 입력하세요",
                                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.white),
                                ),
                              ),
                      ),
                    ),
                  ],
                ),
              ),
              Expanded(
                flex: 2,
                child: Column(
                  children: [
                    Container(
                      color: Colors.blueGrey[900],
                      padding: const EdgeInsets.all(8.0),
                      child: Row(
                        children: [
                          if (_questionType == '음성')
                            Expanded(
                              child: ElevatedButton(
                                onPressed: _isStreaming ? _stopStreaming : () => fetchStatusMessage(_sessionId),
                                style: ElevatedButton.styleFrom(
                                  foregroundColor: Colors.white,
                                  backgroundColor: Colors.teal[700],
                                  textStyle: const TextStyle(
                                    fontSize: 18,
                                    fontWeight: FontWeight.bold,
                                  ),
                                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 15),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                ),
                                child: Text(_isStreaming ? '문제 제출' : '다음 문제'),
                              ),
                            ),
                          if (_questionType != '음성' || _showNextQuestionButton)
                            Expanded(
                              child: ElevatedButton(
                                onPressed: () => fetchStatusMessage(_sessionId),
                                style: ElevatedButton.styleFrom(
                                  foregroundColor: Colors.white,
                                  backgroundColor: Colors.teal[700],
                                  textStyle: const TextStyle(
                                    fontSize: 18,
                                    fontWeight: FontWeight.bold,
                                  ),
                                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 15),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                ),
                                child: const Text('다음 문제'),
                              ),
                            ),
                        ],
                      ),
                    ),
                    Expanded(
                      flex: 4,
                      child: Container(
                        color: Colors.grey[900],
                        child: ListView.builder(
                          itemCount: _messages.length,
                          itemBuilder: (context, index) => ListTile(
                            title: Text(
                              _messages[index],
                              style: const TextStyle(color: Colors.white),
                            ),
                            contentPadding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 2.0),
                          ),
                        ),
                      ),
                    ),
                    if (_questionType != '음성')
                      Padding(
                        padding: const EdgeInsets.all(8.0),
                        child: Row(
                          children: [
                            Expanded(
                              child: TextField(
                                controller: _textController,
                                minLines: 1,
                                maxLines: null,
                                decoration: const InputDecoration(
                                  labelText: '답변을 입력하세요',
                                  labelStyle: TextStyle(color: Colors.white70),
                                  border: OutlineInputBorder(),
                                  enabledBorder: OutlineInputBorder(
                                    borderSide: BorderSide(color: Colors.white54),
                                  ),
                                  focusedBorder: OutlineInputBorder(
                                    borderSide: BorderSide(color: Colors.white),
                                  ),
                                ),
                                style: const TextStyle(fontSize: 16, color: Colors.white),
                                cursorColor: Colors.white,
                              ),
                            ),
                            IconButton(
                              icon: const Icon(Icons.send),
                              color: Colors.teal[700],
                              onPressed: _sendMessage,
                            ),
                          ],
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
          if (_isLoading)
            Container(
              color: Colors.black54,
              child: const Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    CircularProgressIndicator(),
                    SizedBox(height: 16),
                    Text(
                      "평가중입니다...",
                      style: TextStyle(fontSize: 20, color: Colors.white),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }

  void onMessage(message) {
    var decodedMessage = json.decode(message);

    if (decodedMessage['messageId'] != null && _receivedMessageIds.contains(decodedMessage['messageId'])) {
        print('Duplicate message received: ${decodedMessage['messageId']}');
        return;
    }

    print('Received message: $decodedMessage');
    switch (decodedMessage['type']) {
      case 'answer':
        _handleSDPAnswer(decodedMessage['sdpAnswer']).then((_) {
          setState(() {
            _sessionId = decodedMessage['sessionId'];
          });
          Future.microtask(() => fetchStatusMessage(_sessionId));
        });
        break;
      case 'candidate':
        _handleRemoteCandidate(decodedMessage);
        break;
      case 'chat':
        setState(() {
          if (decodedMessage['messageId'] != null) {
            _receivedMessageIds.add(decodedMessage['messageId']);
          }
          print('Chat message received: ${decodedMessage['message']}');
          _messages.add(decodedMessage['message']);
          _isLoading = false;
        });
        
        Future.microtask(() => sendAck(decodedMessage['messageId']));
        break;
      case 'chatAck':
        String messageId = decodedMessage['messageId'];
        if (_messageTimers.containsKey(messageId)) {
          _messageTimers[messageId]?.cancel();
          _messageTimers.remove(messageId);
        }
        break;
      default:
        setState(() {
          print('Unknown message type: ${decodedMessage['type']}');
          _isLoading = false;
        });
        break;
    }
  }
void sendAck(String messageId) {
    print('Sending ACK for messageId: $messageId');
    _channel.sink.add(json.encode({
        'type': 'chatAck',
        'messageId': messageId
    }));
}
  

  void onError(error) {
    print('Error: $error');
    setState(() {
      _isLoading = false;
    });
  }

  void onDone() {
    print('WebSocket connection closed');
    setState(() {
      _isLoading = false;
    });
  }
  void _sendMessage() {
    if (_textController.text.isNotEmpty) {
      String messageId = DateTime.now().millisecondsSinceEpoch.toString();
      String messageText = _textController.text;

      setState(() {
        _messages.add("나: $messageText");
        _isLoading = true;
      });

      _channel.sink.add(json.encode({
        'type': 'chat',
        'message': messageText,
        'messageId': messageId
      }));

      _textController.clear();
      setState(() {
        _showNextQuestionButton = true;
      });

      // Set a timer to wait for ack
      _messageTimers[messageId] = Timer(const Duration(seconds: 5), () {
        // Retry sending the message if no ack received
        _sendMessageRetry(messageText, messageId);
      });
    }
  }
  void _sendMessageRetry(String messageText, String messageId) {
    _channel.sink.add(json.encode({
      'type': 'chat',
      'message': messageText,
      'messageId': messageId
    }));

    // Reset the timer to wait for ack again
    _messageTimers[messageId] = Timer(const Duration(seconds: 5), () {
      // Retry sending the message if no ack received
      _sendMessageRetry(messageText, messageId);
    });
  }
  Future<void> _handleSDPAnswer(String sdpAnswer) async {
    RTCSessionDescription description = RTCSessionDescription(sdpAnswer, 'answer');
    await _peerConnection!.setRemoteDescription(description);
    _channel.sink.add(json.encode({'command': 'start'}));
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
      'iceServers': [{'url': 'stun:stun.l.google.com:19302'}]
    };
    final Map<String, dynamic> constraints = {
      'mandatory': {
        'OfferToReceiveAudio': false,
        'OfferToReceiveVideo': true
      },
      'optional': [],
    };
    _peerConnection = await createPeerConnection(configuration, constraints);
    _localStream = await navigator.mediaDevices.getUserMedia({
      'audio': true,
      'video': {'facingMode': 'user'}
    });
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

    fetchStatusMessage(_sessionId);
  }

  void _stopStreaming() {
    setState(() {
      _isLoading = true;
    });

    _localRenderer.srcObject = null;
    _localStream?.getTracks().forEach((track) {
      track.stop();
    });
    _localStream?.dispose();

    _channel.sink.add(json.encode({
      'command': 'stop'
    }));

    setState(() {
      _isStreaming = false;
    });
    
        Timer(const Duration(seconds: 10), () {
      if (_isLoading) {
        setState(() {
          _isLoading = false;
        });
        print('Loading state set to false after 10 seconds.');
      }
    });
  }
}
