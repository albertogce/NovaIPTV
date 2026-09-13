// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:iptv_flutter/main.dart';
import 'package:iptv_flutter/data/models/iptv_user_info.dart';

void main() {
  testWidgets('Login screen focus navigation test', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        child: IPTVApp(
          initialUser: IptvUserInfo(username: '', password: '', server: ''),
        ),
      ),
    );

    // Verify initial focus on server URL field
    expect(find.byType(TextField), findsNWidgets(3));
    final TextField serverField = tester.widget(find.byType(TextField).at(0));
    expect(serverField.focusNode?.hasFocus, true);

    // Submit next action on server field
    await tester.testTextInput.receiveAction(TextInputAction.next);
    await tester.pump();

    // Focus should move to username field
    final TextField usernameField = tester.widget(find.byType(TextField).at(1));
    expect(usernameField.focusNode?.hasFocus, true);

    // Submit next action on username field
    await tester.testTextInput.receiveAction(TextInputAction.next);
    await tester.pump();

    // Focus should move to password field
    final TextField passwordField = tester.widget(find.byType(TextField).at(2));
    expect(passwordField.focusNode?.hasFocus, true);
  });
}
