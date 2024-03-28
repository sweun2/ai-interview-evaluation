import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:uuid/uuid.dart';
import 'signaling_client.dart';

class WebRTCManager {
  final RTCVideoRenderer localRenderer = RTCVideoRenderer();
  final RTCVideoRenderer remoteRenderer = RTCVideoRenderer();
  late MediaStream localStream;
  final String userId = const Uuid().v4();
  late SignalingClient signalingClient;
  final Map<String, RTCPeerConnection> peerConnections = {};

  Future<void> initialize() async {
    await _initializeRenderers();
    await _requestPermissions();
    _setupSignalingClient();
  }

  Future<void> _initializeRenderers() async {
    await localRenderer.initialize();
    await remoteRenderer.initialize();
    _getUserMedia();
  }

  Future<void> _requestPermissions() async {
    await [Permission.camera, Permission.microphone].request();
  }

  Future<void> _getUserMedia() async {
    final mediaConstraints = {
      'audio': true,
      'video': {'facingMode': 'user'},
    };

    MediaStream stream = await navigator.mediaDevices.getUserMedia(mediaConstraints);
    localRenderer.srcObject = stream;
    localStream = stream;
  }

  void _setupSignalingClient() {
    signalingClient = SignalingClient(
      onSignalingData: _handleSignalingData,
      userId: userId,
    );
    signalingClient.connect();
  }

  Future<void> _handleSignalingData(Map<String, dynamic> data) async {
    // 구현
  }

  void dispose() {
    localRenderer.dispose();
    remoteRenderer.dispose();
    peerConnections.forEach((key, pc) => pc.dispose());
    signalingClient.disconnect();
  }
}
