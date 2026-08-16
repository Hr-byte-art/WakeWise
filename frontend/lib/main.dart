import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'services/api_service.dart';
import 'services/auth_service.dart';
import 'services/alarm_service.dart';
import 'screens/home_screen.dart';

void main() {
  runApp(const WakeWiseApp());
}

class WakeWiseApp extends StatelessWidget {
  const WakeWiseApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        Provider(create: (_) => ApiService()),
        ChangeNotifierProvider(create: (ctx) => AuthService(ctx.read<ApiService>())),
        ChangeNotifierProvider(create: (ctx) => AlarmService(ctx.read<ApiService>())),
      ],
      child: MaterialApp(
        title: '知晨 WakeWise',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(
            seedColor: const Color(0xFF4A90D9),
            brightness: Brightness.light,
          ),
          useMaterial3: true,
          fontFamily: 'Roboto',
        ),
        darkTheme: ThemeData(
          colorScheme: ColorScheme.fromSeed(
            seedColor: const Color(0xFF4A90D9),
            brightness: Brightness.dark,
          ),
          useMaterial3: true,
        ),
        home: const HomeScreen(),
      ),
    );
  }
}