import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'api_service.dart';

class AuthService extends ChangeNotifier {
  final ApiService _api;
  String? _userId;
  String? _deviceId;
  String? _displayName;
  bool _isLoggedIn = false;

  AuthService(this._api);

  String? get userId => _userId;
  String? get deviceId => _deviceId;
  String? get displayName => _displayName;
  bool get isLoggedIn => _isLoggedIn;

  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    _deviceId = prefs.getString('device_id');
    _userId = prefs.getString('user_id');
    _displayName = prefs.getString('display_name');
    if (_deviceId != null && _userId != null) {
      _isLoggedIn = true;
      notifyListeners();
    }
  }

  Future<void> register(String deviceId, [String? displayName]) async {
    final response = await _api.post('/auth/register', {
      'deviceId': deviceId,
      if (displayName != null) 'displayName': displayName,
    });
    final data = response['data'] as Map<String, dynamic>;
    _userId = data['userId'];
    _deviceId = data['deviceId'];
    _displayName = data['displayName'];
    _isLoggedIn = true;

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('device_id', _deviceId!);
    await prefs.setString('user_id', _userId!);
    if (_displayName != null) await prefs.setString('display_name', _displayName!);

    notifyListeners();
  }

  Future<void> logout() async {
    _userId = null;
    _isLoggedIn = false;
    final prefs = await SharedPreferences.getInstance();
    await prefs.clear();
    notifyListeners();
  }
}