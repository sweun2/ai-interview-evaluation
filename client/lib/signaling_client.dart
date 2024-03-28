import 'dart:convert';
import 'package:stomp_dart_client/stomp.dart';
import 'package:stomp_dart_client/stomp_config.dart';
import 'package:stomp_dart_client/stomp_frame.dart';

class SignalingClient {
  final Function(Map<String, dynamic>) onSignalingData;
  final String userId;
  late StompClient stompClient;

  SignalingClient({required this.onSignalingData, required this.userId});

  void connect() {
    stompClient = StompClient(
      config: StompConfig.sockJS(
        url: 'http://localhost:8080/ws',
        onConnect: _onConnect,
        beforeConnect: () async {},
        onWebSocketError: (dynamic error) {},
        stompConnectHeaders: {},
        webSocketConnectHeaders: {},
      ),
    );
    stompClient.activate();
  }

  void _onConnect(StompFrame frame) {
    stompClient.subscribe(
      destination: '/topic/$userId',
      callback: (frame) {
        var data = jsonDecode(frame.body!);
        onSignalingData(data);
      },
    );
  }

  void disconnect() {
    stompClient.deactivate();
  }
}
