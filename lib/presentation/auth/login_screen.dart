import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:iptv_flutter/presentation/auth/auth_controller.dart';
import 'package:iptv_flutter/core/storage/shared_prefs_storage.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _serverController = TextEditingController();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();

  final _serverFocusNode = FocusNode();
  final _usernameFocusNode = FocusNode();
  final _passwordFocusNode = FocusNode();
  final _buttonFocusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    final storage = SharedPrefsStorage();
    _serverController.text =
        storage.getString('server') ?? _serverController.text;
    _usernameController.text =
        storage.getString('username') ?? _usernameController.text;
    _passwordController.text =
        storage.getString('password') ?? _passwordController.text;
  }

  @override
  void dispose() {
    _serverController.dispose();
    _usernameController.dispose();
    _passwordController.dispose();
    _serverFocusNode.dispose();
    _usernameFocusNode.dispose();
    _passwordFocusNode.dispose();
    _buttonFocusNode.dispose();
    super.dispose();
  }

  void _submitForm() async {
    if (_formKey.currentState?.validate() ?? false) {
      final server = _serverController.text.trim();
      final username = _usernameController.text.trim();
      final password = _passwordController.text.trim();

      await ref
          .read(authStateProvider.notifier)
          .login(server, username, password);
      final authState = ref.read(authStateProvider);

      if (mounted && authState.user != null) {
        Navigator.pushReplacementNamed(
          context,
          '/home',
          arguments: authState.user,
        );
      }
    }
  }

  KeyEventResult _handleKeyEvent(
    FocusNode node,
    KeyEvent event,
    FocusNode? nextNode,
    FocusNode? prevNode,
  ) {
    if (event is KeyDownEvent) {
      if (event.logicalKey == LogicalKeyboardKey.arrowDown ||
          event.logicalKey == LogicalKeyboardKey.gameButtonSelect) {
        if (nextNode != null) {
          nextNode.requestFocus();
          return KeyEventResult.handled;
        }
      } else if (event.logicalKey == LogicalKeyboardKey.arrowUp) {
        if (prevNode != null) {
          prevNode.requestFocus();
          return KeyEventResult.handled;
        }
      }
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('IPTV Login')),
      body: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Form(
          key: _formKey,
          child: FocusTraversalGroup(
            policy: WidgetOrderTraversalPolicy(),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Image.asset('assets/app_logo.png', width: 82, height: 82),
                const SizedBox(height: 14),
                const Text(
                  'NOVA IPTV',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 1.1,
                  ),
                ),
                const SizedBox(height: 26),
                Focus(
                  onKeyEvent: (node, event) =>
                      _handleKeyEvent(node, event, _usernameFocusNode, null),
                  child: TextFormField(
                    controller: _serverController,
                    focusNode: _serverFocusNode,
                    autofocus: true,
                    textInputAction: TextInputAction.next,
                    decoration: const InputDecoration(
                      labelText: 'Servidor URL',
                    ),
                    onFieldSubmitted: (_) {
                      _usernameFocusNode.requestFocus();
                    },
                    validator: (value) {
                      if (value?.isEmpty ?? true) {
                        return 'Ingrese la URL del servidor';
                      }
                      return null;
                    },
                  ),
                ),
                const SizedBox(height: 16),
                Focus(
                  onKeyEvent: (node, event) => _handleKeyEvent(
                    node,
                    event,
                    _passwordFocusNode,
                    _serverFocusNode,
                  ),
                  child: TextFormField(
                    controller: _usernameController,
                    focusNode: _usernameFocusNode,
                    textInputAction: TextInputAction.next,
                    decoration: const InputDecoration(labelText: 'Usuario'),
                    onFieldSubmitted: (_) {
                      _passwordFocusNode.requestFocus();
                    },
                    validator: (value) {
                      if (value?.isEmpty ?? true) {
                        return 'Ingrese el usuario';
                      }
                      return null;
                    },
                  ),
                ),
                const SizedBox(height: 16),
                Focus(
                  onKeyEvent: (node, event) => _handleKeyEvent(
                    node,
                    event,
                    _buttonFocusNode,
                    _usernameFocusNode,
                  ),
                  child: TextFormField(
                    controller: _passwordController,
                    focusNode: _passwordFocusNode,
                    textInputAction: TextInputAction.done,
                    decoration: const InputDecoration(labelText: 'Contraseña'),
                    obscureText: true,
                    onFieldSubmitted: (_) => _submitForm(),
                    validator: (value) {
                      if (value?.isEmpty ?? true) {
                        return 'Ingrese la contraseña';
                      }
                      return null;
                    },
                  ),
                ),
                const SizedBox(height: 24),
                Focus(
                  onKeyEvent: (node, event) =>
                      _handleKeyEvent(node, event, null, _passwordFocusNode),
                  child: ElevatedButton(
                    focusNode: _buttonFocusNode,
                    onPressed: _submitForm,
                    child: const Text('CONECTAR'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
